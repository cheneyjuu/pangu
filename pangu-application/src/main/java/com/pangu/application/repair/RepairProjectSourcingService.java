// 关联业务：编排维修工程项目邀价、报价修订、供应商提交、物业比价推荐和中选快照。
package com.pangu.application.repair;

import com.pangu.application.repair.command.RepairProjectSourcingCommands.InviteSuppliers;
import com.pangu.application.repair.command.RepairProjectSourcingCommands.RequestQuoteRevisions;
import com.pangu.application.repair.command.RepairProjectSourcingCommands.SelectQuote;
import com.pangu.application.repair.command.RepairProjectSourcingCommands.SubmitQuote;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.Item;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProjectSourcing;
import com.pangu.domain.model.repair.RepairProjectSourcing.Details;
import com.pangu.domain.model.repair.RepairProjectSourcing.Invitation;
import com.pangu.domain.model.repair.RepairProjectSourcing.InvitationStatus;
import com.pangu.domain.model.repair.RepairProjectSourcing.InvitationType;
import com.pangu.domain.model.repair.RepairProjectSourcing.Quote;
import com.pangu.domain.model.repair.RepairProjectSourcing.Selection;
import com.pangu.domain.model.repair.RepairQuoteConfirmationStatus;
import com.pangu.domain.model.repair.RepairQuoteSubmissionSource;
import com.pangu.domain.model.repair.RepairSupplierQuoteStatus;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.policy.RepairProjectQuotePricingPolicy;
import com.pangu.domain.policy.RepairProjectQuotePricingPolicy.ScopeItem;
import com.pangu.domain.policy.RepairSupplierSelectionPolicy;
import com.pangu.domain.policy.RepairSupplierSelectionPolicy.Input;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectSourcingRepository;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RepairProjectSourcingService {

    private static final Set<String> PROPERTY_ROLES = Set.of("PROPERTY_MANAGER", "PROPERTY_STAFF");
    private static final Set<String> SUPPLIER_ROLES = Set.of(
            "SERVICE_PROVIDER_MANAGER", "SERVICE_PROVIDER_STAFF");
    private static final Set<String> QUOTE_ROLES = Set.of(
            "PROPERTY_MANAGER", "PROPERTY_STAFF", "SERVICE_PROVIDER_MANAGER", "SERVICE_PROVIDER_STAFF");

    private final RepairProjectApplicationSupport support;
    private final RepairProjectRepository projectRepository;
    private final RepairProjectSourcingRepository sourcingRepository;
    private final RepairWorkOrderRepository workOrderRepository;
    private final RepairProjectQuotePricingPolicy quotePricingPolicy;
    private final RepairSupplierSelectionPolicy selectionPolicy;
    private final SupplierActivationService supplierActivationService;
    private final RepairNarrativeImageService narrativeImageService;

    @Transactional(readOnly = true)
    public Details details(Long projectId) {
        UserContext actor = support.requireActor();
        if (!actor.isSysUser()) {
            throw support.forbidden("仅管理端工作身份可查看维修工程询价资料");
        }
        DraftContext context = readContext(projectId, actor.tenantId());
        return details(context);
    }

    @Transactional
    public Details invite(Long projectId, InviteSuppliers command) {
        UserContext actor = support.requireSysActor(PROPERTY_ROLES, "仅物业项目人员可发出维修工程邀价");
        DraftContext context = draftContext(projectId, actor.tenantId(), true);
        List<Long> supplierIds = distinctSupplierIds(command == null ? null : command.supplierDeptIds());
        if (supplierIds.isEmpty() || supplierIds.size() > 20) {
            throw support.invalid("每次必须邀请 1 至 20 家供应商");
        }
        if (command.deadline() != null && !command.deadline().isAfter(LocalDateTime.now())) {
            throw support.invalid("报价截止时间必须晚于当前时间");
        }
        for (Long supplierId : supplierIds) {
            workOrderRepository.findSupplierLegalName(supplierId)
                    .orElseThrow(() -> support.notFound("供应商组织不存在 supplierDeptId=" + supplierId));
            if (sourcingRepository.supplierInvited(
                    projectId, context.plan().planId(), actor.tenantId(), supplierId)) {
                throw support.invalid("所选供应商中包含已发出邀价的企业 supplierDeptId=" + supplierId);
            }
        }
        for (Long supplierId : supplierIds) {
            sourcingRepository.insertInvitation(new Invitation(
                    null, projectId, context.plan().planId(), actor.tenantId(), supplierId, null,
                    actor.userId(), command.deadline(), InvitationStatus.PENDING, 1,
                    InvitationType.INITIAL, null, null, null));
            supplierActivationService.ensureContactInvitation(
                    actor.tenantId(), supplierId, null, actor.userId());
        }
        support.event(context.projectContext(), actor, "PROJECT_SUPPLIERS_INVITED", Map.of(
                "planId", context.plan().planId(), "supplierDeptIds", supplierIds,
                "invitationCount", supplierIds.size()));
        return details(context);
    }

    @Transactional
    public Details requestRevisions(Long projectId, RequestQuoteRevisions command) {
        UserContext actor = support.requireSysActor(PROPERTY_ROLES, "仅物业项目人员可要求修订报价");
        DraftContext context = draftContext(projectId, actor.tenantId(), true);
        List<Long> supplierIds = distinctSupplierIds(command == null ? null : command.supplierDeptIds());
        if (supplierIds.isEmpty() || supplierIds.size() > 20) {
            throw support.invalid("每次必须选择 1 至 20 家供应商修订报价");
        }
        String reason = requireText(command.revisionReason(), "修订报价原因");
        if (command.deadline() != null && !command.deadline().isAfter(LocalDateTime.now())) {
            throw support.invalid("报价截止时间必须晚于当前时间");
        }
        for (Long supplierId : supplierIds) {
            Quote latest = sourcingRepository.findLatestSupplierQuote(
                            projectId, context.plan().planId(), actor.tenantId(), supplierId)
                    .orElseThrow(() -> support.notFound(
                            "所选供应商没有可修订的历史报价 supplierDeptId=" + supplierId));
            if (latest.quoteStatus() != RepairSupplierQuoteStatus.ACTIVE) {
                throw support.invalid("所选供应商没有当前有效报价 supplierDeptId=" + supplierId);
            }
        }
        for (Long supplierId : supplierIds) {
            int round = sourcingRepository.nextInvitationRound(
                    projectId, context.plan().planId(), actor.tenantId(), supplierId);
            sourcingRepository.insertInvitation(new Invitation(
                    null, projectId, context.plan().planId(), actor.tenantId(), supplierId, null,
                    actor.userId(), command.deadline(), InvitationStatus.PENDING, round,
                    InvitationType.REVISION, reason, null, null));
            Quote latest = sourcingRepository.findLatestSupplierQuote(
                    projectId, context.plan().planId(), actor.tenantId(), supplierId).orElseThrow();
            sourcingRepository.requestQuoteRevision(latest.quoteId());
            supplierActivationService.ensureContactInvitation(
                    actor.tenantId(), supplierId, null, actor.userId());
        }
        support.event(context.projectContext(), actor, "PROJECT_QUOTE_REVISIONS_REQUESTED", Map.of(
                "planId", context.plan().planId(), "supplierDeptIds", supplierIds,
                "revisionReason", reason));
        return details(context);
    }

    @Transactional
    public Quote submitQuote(Long projectId, SubmitQuote command) {
        UserContext actor = support.requireGlobalSysActor(QUOTE_ROLES, "当前身份无权提交维修工程报价");
        boolean supplierSubmission = SUPPLIER_ROLES.contains(actor.roleKey());
        Long supplierDeptId = supplierSubmission ? actor.deptId() : command == null ? null : command.supplierDeptId();
        if (command == null || supplierDeptId == null) {
            throw support.invalid("供应商和报价内容均为必填项");
        }
        Invitation invitation = supplierSubmission
                ? supplierInvitation(projectId, supplierDeptId, command.invitationId())
                : null;
        Long tenantId = supplierSubmission ? invitation.tenantId() : actor.tenantId();
        if (tenantId == null) {
            throw support.forbidden("未识别到报价所属小区");
        }
        DraftContext context = draftContext(projectId, tenantId, true);
        if (supplierSubmission && !context.plan().planId().equals(invitation.planId())) {
            throw support.invalid("当前邀价不属于项目正在编制的实施方案");
        }
        String supplierName = workOrderRepository.findSupplierLegalName(supplierDeptId)
                .orElseThrow(() -> support.notFound("供应商组织不存在 supplierDeptId=" + supplierDeptId));
        if (!supplierSubmission) {
            invitation = resolvePropertyInvitation(context, supplierDeptId, command.invitationId());
        }
        Attachment attachment = projectRepository.findAttachment(
                        command.attachmentId(), projectId, tenantId)
                .orElseThrow(() -> support.notFound("报价原件不存在或不属于当前项目"));
        if (supplierSubmission && !actor.accountId().equals(attachment.uploadedByAccountId())) {
            throw support.forbidden("供应商只能提交本人上传的报价原件");
        }
        RepairQuoteConfirmationStatus confirmationStatus = supplierSubmission
                ? RepairQuoteConfirmationStatus.ONLINE_CONFIRMED
                : propertyConfirmationStatus(command.confirmationStatus());
        String originalSource = supplierSubmission ? null : requireText(command.originalSource(), "原始报价来源");
        List<Item> planItems = projectRepository.listItems(context.plan().planId(), tenantId);
        var pricing = quotePricingPolicy.evaluate(new RepairProjectQuotePricingPolicy.Input(
                planItems.stream().map(item -> new ScopeItem(item.itemId(), item.itemNo())).toList(),
                command.quoteLines(), command.quoteAmount(), command.constructionPeriodDays(),
                command.warrantyDays(), command.originalAmountConfirmed()));
        if (!pricing.allowed()) {
            throw support.invalid(pricing.rejectionReason());
        }
        Quote previous = sourcingRepository.findLatestSupplierQuote(
                projectId, context.plan().planId(), tenantId, supplierDeptId).orElse(null);
        int revisionNo = previous == null ? 1 : previous.revisionNo() + 1;
        if (previous != null && previous.quoteStatus() != RepairSupplierQuoteStatus.SUPERSEDED) {
            sourcingRepository.supersedeQuote(previous.quoteId(), null);
        }
        Quote quote = sourcingRepository.insertQuote(new Quote(
                null, projectId, context.plan().planId(), tenantId, supplierDeptId, supplierName,
                pricing.calculatedAmount(), trim(command.quoteSummary()),
                attachment.attachmentId(), attachment.sha256(), actor.userId(), actor.roleKey(),
                supplierSubmission ? RepairQuoteSubmissionSource.SUPPLIER_ONLINE : RepairQuoteSubmissionSource.PROPERTY_ENTRY,
                confirmationStatus, originalSource, command.constructionPeriodDays(), command.warrantyDays(),
                true, RepairSupplierQuoteStatus.ACTIVE, revisionNo, null, null, pricing.normalizedLines()));
        if (previous != null && previous.quoteStatus() != RepairSupplierQuoteStatus.SUPERSEDED) {
            sourcingRepository.supersedeQuote(previous.quoteId(), quote.quoteId());
        }
        if (invitation != null) {
            sourcingRepository.markInvitationSubmitted(invitation.invitationId(), tenantId);
        }
        support.event(context.projectContext(), actor, "PROJECT_SUPPLIER_QUOTE_SUBMITTED", Map.of(
                "planId", context.plan().planId(), "quoteId", quote.quoteId(),
                "supplierDeptId", supplierDeptId, "revisionNo", revisionNo,
                "submissionSource", quote.submissionSource().name()));
        return quote;
    }

    @Transactional
    public Details selectQuote(Long projectId, SelectQuote command) {
        UserContext actor = support.requireSysActor(PROPERTY_ROLES, "仅物业项目人员可形成中选供应商建议");
        DraftContext context = draftContext(projectId, actor.tenantId(), true);
        if (command == null || command.quoteId() == null) {
            throw support.invalid("quoteId 必填");
        }
        Quote quote = sourcingRepository.findQuote(
                        command.quoteId(), projectId, context.plan().planId(), actor.tenantId())
                .orElseThrow(() -> support.notFound("项目报价不存在 quoteId=" + command.quoteId()));
        if (quote.quoteStatus() != RepairSupplierQuoteStatus.ACTIVE
                || !quote.confirmationStatus().confirmedForContract()) {
            throw support.invalid("只能从当前有效且已经供应商确认或线下核验的报价中定商");
        }
        if (!workOrderRepository.supplierVerified(actor.tenantId(), quote.supplierDeptId())) {
            throw support.invalid("中选施工单位必须完成当前小区企业核验");
        }
        if (quote.quoteAmount().compareTo(context.plan().budgetTotal()) > 0) {
            throw support.invalid("中选报价不能超过当前实施方案预算");
        }
        RepairSupplierSelectionMethod method = context.plan().supplierSelectionMethod();
        boolean frameworkValid = method != RepairSupplierSelectionMethod.FRAMEWORK_SUPPLIER
                || command.frameworkRelationId() != null
                && workOrderRepository.frameworkRelationActive(
                        command.frameworkRelationId(), actor.tenantId(), quote.supplierDeptId(), null);
        var decision = selectionPolicy.evaluate(new Input(
                method,
                sourcingRepository.countInitialInvitedSuppliers(
                        projectId, context.plan().planId(), actor.tenantId()),
                sourcingRepository.countActiveConfirmedQuotes(
                        projectId, context.plan().planId(), actor.tenantId()),
                trim(command.recommendationReason()), trim(command.insufficientQuoteReason()),
                frameworkValid));
        if (!decision.allowed()) {
            throw support.invalid(decision.rejectionReason());
        }
        Selection selection = sourcingRepository.insertSelection(new Selection(
                null, projectId, context.plan().planId(), actor.tenantId(), quote.quoteId(),
                quote.supplierDeptId(), quote.supplierName(), quote.quoteAmount(), method,
                trim(command.recommendationReason()), trim(command.insufficientQuoteReason()),
                command.frameworkRelationId(), actor.userId(), null));
        support.event(context.projectContext(), actor, "PROJECT_SUPPLIER_SELECTED", Map.of(
                "planId", context.plan().planId(), "selectionId", selection.selectionId(),
                "quoteId", selection.quoteId(), "supplierDeptId", selection.supplierDeptId(),
                "selectionMethod", selection.selectionMethod().name(),
                "quoteAmount", selection.quoteAmount()));
        return details(context);
    }

    @Transactional(readOnly = true)
    public List<SupplierOpportunity> listSupplierOpportunities() {
        UserContext actor = support.requireGlobalSysActor(SUPPLIER_ROLES, "仅施工单位账号可查看维修工程邀价");
        if (actor.deptId() == null) {
            throw support.forbidden("当前施工单位账号未绑定企业组织");
        }
        return sourcingRepository.listSupplierInvitations(actor.deptId()).stream()
                .map(invitation -> supplierOpportunity(actor, invitation))
                .toList();
    }

    private SupplierOpportunity supplierOpportunity(UserContext actor, Invitation invitation) {
        Long tenantId = invitation.tenantId();
        RepairProject project = projectRepository.findProject(invitation.projectId(), tenantId)
                .orElseThrow(() -> support.notFound("维修工程项目不存在"));
        PlanVersion plan = projectRepository.listPlans(project.projectId(), tenantId).stream()
                .filter(candidate -> candidate.planId().equals(invitation.planId()))
                .findFirst()
                .orElseThrow(() -> support.notFound("维修工程实施方案不存在"));
        List<SupplierItem> items = projectRepository.listItems(plan.planId(), tenantId).stream()
                .map(item -> new SupplierItem(
                        item.itemId(), item.itemNo(), item.locationText(), item.workContent(),
                        item.quantity(), item.unit()))
                .toList();
        Quote latestQuote = sourcingRepository.findLatestSupplierQuote(
                project.projectId(), plan.planId(), tenantId, actor.deptId()).orElse(null);
        return new SupplierOpportunity(
                project.projectId(), project.projectNo(), project.projectName(), plan.planId(),
                narrativeImageService.resolveForPlan(plan.planId(), tenantId, plan.planDescription()),
                plan.constructionManagementRequirements(), plan.safetyRequirements(),
                plan.plannedStartDate(), plan.plannedCompletionDate(), plan.warrantyDays(),
                items, invitation, latestQuote);
    }

    private Invitation resolvePropertyInvitation(
            DraftContext context,
            Long supplierDeptId,
            Long invitationId) {
        if (invitationId != null) {
            Invitation invitation = sourcingRepository.findInvitation(
                            invitationId, context.project().projectId(), context.plan().planId(),
                            context.project().tenantId(), supplierDeptId)
                    .orElseThrow(() -> support.notFound("维修工程邀价不存在"));
            return requireUsableInvitation(invitation);
        }
        if (context.plan().supplierSelectionMethod() == RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION) {
            return sourcingRepository.listInvitations(
                            context.project().projectId(), context.plan().planId(), context.project().tenantId()).stream()
                    .filter(candidate -> candidate.supplierDeptId().equals(supplierDeptId))
                    .filter(candidate -> candidate.status() == InvitationStatus.PENDING)
                    .findFirst()
                    .orElseThrow(() -> support.invalid("竞争性询价只能录入已受邀供应商的报价"));
        }
        return null;
    }

    private Invitation supplierInvitation(Long projectId, Long supplierDeptId, Long invitationId) {
        if (invitationId == null) {
            throw support.invalid("供应商在线报价必须关联有效邀价");
        }
        Invitation invitation = sourcingRepository.findSupplierInvitation(
                        invitationId, projectId, supplierDeptId)
                .orElseThrow(() -> support.notFound("维修工程邀价不存在"));
        return requireUsableInvitation(invitation);
    }

    private Invitation requireUsableInvitation(Invitation invitation) {
        if (invitation.status() != InvitationStatus.PENDING) {
            throw support.invalid("当前邀价已响应或不可用");
        }
        if (invitation.deadline() != null && !invitation.deadline().isAfter(LocalDateTime.now())) {
            throw support.invalid("当前邀价已经超过报价截止时间");
        }
        return invitation;
    }

    private Details details(DraftContext context) {
        return new Details(
                context.project().projectId(), context.plan().planId(),
                context.plan().supplierSelectionMethod(),
                sourcingRepository.listInvitations(
                        context.project().projectId(), context.plan().planId(), context.project().tenantId()),
                sourcingRepository.listQuotes(
                        context.project().projectId(), context.plan().planId(), context.project().tenantId()),
                sourcingRepository.findCurrentSelection(
                        context.project().projectId(), context.plan().planId(), context.project().tenantId()).orElse(null));
    }

    private DraftContext draftContext(Long projectId, Long tenantId, boolean lock) {
        RepairProject project = (lock
                ? projectRepository.findProjectForUpdate(projectId, tenantId)
                : projectRepository.findProject(projectId, tenantId))
                .orElseThrow(() -> support.notFound("维修工程项目不存在"));
        if (project.status() != Status.DRAFT && project.status() != Status.PLAN_LOCKED) {
            throw support.conflict("当前项目已锁定方案，不能再修改询价和中选结果");
        }
        PlanVersion plan = projectRepository.listPlans(projectId, tenantId).stream()
                .filter(candidate -> candidate.status() == PlanStatus.DRAFT)
                .findFirst()
                .orElseThrow(() -> support.conflict("当前项目没有可询价的实施方案草稿"));
        return new DraftContext(project, plan, new RepairProjectApplicationSupport.Context(project, plan, List.of()));
    }

    private DraftContext readContext(Long projectId, Long tenantId) {
        RepairProject project = projectRepository.findProject(projectId, tenantId)
                .orElseThrow(() -> support.notFound("维修工程项目不存在"));
        List<PlanVersion> plans = projectRepository.listPlans(projectId, tenantId);
        PlanVersion plan = plans.stream()
                .filter(candidate -> candidate.status() == PlanStatus.DRAFT)
                .findFirst()
                .or(() -> plans.stream()
                        .filter(candidate -> candidate.planId().equals(project.activePlanId()))
                        .findFirst())
                .orElseThrow(() -> support.conflict("当前项目没有可查看的实施方案"));
        return new DraftContext(project, plan, new RepairProjectApplicationSupport.Context(project, plan, List.of()));
    }

    private List<Long> distinctSupplierIds(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long value : values) {
            if (value == null) {
                throw support.invalid("supplierDeptIds 不能包含空值");
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private RepairQuoteConfirmationStatus propertyConfirmationStatus(String value) {
        RepairQuoteConfirmationStatus status;
        try {
            status = value == null || value.isBlank()
                    ? RepairQuoteConfirmationStatus.PENDING_SUPPLIER_CONFIRMATION
                    : RepairQuoteConfirmationStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw support.invalid("confirmationStatus 不合法");
        }
        if (status != RepairQuoteConfirmationStatus.PENDING_SUPPLIER_CONFIRMATION
                && status != RepairQuoteConfirmationStatus.OFFLINE_EVIDENCE_VERIFIED) {
            throw support.invalid("物业代录报价只能标记为待供应商确认或线下证据已核验");
        }
        return status;
    }

    private String requireText(String value, String field) {
        String normalized = trim(value);
        if (normalized == null) {
            throw support.invalid(field + " 必填");
        }
        return normalized;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record DraftContext(
            RepairProject project,
            PlanVersion plan,
            RepairProjectApplicationSupport.Context projectContext
    ) {
    }

    public record SupplierOpportunity(
            Long projectId,
            String projectNo,
            String projectName,
            Long planId,
            String planDescription,
            String constructionManagementRequirements,
            String safetyRequirements,
            LocalDate plannedStartDate,
            LocalDate plannedCompletionDate,
            Integer warrantyDays,
            List<SupplierItem> items,
            Invitation invitation,
            Quote latestQuote
    ) {
        public SupplierOpportunity {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record SupplierItem(
            Long itemId,
            String itemNo,
            String locationText,
            String workContent,
            BigDecimal quantity,
            String unit
    ) {
    }
}
