// 关联业务：编排维修工程项目邀价、报价修订，以及业委会依据不可变决定/授权快照确认中选供应商。
package com.pangu.application.repair;

import com.pangu.application.repair.command.RepairProjectSourcingCommands.InviteSuppliers;
import com.pangu.application.repair.command.RepairProjectSourcingCommands.RequestQuoteRevisions;
import com.pangu.application.repair.command.RepairProjectSourcingCommands.SelectQuote;
import com.pangu.application.repair.command.RepairProjectSourcingCommands.SubmitQuote;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProject.WorkPoint;
import com.pangu.domain.model.repair.RepairProjectSourcing;
import com.pangu.domain.model.repair.RepairProjectSourcing.Details;
import com.pangu.domain.model.repair.RepairProjectSourcing.Invitation;
import com.pangu.domain.model.repair.RepairProjectSourcing.InvitationStatus;
import com.pangu.domain.model.repair.RepairProjectSourcing.InvitationType;
import com.pangu.domain.model.repair.RepairProjectSourcing.Quote;
import com.pangu.domain.model.repair.RepairProjectSourcing.Selection;
import com.pangu.domain.model.repair.RepairProjectSourcing.SelectionAuthorization;
import com.pangu.domain.model.repair.RepairProjectSourcing.SelectionAuthorizationStatus;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingDecision;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcess;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcessStatus;
import com.pangu.domain.model.repair.RepairProjectGovernance.DecisionPolicySnapshot;
import com.pangu.domain.model.repair.RepairProjectGovernance.GovernanceBasis;
import com.pangu.domain.model.repair.RepairProjectGovernance.GovernanceResult;
import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairQuoteConfirmationStatus;
import com.pangu.domain.model.repair.RepairQuoteSubmissionSource;
import com.pangu.domain.model.repair.RepairFrameworkRelation;
import com.pangu.domain.model.repair.RepairSupplierQuoteStatus;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.policy.RepairProjectQuotePricingPolicy;
import com.pangu.domain.policy.RepairProjectQuotePricingPolicy.ScopeWorkPoint;
import com.pangu.domain.policy.RepairSupplierSelectionPolicy;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectSourcingRepository;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
    private static final Set<String> FINAL_SELECTION_POSITIONS = Set.of("DIRECTOR", "VICE_DIRECTOR");

    private final RepairProjectApplicationSupport support;
    private final RepairProjectRepository projectRepository;
    private final RepairProjectSourcingRepository sourcingRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
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
        return details(context, actor);
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
        return details(context, actor);
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
        return details(context, actor);
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
        List<WorkPoint> planWorkPoints = projectRepository.listWorkPoints(context.plan().planId(), tenantId);
        var pricing = quotePricingPolicy.evaluate(new RepairProjectQuotePricingPolicy.Input(
                planWorkPoints.stream().map(workPoint -> new ScopeWorkPoint(
                        workPoint.workPointId(), workPoint.businessName())).toList(),
                command.quoteLines(), command.quoteAmount(), command.taxRate(), command.constructionPeriodDays(),
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
                pricing.amountExcludingTax(), pricing.taxRate(), pricing.taxAmount(),
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
        UserContext actor = support.requireActor();
        if (!actor.isSysUser() || actor.userId() == null) {
            throw support.forbidden("仅管理端业委会确认人可确认中选供应商");
        }
        RepairProjectApplicationSupport.Context projectContext = support.loadForUpdate(
                projectId, actor.tenantId(), Status.AUTHORIZED);
        SelectionAuthorization authorization = resolveSelectionAuthorization(
                projectContext.project(), projectContext.plan(), actor);
        if (authorization.status() != SelectionAuthorizationStatus.AUTHORIZED) {
            throw support.conflict(authorization.blockingReason());
        }
        if (!authorization.currentActorMayConfirm()) {
            throw support.forbidden("仅当前在任且拥有治理权限的业委会主任或副主任可以确认中选供应商");
        }
        if (command == null || command.quoteId() == null || command.selectionEvidenceAttachmentId() == null) {
            throw support.invalid("quoteId 和 selectionEvidenceAttachmentId 均为必填项");
        }
        if (sourcingRepository.findCurrentSelection(
                projectId, projectContext.plan().planId(), actor.tenantId()).isPresent()) {
            throw support.conflict("当前锁定方案已存在经过授权确认的中选供应商，不能重复确认");
        }
        Quote quote = sourcingRepository.findQuote(
                        command.quoteId(), projectId, projectContext.plan().planId(), actor.tenantId())
                .orElseThrow(() -> support.notFound("中选报价不存在或不属于当前锁定方案"));
        if (quote.quoteStatus() != RepairSupplierQuoteStatus.ACTIVE
                || !quote.confirmationStatus().confirmedForContract()) {
            throw support.invalid("中选报价必须是当前有效且已经确认的报价原件");
        }
        if (!workOrderRepository.supplierVerified(actor.tenantId(), quote.supplierDeptId())) {
            throw support.invalid("中选供应商必须是当前小区已核验的企业主体");
        }
        if (quote.quoteAmount().compareTo(authorization.approvedBudgetAmount()) > 0) {
            throw support.invalid("中选报价超过已通过的审价金额");
        }
        Attachment selectionEvidence = support.attachment(
                projectContext, command.selectionEvidenceAttachmentId(), "评审或定商记录");
        if (!isSelectionEvidenceDocument(selectionEvidence.contentType())) {
            throw support.invalid("评审或定商记录必须是图片、PDF 或办公文档");
        }
        String rationale = requireText(command.selectionRationale(), "中选说明");
        boolean frameworkRelationValid = validateFrameworkRelation(
                authorization, command.frameworkRelationId(), quote, actor.tenantId());
        int invitationCount = sourcingRepository.countInitialInvitedSuppliers(
                projectId, projectContext.plan().planId(), actor.tenantId());
        int validQuoteCount = sourcingRepository.countActiveConfirmedQuotes(
                projectId, projectContext.plan().planId(), actor.tenantId());
        RepairSupplierSelectionPolicy.Decision policyDecision = selectionPolicy.evaluate(
                new RepairSupplierSelectionPolicy.Input(
                        authorization.approvedSelectionMethod(), authorization.approvedEvaluationRule(),
                        authorization.minimumInvitedSupplierCount(), authorization.minimumValidQuoteCount(),
                        authorization.nonCompetitiveSelectionBasis(), invitationCount, validQuoteCount,
                        rationale, true, frameworkRelationValid));
        if (!policyDecision.allowed()) {
            throw support.invalid(policyDecision.rejectionReason());
        }
        if (authorization.approvedEvaluationRule() == SupplierSelectionEvaluationRule.LOWEST_COMPLIANT_QUOTE) {
            requireNoLowerConfirmedQuote(projectId, projectContext.plan().planId(), actor.tenantId(), quote);
        }
        sourcingRepository.insertSelection(new Selection(
                null, projectId, projectContext.plan().planId(), actor.tenantId(), quote.quoteId(),
                quote.supplierDeptId(), quote.supplierName(), quote.quoteAmount(),
                authorization.approvedSelectionMethod(), authorization.approvedEvaluationRule(), rationale,
                selectionEvidence.attachmentId(), authorization.governanceBasisId(),
                authorization.governanceBasisHash(), command.frameworkRelationId(), actor.userId(), null));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("planId", projectContext.plan().planId());
        event.put("quoteId", quote.quoteId());
        event.put("supplierDeptId", quote.supplierDeptId());
        event.put("selectionMethod", authorization.approvedSelectionMethod().name());
        event.put("selectionEvaluationRule", authorization.approvedEvaluationRule().name());
        event.put("selectionEvidenceAttachmentId", selectionEvidence.attachmentId());
        event.put("governanceBasisId", authorization.governanceBasisId());
        event.put("governanceBasisHash", authorization.governanceBasisHash());
        event.put("frameworkRelationId", command.frameworkRelationId());
        support.event(projectContext, actor, "PROJECT_SUPPLIER_SELECTION_CONFIRMED", event);
        return details(projectContext, actor);
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
        List<SupplierWorkPoint> workPoints = projectRepository.listWorkPoints(plan.planId(), tenantId).stream()
                .map(workPoint -> new SupplierWorkPoint(
                        workPoint.workPointId(), workPoint.businessName(), workPoint.buildingId(),
                        workPoint.unitName(), workPoint.locationType(), workPoint.referenceRoomId(),
                        workPoint.commonAreaName(), workPoint.spaceName(), workPoint.orientation(),
                        workPoint.component(), workPoint.specificPart(), workPoint.symptom(),
                        workPoint.proposedMeasure(), workPoint.technicalRequirements(),
                        workPoint.quantity(), workPoint.unit()))
                .toList();
        Quote latestQuote = sourcingRepository.findLatestSupplierQuote(
                project.projectId(), plan.planId(), tenantId, actor.deptId()).orElse(null);
        return new SupplierOpportunity(
                project.projectId(), project.projectNo(), project.projectName(), plan.planId(),
                narrativeImageService.resolveForPlan(plan.planId(), tenantId, plan.planDescription()),
                plan.constructionManagementRequirements(), plan.safetyRequirements(),
                plan.plannedStartDate(), plan.plannedCompletionDate(), plan.warrantyDays(),
                workPoints, invitation, latestQuote);
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
        return sourcingRepository.listInvitations(
                        context.project().projectId(), context.plan().planId(), context.project().tenantId()).stream()
                .filter(candidate -> candidate.supplierDeptId().equals(supplierDeptId))
                .filter(candidate -> candidate.status() == InvitationStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> support.invalid("物业代录报价必须关联有效邀价"));
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

    private Details details(DraftContext context, UserContext actor) {
        return details(context.project(), context.plan(), actor);
    }

    private Details details(RepairProjectApplicationSupport.Context context, UserContext actor) {
        return details(context.project(), context.plan(), actor);
    }

    private Details details(RepairProject project, PlanVersion plan, UserContext actor) {
        SelectionAuthorization authorization = resolveSelectionAuthorization(project, plan, actor);
        List<RepairFrameworkRelation> eligibleFrameworkRelations = eligibleFrameworkRelations(
                authorization, project.tenantId());
        return new Details(
                project.projectId(), plan.planId(), authorization.approvedSelectionMethod(), authorization,
                eligibleFrameworkRelations,
                sourcingRepository.listInvitations(
                        project.projectId(), plan.planId(), project.tenantId()),
                sourcingRepository.listQuotes(
                        project.projectId(), plan.planId(), project.tenantId()),
                sourcingRepository.findCurrentSelection(
                        project.projectId(), plan.planId(), project.tenantId()).orElse(null));
    }

    /**
     * 只认楼栋维修流程实际落库的决定、审价、审批事实和用印依据；草稿字段与旧物业推荐均不能形成授权。
     */
    private SelectionAuthorization resolveSelectionAuthorization(
            RepairProject project, PlanVersion plan, UserContext actor) {
        if (project.workflowType() != RepairWorkflowType.BUILDING_REPAIR) {
            return unsupported("全小区维修尚未接入与楼栋流程等价的施工单位选择授权快照，不能最终定商");
        }
        if (project.activePlanId() == null || !project.activePlanId().equals(plan.planId())
                || plan.status() != PlanStatus.LOCKED) {
            return pending("当前项目没有有效的锁定实施方案，不能最终定商");
        }
        if (!authorizationStageReached(project.status())) {
            return pending("当前项目尚未完成有效决定、审价、业委会确认和用印授权，不能最终定商");
        }
        BuildingProcess process = governanceRepository.findBuildingProcess(
                        project.projectId(), plan.planId(), project.tenantId())
                .orElse(null);
        if (process == null || process.status() != BuildingProcessStatus.AUTHORIZED) {
            return pending("楼栋维修流程尚未完成用印授权，不能最终定商");
        }
        if (process.officialDocumentAttachmentId() == null || process.sealUsageId() == null
                || process.reviewedAmount() == null || process.reviewedAmount().compareTo(BigDecimal.ZERO) <= 0
                || !"APPROVED".equals(process.priceReviewConclusion())
                || process.approvedByUserId() == null
                || !FINAL_SELECTION_POSITIONS.contains(process.approverPosition())) {
            return pending("楼栋维修流程缺少有效的正式文件、审价、审批事实或用印事实，不能最终定商");
        }
        DecisionPolicySnapshot policy = governanceRepository.findPolicySnapshot(
                        process.policySnapshotId(), project.tenantId())
                .orElse(null);
        if (policy == null || !project.projectId().equals(policy.projectId())
                || !plan.planId().equals(policy.planId()) || blank(policy.ruleHash())) {
            return pending("楼栋维修流程缺少可核验的议事规则快照，不能最终定商");
        }
        BuildingDecision decision = governanceRepository.findBuildingDecision(
                        process.decisionId(), project.tenantId())
                .orElse(null);
        if (decision == null || !project.projectId().equals(decision.projectId())
                || !plan.planId().equals(decision.planId())
                || !GovernanceResult.PASSED.name().equals(decision.result())) {
            return pending("楼栋维修决定未形成通过结果，不能最终定商");
        }
        GovernanceBasis basis = governanceRepository.findActiveGovernanceBasis(
                        project.projectId(), plan.planId(), project.tenantId())
                .orElse(null);
        if (basis == null || !"BUILDING_REPAIR_DECISION".equals(basis.basisType())
                || !"BUILDING_PROCESS".equals(basis.referenceType())
                || !process.processId().equals(basis.referenceId()) || blank(basis.snapshotHash())) {
            return pending("楼栋维修未形成匹配当前流程的不可变授权依据，不能最终定商");
        }
        String snapshotIssue = selectionAuthorizationSnapshotIssue(basis);
        if (snapshotIssue != null) {
            return pending(snapshotIssue);
        }
        boolean mayConfirm = project.status() == Status.AUTHORIZED && mayConfirmSelection(actor, process);
        return new SelectionAuthorization(
                SelectionAuthorizationStatus.AUTHORIZED, null,
                basis.approvedSupplierSelectionMethod(), basis.approvedSupplierEvaluationRule(),
                basis.minimumInvitedSupplierCount(), basis.minimumValidQuoteCount(),
                basis.nonCompetitiveSelectionBasis(), process.reviewedAmount(), basis.basisId(),
                basis.snapshotHash(), process.processId(), decision.decisionId(), mayConfirm);
    }

    private boolean authorizationStageReached(Status status) {
        return status == Status.AUTHORIZED || status == Status.CONTRACT_EFFECTIVE
                || status == Status.IN_PROGRESS || status == Status.PENDING_ACCEPTANCE
                || status == Status.COMPLETED || status == Status.WARRANTY || status == Status.ARCHIVED;
    }

    private String selectionAuthorizationSnapshotIssue(GovernanceBasis basis) {
        RepairSupplierSelectionMethod method = basis.approvedSupplierSelectionMethod();
        SupplierSelectionEvaluationRule rule = basis.approvedSupplierEvaluationRule();
        if (method == null || rule == null) {
            return "用印授权文件未固化施工单位选择方式和评审规则，不能最终定商";
        }
        Integer invited = basis.minimumInvitedSupplierCount();
        Integer quotes = basis.minimumValidQuoteCount();
        if ((invited != null && invited <= 0) || (quotes != null && quotes <= 0)
                || (invited != null && quotes != null && quotes > invited)) {
            return "用印授权文件中的施工单位选择数量门槛不合法，不能最终定商";
        }
        if (method == RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION) {
            if (rule != SupplierSelectionEvaluationRule.LOWEST_COMPLIANT_QUOTE
                    && rule != SupplierSelectionEvaluationRule.COMPREHENSIVE_EVALUATION) {
                return "竞争性报价授权未使用有效评审规则，不能最终定商";
            }
            if (!blank(basis.nonCompetitiveSelectionBasis())) {
                return "竞争性报价授权混入非竞争定商依据，不能最终定商";
            }
            return null;
        }
        if (rule != SupplierSelectionEvaluationRule.AUTHORIZED_DIRECT_SELECTION) {
            return "非竞争定商授权未使用授权直接选择规则，不能最终定商";
        }
        if (blank(basis.nonCompetitiveSelectionBasis())) {
            return "非竞争定商授权缺少明确依据，不能最终定商";
        }
        if (invited != null || quotes != null) {
            return "非竞争定商授权不应携带虚构的询价数量门槛，不能最终定商";
        }
        return null;
    }

    private boolean mayConfirmSelection(UserContext actor, BuildingProcess process) {
        if (actor == null || !actor.isSysUser() || actor.userId() == null
                || !actor.hasPermission("repair:workorder:governance")
                || !FINAL_SELECTION_POSITIONS.contains(process.approverPosition())) {
            return false;
        }
        return workOrderRepository.findActiveCommitteePosition(process.tenantId(), actor.userId())
                .filter(FINAL_SELECTION_POSITIONS::contains)
                .isPresent();
    }

    private List<RepairFrameworkRelation> eligibleFrameworkRelations(
            SelectionAuthorization authorization, Long tenantId) {
        if (authorization.status() != SelectionAuthorizationStatus.AUTHORIZED
                || authorization.approvedSelectionMethod() != RepairSupplierSelectionMethod.FRAMEWORK_SUPPLIER
                || !authorization.currentActorMayConfirm()) {
            return List.of();
        }
        // 项目没有可核验的服务分类快照时，仅允许关系本身声明为通用适用范围的框架供应商。
        return workOrderRepository.listActiveFrameworkRelations(tenantId, null).stream()
                .filter(relation -> relation.serviceCategory() == null)
                .toList();
    }

    private boolean validateFrameworkRelation(
            SelectionAuthorization authorization,
            Long frameworkRelationId,
            Quote quote,
            Long tenantId) {
        if (authorization.approvedSelectionMethod() != RepairSupplierSelectionMethod.FRAMEWORK_SUPPLIER) {
            if (frameworkRelationId != null) {
                throw support.invalid("非框架定商不得提交 frameworkRelationId");
            }
            return false;
        }
        if (frameworkRelationId == null) {
            throw support.invalid("框架供应商定商必须从有效框架关系中选择 frameworkRelationId");
        }
        boolean eligible = workOrderRepository.listActiveFrameworkRelations(tenantId, null).stream()
                .filter(relation -> relation.serviceCategory() == null)
                .anyMatch(relation -> frameworkRelationId.equals(relation.relationId())
                        && quote.supplierDeptId().equals(relation.supplierDeptId()));
        if (!eligible || !workOrderRepository.frameworkRelationActive(
                frameworkRelationId, tenantId, quote.supplierDeptId(), null)) {
            throw support.invalid("所选框架关系无效、已失效或不属于中选供应商");
        }
        return true;
    }

    private void requireNoLowerConfirmedQuote(Long projectId, Long planId, Long tenantId, Quote selectedQuote) {
        boolean lowerQuoteExists = sourcingRepository.listQuotes(projectId, planId, tenantId).stream()
                .filter(candidate -> candidate.quoteStatus() == RepairSupplierQuoteStatus.ACTIVE)
                .filter(candidate -> candidate.confirmationStatus().confirmedForContract())
                .anyMatch(candidate -> candidate.quoteAmount().compareTo(selectedQuote.quoteAmount()) < 0);
        if (lowerQuoteExists) {
            throw support.invalid("最低合格报价规则下不能确认高于现存有效已确认报价的中选供应商");
        }
    }

    private boolean isSelectionEvidenceDocument(String contentType) {
        return contentType != null && (contentType.startsWith("image/")
                || "application/pdf".equals(contentType)
                || contentType.contains("word") || contentType.contains("excel")
                || contentType.contains("spreadsheet"));
    }

    private SelectionAuthorization pending(String reason) {
        return new SelectionAuthorization(
                SelectionAuthorizationStatus.PENDING_AUTHORIZATION, reason,
                null, null, null, null, null, null, null, null, null, null, false);
    }

    private SelectionAuthorization unsupported(String reason) {
        return new SelectionAuthorization(
                SelectionAuthorizationStatus.UNSUPPORTED_WORKFLOW, reason,
                null, null, null, null, null, null, null, null, null, null, false);
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

    private boolean blank(String value) {
        return trim(value) == null;
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
            List<SupplierWorkPoint> workPoints,
            Invitation invitation,
            Quote latestQuote
    ) {
        public SupplierOpportunity {
            workPoints = workPoints == null ? List.of() : List.copyOf(workPoints);
        }
    }

    public record SupplierWorkPoint(
            Long workPointId,
            String businessName,
            Long buildingId,
            String unitName,
            RepairProject.WorkPointLocationType locationType,
            Long referenceRoomId,
            String commonAreaName,
            String spaceName,
            String orientation,
            String component,
            String specificPart,
            String symptom,
            String proposedMeasure,
            String technicalRequirements,
            BigDecimal quantity,
            String unit
    ) {
    }
}
