// 关联业务：编排维修工程项目创建、实施方案版本、费用分摊与受影响业主快照、附件引用及方案锁定。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.repair.command.CreateRepairPlanVersionCommand;
import com.pangu.application.repair.command.CreateRepairProjectCommand;
import com.pangu.application.repair.command.RepairPlanDraftCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.gateway.RichTextSanitizer;
import com.pangu.domain.gateway.RichTextSanitizer.SanitizedRichText;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.AllocationBasis;
import com.pangu.domain.model.repair.RepairProject.AllocationPreview;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerCandidate;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerPreview;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerSourceType;
import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import com.pangu.domain.model.repair.RepairProject.EvidenceRequirement;
import com.pangu.domain.model.repair.RepairProject.EvidenceStage;
import com.pangu.domain.model.repair.RepairProject.EligibleAffectedOwner;
import com.pangu.domain.model.repair.RepairProject.Item;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestone;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestoneType;
import com.pangu.domain.model.repair.RepairProject.PlanAttachment;
import com.pangu.domain.model.repair.RepairProject.PlanAffectedOwner;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.ScopeType;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProjectSourcing.Quote;
import com.pangu.domain.model.repair.RepairProjectSourcing.Selection;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderEvent;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.policy.RepairAllocationPolicy;
import com.pangu.domain.policy.RepairAllocationPolicy.AllocationDecision;
import com.pangu.domain.policy.RepairAllocationPolicy.AllocationInput;
import com.pangu.domain.policy.RepairCaseLifecyclePolicy;
import com.pangu.domain.policy.RepairWorkflowRoutingPolicy;
import com.pangu.domain.policy.RepairWorkflowRoutingPolicy.RoutingDecision;
import com.pangu.domain.policy.RepairWorkflowRoutingPolicy.RoutingInput;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectSourcingRepository;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

@Service
@RequiredArgsConstructor
public class RepairProjectService {

    private static final Set<String> BUILDING_ACCEPTANCE_ROLES = Set.of(
            "ENGINEERING_SUBMITTER",
            "ENGINEERING_VERIFIER",
            "BUILDING_LEADER_ACCEPTOR",
            "AFFECTED_OWNER_ACCEPTOR");
    private static final Set<String> COMMUNITY_ACCEPTANCE_ROLES = Set.of(
            "ENGINEERING_SUBMITTER",
            "COMMITTEE_EXECUTIVE_APPROVER",
            "COMMITTEE_SEAL_OPERATOR",
            "PROPERTY_OR_THIRD_PARTY_TECHNICAL_COSIGNER");
    private static final Set<EvidenceStage> REQUIRED_EVIDENCE_STAGES = Set.of(
            EvidenceStage.BEFORE_CONSTRUCTION,
            EvidenceStage.MATERIAL_ENTRY,
            EvidenceStage.DURING_CONSTRUCTION,
            EvidenceStage.COMPLETION,
            EvidenceStage.ACCEPTANCE);

    private final RepairProjectRepository projectRepository;
    private final RepairProjectSourcingRepository sourcingRepository;
    private final RepairWorkOrderRepository workOrderRepository;
    private final RepairWorkflowRoutingPolicy routingPolicy;
    private final RepairAllocationPolicy allocationPolicy;
    private final RepairCaseLifecyclePolicy caseLifecyclePolicy;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;
    private final RichTextSanitizer richTextSanitizer;
    private final RepairNarrativeImageService narrativeImageService;

    @Transactional
    public RepairProject.Details createProject(CreateRepairProjectCommand command) {
        UserContext actor = requireActor();
        if (command == null || command.plan() == null) {
            throw invalid("project 和 plan 均为必填项");
        }
        String projectName = requireText(command.projectName(), "projectName");
        String unitName = normalizeUnitName(command.scopeType(), command.unitName());
        RoutingDecision routing = route(command.scopeType(), command.buildingId(), unitName,
                command.fundSource(), command.governancePath());
        validateBuilding(actor.tenantId(), command.scopeType(), command.buildingId());
        AllocationPreview allocation = resolveAllocation(
                actor.tenantId(), command.scopeType(), command.buildingId(), unitName, command.fundSource());

        RepairProject project = projectRepository.insertProject(new RepairProject(
                null, null, actor.tenantId(), projectName, routing.workflowType(), command.scopeType(),
                command.buildingId(), unitName, command.fundSource(), command.governancePath(),
                Status.DRAFT, null, 0, actor.accountId(), actor.userId(), null, null));
        PlanVersion plan = insertPlan(project, command.plan(), 1, actor, allocation);
        event(project, actor, "PROJECT_CREATED", Map.of(
                "workflowType", project.workflowType().name(),
                "planId", plan.planId(),
                "planVersion", plan.versionNo()));
        return details(project.projectId(), actor.tenantId());
    }

    @Transactional
    public RepairProject.Details createPlanVersion(Long projectId, CreateRepairPlanVersionCommand command) {
        UserContext actor = requireActor();
        if (command == null || command.plan() == null || command.expectedProjectVersion() == null) {
            throw invalid("expectedProjectVersion 和 plan 均为必填项");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        if (!Objects.equals(project.version(), command.expectedProjectVersion())) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "项目版本已变化，请刷新后再创建实施方案");
        }
        if (project.status() != Status.DRAFT && project.status() != Status.PLAN_LOCKED) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "当前项目状态不能修改实施方案 status=" + project.status());
        }
        List<PlanVersion> plans = projectRepository.listPlans(projectId, actor.tenantId());
        if (plans.stream().anyMatch(plan -> plan.status() == PlanStatus.DRAFT)) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "当前项目已有未锁定的实施方案草稿");
        }
        int nextVersion = plans.stream().mapToInt(PlanVersion::versionNo).max().orElse(0) + 1;
        AllocationPreview allocation = resolveAllocation(
                actor.tenantId(), project.scopeType(), project.buildingId(), project.unitName(), project.fundSource());
        PlanVersion plan = insertPlan(project, command.plan(), nextVersion, actor, allocation);
        event(project, actor, "PLAN_VERSION_CREATED", Map.of(
                "planId", plan.planId(), "planVersion", plan.versionNo()));
        return details(projectId, actor.tenantId());
    }

    @Transactional
    public RepairProject.Details linkDraftPlanAttachment(
            Long projectId, Long planId, Long attachmentId, AttachmentPurpose purpose) {
        UserContext actor = requireActor();
        if (purpose == null) {
            throw invalid("purpose 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        PlanVersion plan = projectRepository.findPlanForUpdate(planId, projectId, actor.tenantId())
                .orElseThrow(() -> notFound("实施方案不存在"));
        if (plan.status() != PlanStatus.DRAFT) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "锁定方案不能再绑定附件");
        }
        projectRepository.findAttachment(attachmentId, projectId, actor.tenantId())
                .orElseThrow(() -> notFound("项目附件不存在"));
        List<PlanAttachment> existing = projectRepository.listPlanAttachments(planId, actor.tenantId());
        if (existing.stream().anyMatch(reference -> reference.attachmentId().equals(attachmentId)
                && reference.purpose() == purpose)) {
            return details(projectId, actor.tenantId());
        }
        projectRepository.linkPlanAttachment(
                planId, new PlanAttachment(attachmentId, purpose, existing.size() + 1));
        event(project, actor, "PLAN_ATTACHMENT_LINKED", Map.of(
                "planId", planId, "attachmentId", attachmentId, "purpose", purpose.name()));
        return details(projectId, actor.tenantId());
    }

    @Transactional
    public RepairProject.Details lockPlan(Long projectId, Long planId, Integer expectedProjectVersion) {
        UserContext actor = requireActor();
        if (expectedProjectVersion == null) {
            throw invalid("expectedProjectVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        if (!Objects.equals(project.version(), expectedProjectVersion)) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "项目版本已变化，请刷新后再锁定");
        }
        if (project.status() != Status.DRAFT && project.status() != Status.PLAN_LOCKED) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "当前项目状态不能锁定实施方案 status=" + project.status());
        }
        PlanVersion plan = projectRepository.findPlanForUpdate(planId, projectId, actor.tenantId())
                .orElseThrow(() -> notFound("实施方案不存在"));
        if (plan.status() != PlanStatus.DRAFT) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "实施方案已经锁定或被替代");
        }
        List<Item> items = projectRepository.listItems(planId, actor.tenantId());
        List<AllocationRoom> allocation = projectRepository.listAllocationRooms(planId, actor.tenantId());
        List<PlanAffectedOwner> affectedOwners = projectRepository.listPlanAffectedOwners(
                planId, actor.tenantId());
        List<PlanAttachment> attachments = projectRepository.listPlanAttachments(planId, actor.tenantId());
        Selection selection = sourcingRepository.findCurrentSelection(projectId, planId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "锁定实施方案前必须完成供应商报价、比价和定商"));
        Quote selectedQuote = sourcingRepository.findQuote(
                        selection.quoteId(), projectId, planId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "中选报价不存在，禁止锁定实施方案"));
        if (selection.selectionMethod() != plan.supplierSelectionMethod()) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "中选供应商方式与当前实施方案不一致，请重新定商");
        }
        validateLockEvidence(attachments);
        validateBudget(plan.budgetTotal(), items);
        if (allocation.isEmpty()) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "费用承担房屋快照为空，禁止锁定实施方案");
        }
        if (project.workflowType() == RepairWorkflowType.BUILDING_REPAIR) {
            if (affectedOwners.isEmpty()) {
                throw new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "受影响业主名单为空，禁止锁定实施方案");
            }
            long affectedOwnerCount = affectedOwners.stream().map(PlanAffectedOwner::ownerUid).distinct().count();
            if (plan.minimumAffectedOwnerAcceptors() > affectedOwnerCount) {
                throw new RepairWorkOrderApplicationException(
                        INVALID_STATUS,
                        "最低有效验收人数不能超过锁定范围内的受影响业主人数");
            }
        }
        String snapshotHash = snapshotHash(
                project, plan, items, allocation, affectedOwners, attachments, selection, selectedQuote);
        if (projectRepository.lockPlan(planId, projectId, actor.tenantId(), snapshotHash, actor.userId()) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "实施方案锁定失败，请刷新后重试");
        }
        projectRepository.supersedeLockedPlans(projectId, actor.tenantId(), planId);
        if (projectRepository.activatePlan(
                projectId, actor.tenantId(), planId, expectedProjectVersion) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "项目状态已变化，请刷新后重试");
        }
        event(project, actor, "PLAN_LOCKED", Map.of(
                "planId", planId, "planVersion", plan.versionNo(), "snapshotHash", snapshotHash,
                "selectionId", selection.selectionId(), "quoteId", selection.quoteId(),
                "supplierDeptId", selection.supplierDeptId()));
        return details(projectId, actor.tenantId());
    }

    @Transactional(readOnly = true)
    public RepairProject.Details findProject(Long projectId) {
        UserContext actor = requireActor();
        return details(projectId, actor.tenantId());
    }

    @Transactional(readOnly = true)
    public Page<RepairProject> pageProjects(Status status, String keyword, int page, int size) {
        UserContext actor = requireActor();
        String normalizedKeyword = trim(keyword);
        int offset = (page - 1) * size;
        return new Page<>(
                projectRepository.listProjects(actor.tenantId(), status, normalizedKeyword, offset, size),
                projectRepository.countProjects(actor.tenantId(), status, normalizedKeyword),
                page,
                size);
    }

    @Transactional(readOnly = true)
    public AllocationPreview previewAllocation(ScopeType scopeType, Long buildingId, String unitName) {
        UserContext actor = requireActor();
        String normalizedUnitName = normalizeUnitName(scopeType, unitName);
        RepairProject.FundSource fundSource = scopeType == ScopeType.COMMUNITY
                ? RepairProject.FundSource.COMMUNITY_MAINTENANCE_FUND
                : RepairProject.FundSource.BUILDING_MAINTENANCE_FUND;
        RepairProject.GovernancePath governancePath = scopeType == ScopeType.COMMUNITY
                ? RepairProject.GovernancePath.COMMUNITY_ASSEMBLY_DECISION
                : RepairProject.GovernancePath.BUILDING_REPAIR_DECISION;
        route(scopeType, buildingId, normalizedUnitName, fundSource, governancePath);
        validateBuilding(actor.tenantId(), scopeType, buildingId);
        return resolveAllocation(actor.tenantId(), scopeType, buildingId, normalizedUnitName, fundSource);
    }

    @Transactional(readOnly = true)
    public AffectedOwnerPreview previewAffectedOwners(
            ScopeType scopeType, Long buildingId, String unitName) {
        UserContext actor = requireActor();
        if (scopeType == ScopeType.COMMUNITY) {
            throw invalid("全小区公共维修由业委会验收，不生成楼栋受影响业主名单");
        }
        String normalizedUnitName = normalizeUnitName(scopeType, unitName);
        route(scopeType, buildingId, normalizedUnitName,
                RepairProject.FundSource.BUILDING_MAINTENANCE_FUND,
                RepairProject.GovernancePath.BUILDING_REPAIR_DECISION);
        validateBuilding(actor.tenantId(), scopeType, buildingId);
        AllocationPreview allocation = resolveAllocation(
                actor.tenantId(), scopeType, buildingId, normalizedUnitName,
                RepairProject.FundSource.BUILDING_MAINTENANCE_FUND);
        List<EligibleAffectedOwner> eligible = projectRepository.listEligibleAffectedOwners(
                actor.tenantId(), scopeType, buildingId, normalizedUnitName);
        String reason = defaultAffectedReason(allocation.scopeLabel());
        return new AffectedOwnerPreview(
                allocation.scopeLabel(),
                eligible.stream().map(EligibleAffectedOwner::ownerUid).distinct().count(),
                eligible.stream().map(candidate -> new AffectedOwnerCandidate(
                        candidate.roomId(), candidate.buildingId(), candidate.buildingName(),
                        candidate.unitName(), candidate.roomName(), reason)).toList());
    }

    private PlanVersion insertPlan(
            RepairProject project,
            RepairPlanDraftCommand draft,
            int versionNo,
            UserContext actor,
            AllocationPreview allocation) {
        if (draft == null) {
            throw invalid("plan 必填");
        }
        // 先清洗再持久化和计算快照，保证管理端预览、业主端披露与锁定哈希使用同一正文。
        PlanNarratives narratives = sanitizeNarratives(draft);
        validateDraft(project, draft);
        RepairSupplierSelectionMethod supplierSelectionMethod = draft.supplierSelectionMethod() == null
                ? RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION
                : draft.supplierSelectionMethod();
        String supplierSelectionBasis = supplierSelectionBasis(
                supplierSelectionMethod, draft.supplierSelectionReason());
        AffectedOwnerSelection affectedOwnerSelection = resolveAffectedOwnerSelection(
                project, draft, allocation);
        narrativeImageService.assertDraftImagesUsable(narratives.imageIds(), actor);
        PlanVersion plan = projectRepository.insertPlan(new PlanVersion(
                null, project.projectId(), project.tenantId(), versionNo,
                narratives.planDescription(),
                draft.budgetTotal(), project.fundSource(), allocation.allocationRuleType(),
                allocation.allocationRuleDescription(), supplierSelectionMethod,
                supplierSelectionBasis,
                narratives.constructionManagementRequirements(), draft.evidenceRequirements(),
                narratives.safetyRequirements(),
                requireText(draft.acceptanceMethod(), "acceptanceMethod"), acceptanceRoles(project.workflowType()),
                affectedOwnerSelection.scopeDescription(), draft.minimumAffectedOwnerAcceptors(),
                draft.affectedOwnerPassRule(), draft.affectedOwnerApprovalRatio(), draft.settlementMethod(),
                draft.plannedStartDate(), draft.plannedCompletionDate(), draft.warrantyDays(),
                project.governancePath(), draft.priceReviewRequired(), draft.paymentMilestones(),
                PlanStatus.DRAFT, null, actor.accountId(), actor.userId(), null, null, null));
        narrativeImageService.bindDraftImages(
                narratives.imageIds(), actor, project.projectId(), plan.planId());
        insertItems(project, plan, draft.items(), actor);
        List<AllocationRoom> allocationRooms = projectRepository.snapshotAllocationRooms(
                plan.planId(), project.tenantId(), project.scopeType(), project.buildingId(), project.unitName());
        if (allocationRooms.isEmpty()) {
            throw invalid("当前维修范围没有已核验的费用承担房屋，不能形成实施方案");
        }
        for (PlanAffectedOwner affectedOwner : affectedOwnerSelection.affectedOwners()) {
            projectRepository.insertPlanAffectedOwner(new PlanAffectedOwner(
                    null, plan.planId(), project.tenantId(), affectedOwner.roomId(),
                    affectedOwner.buildingId(), affectedOwner.buildingName(), affectedOwner.unitName(),
                    affectedOwner.roomName(), affectedOwner.ownerUid(), affectedOwner.affectedReason(),
                    affectedOwner.sourceType(), null));
        }
        if (!affectedOwnerSelection.affectedOwners().isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("planId", plan.planId());
            payload.put("affectedOwnerCount", affectedOwnerSelection.ownerCount());
            payload.put("sourceType", affectedOwnerSelection.sourceType());
            if (affectedOwnerSelection.adjustmentReason() != null) {
                payload.put("adjustmentReason", affectedOwnerSelection.adjustmentReason());
            }
            event(project, actor, "AFFECTED_OWNER_SCOPE_LOCKED", payload);
        }
        linkInitialAttachments(project, plan, draft.attachments());
        return plan;
    }

    private void insertItems(
            RepairProject project,
            PlanVersion plan,
            List<RepairPlanDraftCommand.ItemDraft> drafts,
            UserContext actor) {
        int sortOrder = 0;
        Set<String> itemNumbers = new LinkedHashSet<>();
        Map<Long, RepairWorkOrder> linkedWorkOrders = new LinkedHashMap<>();
        for (RepairPlanDraftCommand.ItemDraft draft : drafts) {
            sortOrder++;
            String itemNo = requireText(draft.itemNo(), "items.itemNo");
            if (!itemNumbers.add(itemNo)) {
                throw invalid("工程项编号重复 itemNo=" + itemNo);
            }
            ItemLocation location = validateItemLocation(project, draft);
            for (Long workOrderId : new LinkedHashSet<>(draft.linkedWorkOrderIds())) {
                if (workOrderId == null) {
                    throw invalid("linkedWorkOrderIds 不能包含空值");
                }
                linkedWorkOrders.computeIfAbsent(
                        workOrderId, ignored -> validateLinkedWorkOrder(project, workOrderId));
            }
            Item item = projectRepository.insertItem(new Item(
                    null, project.projectId(), plan.planId(), project.tenantId(), itemNo,
                    location.buildingId(), location.unitName(), draft.roomId(),
                    requireText(draft.locationText(), "items.locationText"),
                    requireText(draft.workContent(), "items.workContent"), draft.quantity(),
                    requireText(draft.unit(), "items.unit"), draft.estimatedUnitPrice(),
                    draft.estimatedAmount(), sortOrder, List.of(), null));
            for (Long workOrderId : new LinkedHashSet<>(draft.linkedWorkOrderIds())) {
                projectRepository.linkItemToWorkOrder(item.itemId(), workOrderId, project.tenantId());
            }
        }
        markCasesLinked(project, plan, linkedWorkOrders.values(), actor);
    }

    private void linkInitialAttachments(
            RepairProject project,
            PlanVersion plan,
            List<RepairPlanDraftCommand.AttachmentReference> references) {
        int sortOrder = 0;
        Set<String> unique = new LinkedHashSet<>();
        for (RepairPlanDraftCommand.AttachmentReference reference : references) {
            if (reference == null || reference.attachmentId() == null || reference.purpose() == null) {
                throw invalid("实施方案附件引用必须包含 attachmentId 和 purpose");
            }
            String key = reference.attachmentId() + ":" + reference.purpose();
            if (!unique.add(key)) {
                throw invalid("实施方案附件引用重复 attachmentId=" + reference.attachmentId());
            }
            projectRepository.findAttachment(reference.attachmentId(), project.projectId(), project.tenantId())
                    .orElseThrow(() -> notFound("项目附件不存在 attachmentId=" + reference.attachmentId()));
            projectRepository.linkPlanAttachment(plan.planId(),
                    new PlanAttachment(reference.attachmentId(), reference.purpose(), ++sortOrder));
        }
    }

    private void validateDraft(RepairProject project, RepairPlanDraftCommand draft) {
        requirePositive(draft.budgetTotal(), "budgetTotal");
        if (draft.settlementMethod() == null) {
            throw invalid("settlementMethod 必填");
        }
        requireText(draft.acceptanceMethod(), "acceptanceMethod");
        if (draft.plannedStartDate() == null || draft.plannedCompletionDate() == null
                || draft.plannedCompletionDate().isBefore(draft.plannedStartDate())) {
            throw invalid("计划完工日期不得早于计划开工日期");
        }
        if (draft.warrantyDays() == null || draft.warrantyDays() < 0) {
            throw invalid("warrantyDays 不得小于 0");
        }
        validateEvidenceRequirements(draft.evidenceRequirements());
        validatePaymentMilestones(draft.paymentMilestones(), draft.priceReviewRequired());
        validateAcceptanceRule(project.workflowType(), draft);
        if (draft.items().isEmpty()) {
            throw invalid("实施方案至少包含一个工程项");
        }
        validateItemAmounts(draft.items(), draft.budgetTotal());
    }

    private void validateEvidenceRequirements(List<EvidenceRequirement> requirements) {
        Map<EvidenceStage, EvidenceRequirement> byStage = new EnumMap<>(EvidenceStage.class);
        for (EvidenceRequirement requirement : requirements) {
            if (requirement == null || requirement.stage() == null) {
                throw invalid("施工证据阶段不能为空");
            }
            requireText(requirement.description(), "evidenceRequirements.description");
            if (byStage.put(requirement.stage(), requirement) != null) {
                throw invalid("施工证据阶段重复 stage=" + requirement.stage());
            }
        }
        for (EvidenceStage stage : REQUIRED_EVIDENCE_STAGES) {
            EvidenceRequirement requirement = byStage.get(stage);
            if (requirement == null || !requirement.required()) {
                throw invalid("实施方案必须把施工证据阶段设为必需 stage=" + stage);
            }
        }
    }

    private void validatePaymentMilestones(List<PaymentMilestone> milestones, boolean priceReviewRequired) {
        Map<PaymentMilestoneType, Set<String>> mandatoryEvidence = Map.of(
                PaymentMilestoneType.ADVANCE, Set.of("SIGNED_CONTRACT"),
                PaymentMilestoneType.PROGRESS, Set.of("PROGRESS_RECORD"),
                PaymentMilestoneType.COMPLETION, Set.of("ACCEPTANCE", "SETTLEMENT"),
                PaymentMilestoneType.WARRANTY_RELEASE, Set.of("WARRANTY_EXPIRED_CERTIFICATE"));
        Map<PaymentMilestoneType, PaymentMilestone> byType = new EnumMap<>(PaymentMilestoneType.class);
        for (PaymentMilestone milestone : milestones) {
            if (milestone == null || milestone.type() == null) {
                throw invalid("付款节点类型不能为空");
            }
            requireRatio(milestone.maximumContractRatio(), "paymentMilestones.maximumContractRatio");
            if (milestone.requiredEvidenceCodes().isEmpty()
                    || milestone.requiredEvidenceCodes().stream().anyMatch(this::isBlank)) {
                throw invalid("每个付款节点必须配置结构化必需材料代码");
            }
            Set<String> evidenceCodes = new LinkedHashSet<>(milestone.requiredEvidenceCodes());
            if (evidenceCodes.size() != milestone.requiredEvidenceCodes().size()) {
                throw invalid("同一付款节点的必需材料代码不能重复");
            }
            if (evidenceCodes.stream().anyMatch(code -> !code.equals(code.trim().toUpperCase()))) {
                throw invalid("付款材料代码必须使用大写英文标识");
            }
            if (!evidenceCodes.containsAll(mandatoryEvidence.get(milestone.type()))) {
                throw invalid("付款节点缺少平台必需材料代码 type=" + milestone.type());
            }
            if (byType.put(milestone.type(), milestone) != null) {
                throw invalid("付款节点重复 type=" + milestone.type());
            }
        }
        for (PaymentMilestoneType type : PaymentMilestoneType.values()) {
            if (!byType.containsKey(type)) {
                throw invalid("缺少付款节点 type=" + type);
            }
        }
        if (byType.get(PaymentMilestoneType.ADVANCE).maximumContractRatio()
                .compareTo(new BigDecimal("0.30")) > 0) {
            throw invalid("首次支取比例不得超过合同总价 30%");
        }
        if (priceReviewRequired
                && byType.get(PaymentMilestoneType.PROGRESS).maximumContractRatio()
                .compareTo(new BigDecimal("0.90")) > 0) {
            throw invalid("审价完成前累计进度款比例不得超过合同总价 90%");
        }
        BigDecimal previous = BigDecimal.ZERO;
        for (PaymentMilestoneType type : PaymentMilestoneType.values()) {
            BigDecimal current = byType.get(type).maximumContractRatio();
            if (current.compareTo(previous) < 0) {
                throw invalid("付款节点累计比例必须按业务顺序递增 type=" + type);
            }
            previous = current;
        }
    }

    private void validateAcceptanceRule(RepairWorkflowType workflowType, RepairPlanDraftCommand draft) {
        if (workflowType == RepairWorkflowType.BUILDING_REPAIR) {
            if (draft.minimumAffectedOwnerAcceptors() == null || draft.minimumAffectedOwnerAcceptors() <= 0) {
                throw invalid("楼栋维修必须锁定受影响业主最低有效验收人数");
            }
            if (draft.affectedOwnerPassRule() == null) {
                throw invalid("楼栋维修必须锁定受影响业主验收通过规则");
            }
            requireRatio(draft.affectedOwnerApprovalRatio(), "affectedOwnerApprovalRatio");
            if (draft.affectedOwnerPassRule() == RepairProject.AffectedOwnerPassRule.ALL
                    && draft.affectedOwnerApprovalRatio().compareTo(BigDecimal.ONE) != 0) {
                throw invalid("ALL 通过规则的同意比例必须为 1");
            }
            return;
        }
        if (!draft.affectedOwners().isEmpty()
                || trim(draft.affectedOwnerAdjustmentReason()) != null
                || draft.minimumAffectedOwnerAcceptors() != null
                || draft.affectedOwnerPassRule() != null
                || draft.affectedOwnerApprovalRatio() != null) {
            throw invalid("全小区公共维修由业委会验收，不能配置楼栋受影响业主验收规则");
        }
    }

    private String supplierSelectionBasis(
            RepairSupplierSelectionMethod method, String submittedBasis) {
        if (method == RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION) {
            return null;
        }
        return requireText(submittedBasis, "非竞争性供应商遴选方式依据");
    }

    /**
     * 费用承担范围和受影响业主名单是两个事实：前者依法确定分摊，后者用于楼栋验收。
     * 系统先按项目范围推荐，物业如调整名单必须说明原因并保留结构化快照。
     */
    private AffectedOwnerSelection resolveAffectedOwnerSelection(
            RepairProject project, RepairPlanDraftCommand draft, AllocationPreview allocation) {
        if (project.workflowType() != RepairWorkflowType.BUILDING_REPAIR) {
            return new AffectedOwnerSelection(List.of(), null, 0, null, null);
        }
        List<EligibleAffectedOwner> eligible = projectRepository.listEligibleAffectedOwners(
                project.tenantId(), project.scopeType(), project.buildingId(), project.unitName());
        if (eligible.isEmpty()) {
            throw invalid("当前维修范围没有已核验的受影响业主候选房屋");
        }
        Map<Long, EligibleAffectedOwner> eligibleByRoom = new LinkedHashMap<>();
        eligible.forEach(candidate -> eligibleByRoom.put(candidate.roomId(), candidate));

        Map<Long, RepairPlanDraftCommand.AffectedOwnerDraft> submittedByRoom = new LinkedHashMap<>();
        for (RepairPlanDraftCommand.AffectedOwnerDraft submitted : draft.affectedOwners()) {
            if (submitted == null || submitted.roomId() == null) {
                throw invalid("affectedOwners.roomId 必填");
            }
            if (!eligibleByRoom.containsKey(submitted.roomId())) {
                throw invalid("受影响业主房屋不在当前维修范围 roomId=" + submitted.roomId());
            }
            if (submittedByRoom.put(submitted.roomId(), submitted) != null) {
                throw invalid("受影响业主房屋重复 roomId=" + submitted.roomId());
            }
        }

        boolean useSystemRecommendation = submittedByRoom.isEmpty();
        Set<Long> selectedRoomIds = useSystemRecommendation
                ? new LinkedHashSet<>(eligibleByRoom.keySet())
                : new LinkedHashSet<>(submittedByRoom.keySet());
        if (selectedRoomIds.isEmpty()) {
            throw invalid("楼栋维修至少需要锁定一名受影响业主");
        }
        String defaultReason = defaultAffectedReason(allocation.scopeLabel());
        boolean reasonAdjusted = submittedByRoom.values().stream()
                .map(RepairPlanDraftCommand.AffectedOwnerDraft::affectedReason)
                .map(this::trim)
                .filter(Objects::nonNull)
                .anyMatch(reason -> !reason.equals(defaultReason));
        boolean adjusted = !selectedRoomIds.equals(eligibleByRoom.keySet()) || reasonAdjusted;
        String adjustmentReason = trim(draft.affectedOwnerAdjustmentReason());
        if (adjusted && adjustmentReason == null) {
            throw invalid("调整系统推荐的受影响业主名单时必须填写调整原因");
        }
        AffectedOwnerSourceType sourceType = adjusted
                ? AffectedOwnerSourceType.PROPERTY_ADJUSTED
                : AffectedOwnerSourceType.SYSTEM_RECOMMENDED;
        List<PlanAffectedOwner> selected = selectedRoomIds.stream().map(roomId -> {
            EligibleAffectedOwner candidate = eligibleByRoom.get(roomId);
            RepairPlanDraftCommand.AffectedOwnerDraft submitted = submittedByRoom.get(roomId);
            String affectedReason = submitted == null || trim(submitted.affectedReason()) == null
                    ? defaultReason
                    : trim(submitted.affectedReason());
            return new PlanAffectedOwner(
                    null, null, project.tenantId(), candidate.roomId(), candidate.buildingId(),
                    candidate.buildingName(), candidate.unitName(), candidate.roomName(), candidate.ownerUid(),
                    affectedReason, sourceType, null);
        }).toList();
        long ownerCount = selected.stream().map(PlanAffectedOwner::ownerUid).distinct().count();
        if (draft.minimumAffectedOwnerAcceptors() > ownerCount) {
            throw invalid("最低有效验收人数不能超过已选择的受影响业主人数");
        }
        String scopeDescription = allocation.scopeLabel() + " · 已锁定 " + ownerCount + " 名受影响业主";
        return new AffectedOwnerSelection(
                selected, scopeDescription, ownerCount, sourceType.name(), adjusted ? adjustmentReason : null);
    }

    private String defaultAffectedReason(String scopeLabel) {
        return scopeLabel + "维修范围内受影响，参与楼栋维修验收";
    }

    private void validateItemAmounts(
            List<RepairPlanDraftCommand.ItemDraft> items, BigDecimal budgetTotal) {
        BigDecimal total = BigDecimal.ZERO;
        for (RepairPlanDraftCommand.ItemDraft item : items) {
            if (item == null) {
                throw invalid("工程项不能为空");
            }
            requirePositive(item.quantity(), "items.quantity");
            requireNonNegative(item.estimatedUnitPrice(), "items.estimatedUnitPrice");
            requireNonNegative(item.estimatedAmount(), "items.estimatedAmount");
            BigDecimal calculated = item.quantity().multiply(item.estimatedUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            if (calculated.compareTo(item.estimatedAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw invalid("工程项预算金额必须等于数量乘以估算单价 itemNo=" + item.itemNo());
            }
            total = total.add(item.estimatedAmount());
        }
        if (total.setScale(2, RoundingMode.HALF_UP)
                .compareTo(budgetTotal.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw invalid("工程项预算合计必须等于实施方案预算总额");
        }
    }

    private ItemLocation validateItemLocation(RepairProject project, RepairPlanDraftCommand.ItemDraft draft) {
        Long buildingId = draft.buildingId();
        String unitName = trim(draft.unitName());
        if (project.scopeType() == ScopeType.BUILDING || project.scopeType() == ScopeType.BUILDING_UNIT) {
            buildingId = buildingId == null ? project.buildingId() : buildingId;
            if (!project.buildingId().equals(buildingId)) {
                throw invalid("楼栋维修工程项不能使用其他楼栋 buildingId=" + buildingId);
            }
            if (project.scopeType() == ScopeType.BUILDING_UNIT) {
                unitName = unitName == null ? project.unitName() : unitName;
                if (!project.unitName().equals(unitName)) {
                    throw invalid("单元维修工程项不能使用其他单元 unitName=" + unitName);
                }
            }
        } else if (buildingId != null && !workOrderRepository.buildingExists(project.tenantId(), buildingId)) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.BUILDING_NOT_IN_SCOPE,
                    "工程项楼栋不在当前小区 buildingId=" + buildingId);
        }
        if (draft.roomId() != null
                && (buildingId == null || !workOrderRepository.roomExists(project.tenantId(), buildingId, draft.roomId()))) {
            throw invalid("工程项房屋不在所选楼栋 roomId=" + draft.roomId());
        }
        return new ItemLocation(buildingId, unitName);
    }

    private RepairWorkOrder validateLinkedWorkOrder(RepairProject project, Long workOrderId) {
        RepairWorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> notFound("关联报修事项不存在 workOrderId=" + workOrderId));
        if (!project.tenantId().equals(workOrder.tenantId())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "禁止跨小区关联报修事项");
        }
        RepairCaseLifecyclePolicy.Decision decision = caseLifecyclePolicy.assessProjectLink(
                workOrder, project.workflowType(), project.buildingId());
        if (!decision.allowed()) {
            throw invalid(decision.reason() + " workOrderId=" + workOrderId);
        }
        return workOrder;
    }

    /** 项目与工程项创建在同一事务内，只有全部结构化方案校验通过后才交接报修事项状态。 */
    private void markCasesLinked(
            RepairProject project,
            PlanVersion plan,
            Iterable<RepairWorkOrder> workOrders,
            UserContext actor) {
        for (RepairWorkOrder workOrder : workOrders) {
            if (workOrder.status() == RepairWorkOrderStatus.PROJECT_LINKED) {
                continue;
            }
            RepairWorkOrder linked = workOrder.withStatus(
                    RepairWorkOrderStatus.PROJECT_LINKED, false, true, false);
            if (workOrderRepository.update(linked) != 1) {
                throw new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "报修事项状态已变化，请刷新后重新关联 workOrderId=" + workOrder.workOrderId());
            }
            workOrderRepository.insertEvent(new RepairWorkOrderEvent(
                    null,
                    workOrder.workOrderId(),
                    workOrder.tenantId(),
                    "LINK_PROJECT",
                    workOrder.status(),
                    RepairWorkOrderStatus.PROJECT_LINKED,
                    actor.accountId(),
                    actor.identityType().name(),
                    actor.activeIdentityId(),
                    "报修事项已交接维修工程项目",
                    workOrderLinkPayload(project, plan),
                    null));
        }
    }

    private String workOrderLinkPayload(RepairProject project, PlanVersion plan) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "projectId", project.projectId(),
                    "projectNo", project.projectNo(),
                    "workflowType", project.workflowType().name(),
                    "planId", plan.planId(),
                    "planVersion", plan.versionNo()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("报修事项关联工程审计事件序列化失败", ex);
        }
    }

    private void validateBuilding(Long tenantId, ScopeType scopeType, Long buildingId) {
        if (scopeType == null) {
            throw invalid("scopeType 必填");
        }
        if (buildingId != null && !workOrderRepository.buildingExists(tenantId, buildingId)) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.BUILDING_NOT_IN_SCOPE,
                    "楼栋不在当前小区 buildingId=" + buildingId);
        }
    }

    private RoutingDecision route(
            ScopeType scopeType,
            Long buildingId,
            String unitName,
            RepairProject.FundSource fundSource,
            RepairProject.GovernancePath governancePath) {
        RoutingDecision routing = routingPolicy.route(
                new RoutingInput(scopeType, buildingId, unitName, fundSource, governancePath));
        if (!routing.allowed()) {
            throw invalid(routing.rejectionReason());
        }
        return routing;
    }

    private AllocationPreview resolveAllocation(
            Long tenantId,
            ScopeType scopeType,
            Long buildingId,
            String unitName,
            RepairProject.FundSource fundSource) {
        AllocationBasis basis = projectRepository.findAllocationBasis(
                        tenantId, scopeType, buildingId, unitName)
                .orElseThrow(() -> invalid("当前小区没有可用的费用承担产权名册"));
        if (basis.roomCount() <= 0 || basis.totalBuildArea() == null
                || basis.totalBuildArea().signum() <= 0) {
            throw invalid("当前维修范围没有已核验且面积有效的费用承担房屋");
        }
        AllocationDecision decision = allocationPolicy.resolve(
                new AllocationInput(scopeType, fundSource, basis.scopeLabel()));
        if (!decision.supported()) {
            throw invalid(decision.rejectionReason());
        }
        return new AllocationPreview(
                scopeType, fundSource, basis.scopeLabel(), basis.roomCount(), basis.ownerCount(),
                basis.totalBuildArea(), decision.ruleType(), decision.ruleDescription(), decision.legalBasis());
    }

    private void validateBudget(BigDecimal budgetTotal, List<Item> items) {
        BigDecimal actual = items.stream()
                .map(Item::estimatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (actual.compareTo(budgetTotal.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "工程项预算合计与实施方案预算总额不一致");
        }
    }

    private void validateLockEvidence(List<PlanAttachment> attachments) {
        Set<AttachmentPurpose> purposes = attachments.stream()
                .map(PlanAttachment::purpose)
                .collect(java.util.stream.Collectors.toSet());
        if (!purposes.contains(AttachmentPurpose.SITE_PHOTO)) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "锁定实施方案前必须关联现场照片");
        }
    }

    private String snapshotHash(
            RepairProject project,
            PlanVersion plan,
            List<Item> items,
            List<AllocationRoom> allocation,
            List<PlanAffectedOwner> affectedOwners,
            List<PlanAttachment> attachments,
            Selection selection,
            Quote selectedQuote) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectId", project.projectId());
        snapshot.put("workflowType", project.workflowType());
        snapshot.put("scopeType", project.scopeType());
        snapshot.put("buildingId", project.buildingId());
        snapshot.put("unitName", project.unitName());
        snapshot.put("fundSource", project.fundSource());
        snapshot.put("governancePath", project.governancePath());
        snapshot.put("plan", immutablePlanSnapshot(plan));
        snapshot.put("items", items.stream().sorted(Comparator.comparing(Item::sortOrder)).toList());
        snapshot.put("allocation", allocation.stream().sorted(Comparator.comparing(AllocationRoom::roomId)).toList());
        snapshot.put("affectedOwners", affectedOwners.stream()
                .sorted(Comparator.comparing(PlanAffectedOwner::roomId)).toList());
        snapshot.put("attachments", attachments.stream().sorted(Comparator.comparing(PlanAttachment::sortOrder)).toList());
        snapshot.put("supplierSelection", selection);
        // 锁定哈希同时绑定中选报价原件摘要和结构化明细，避免后续合同引用另一份报价。
        snapshot.put("selectedQuote", selectedQuote);
        try {
            byte[] canonical = objectMapper.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("维修实施方案快照哈希生成失败", ex);
        }
    }

    private RepairProject.Details details(Long projectId, Long tenantId) {
        RepairProject project = projectRepository.findProject(projectId, tenantId)
                .orElseThrow(() -> notFound("维修工程项目不存在"));
        List<PlanVersion> storedPlans = projectRepository.listPlans(projectId, tenantId);
        List<PlanVersion> plans = storedPlans.stream()
                .map(plan -> plan.withPlanDescription(narrativeImageService.resolveForPlan(
                        plan.planId(), tenantId, plan.planDescription())))
                .toList();
        Long currentPlanId = plans.stream()
                .filter(plan -> plan.status() == PlanStatus.DRAFT)
                .map(PlanVersion::planId)
                .findFirst()
                .orElse(project.activePlanId());
        if (currentPlanId == null && !plans.isEmpty()) {
            currentPlanId = plans.get(0).planId();
        }
        return new RepairProject.Details(
                project,
                plans,
                currentPlanId == null ? List.of() : projectRepository.listItems(currentPlanId, tenantId),
                currentPlanId == null ? List.of() : projectRepository.listAllocationRooms(currentPlanId, tenantId),
                currentPlanId == null ? null : projectRepository.findAllocationSnapshotBasis(currentPlanId, tenantId)
                        .orElse(null),
                currentPlanId == null ? List.of() : projectRepository.listPlanAffectedOwners(currentPlanId, tenantId),
                projectRepository.listAttachments(projectId, tenantId),
                currentPlanId == null ? List.of() : projectRepository.listPlanAttachments(currentPlanId, tenantId));
    }

    private List<String> acceptanceRoles(RepairWorkflowType workflowType) {
        Set<String> roles = workflowType == RepairWorkflowType.BUILDING_REPAIR
                ? BUILDING_ACCEPTANCE_ROLES
                : COMMUNITY_ACCEPTANCE_ROLES;
        return roles.stream().sorted().toList();
    }

    /** 只把锁定后不可变化的业务事实纳入哈希，避免状态和锁定时间使快照无法复算。 */
    private Map<String, Object> immutablePlanSnapshot(PlanVersion plan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planId", plan.planId());
        snapshot.put("versionNo", plan.versionNo());
        snapshot.put("planDescription", plan.planDescription());
        snapshot.put("budgetTotal", plan.budgetTotal());
        snapshot.put("fundSource", plan.fundSource());
        snapshot.put("allocationRuleType", plan.allocationRuleType());
        snapshot.put("allocationRuleDescription", plan.allocationRuleDescription());
        snapshot.put("supplierSelectionMethod", plan.supplierSelectionMethod());
        snapshot.put("supplierSelectionReason", plan.supplierSelectionReason());
        snapshot.put("constructionManagementRequirements", plan.constructionManagementRequirements());
        snapshot.put("evidenceRequirements", plan.evidenceRequirements());
        snapshot.put("safetyRequirements", plan.safetyRequirements());
        snapshot.put("acceptanceMethod", plan.acceptanceMethod());
        snapshot.put("requiredAcceptanceRoles", plan.requiredAcceptanceRoles());
        snapshot.put("affectedOwnerScopeDescription", plan.affectedOwnerScopeDescription());
        snapshot.put("minimumAffectedOwnerAcceptors", plan.minimumAffectedOwnerAcceptors());
        snapshot.put("affectedOwnerPassRule", plan.affectedOwnerPassRule());
        snapshot.put("affectedOwnerApprovalRatio", plan.affectedOwnerApprovalRatio());
        snapshot.put("settlementMethod", plan.settlementMethod());
        snapshot.put("plannedStartDate", plan.plannedStartDate());
        snapshot.put("plannedCompletionDate", plan.plannedCompletionDate());
        snapshot.put("warrantyDays", plan.warrantyDays());
        snapshot.put("governancePath", plan.governancePath());
        snapshot.put("priceReviewRequired", plan.priceReviewRequired());
        snapshot.put("paymentMilestones", plan.paymentMilestones());
        return snapshot;
    }

    private void event(RepairProject project, UserContext actor, String action, Map<String, Object> payload) {
        try {
            projectRepository.insertEvent(
                    project.projectId(), project.tenantId(), action, actor.accountId(), actor.userId(),
                    objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修工程审计事件序列化失败", ex);
        }
    }

    private RepairProject loadProjectForUpdate(Long projectId, Long tenantId) {
        if (projectId == null) {
            throw invalid("projectId 必填");
        }
        return projectRepository.findProjectForUpdate(projectId, tenantId)
                .orElseThrow(() -> notFound("维修工程项目不存在"));
    }

    private UserContext requireActor() {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.userId() == null
                || actor.accountId() == null || actor.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前小区管理端工作身份");
        }
        return actor;
    }

    private String normalizeUnitName(ScopeType scopeType, String unitName) {
        String normalized = trim(unitName);
        return scopeType == ScopeType.BUILDING_UNIT ? normalized : null;
    }

    private String requireText(String value, String field) {
        String normalized = trim(value);
        if (normalized == null) {
            throw invalid(field + " 必填");
        }
        return normalized;
    }

    private PlanNarratives sanitizeNarratives(RepairPlanDraftCommand draft) {
        SanitizedRichText planDescription = requireRichText(draft.planDescription(), "planDescription");
        SanitizedRichText construction = requireTextOnlyRichText(
                draft.constructionManagementRequirements(), "constructionManagementRequirements");
        SanitizedRichText safety = requireTextOnlyRichText(draft.safetyRequirements(), "safetyRequirements");
        return new PlanNarratives(
                planDescription.html(), planDescription.narrativeImageIds(),
                construction.html(), safety.html());
    }

    private SanitizedRichText requireRichText(String value, String field) {
        SanitizedRichText sanitized = richTextSanitizer.sanitize(value);
        if (sanitized.isBlank()) {
            throw invalid(field + " 必填");
        }
        return sanitized;
    }

    private SanitizedRichText requireTextOnlyRichText(String value, String field) {
        SanitizedRichText sanitized = requireRichText(value, field);
        if (!sanitized.narrativeImageIds().isEmpty()) {
            throw invalid(field + " 不支持正文图片");
        }
        return sanitized;
    }

    private record PlanNarratives(
            String planDescription,
            Set<Long> imageIds,
            String constructionManagementRequirements,
            String safetyRequirements) {
    }

    private record AffectedOwnerSelection(
            List<PlanAffectedOwner> affectedOwners,
            String scopeDescription,
            long ownerCount,
            String sourceType,
            String adjustmentReason
    ) {
        private AffectedOwnerSelection {
            affectedOwners = affectedOwners == null ? List.of() : List.copyOf(affectedOwners);
        }
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid(field + " 必须大于 0");
        }
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw invalid(field + " 不得小于 0");
        }
    }

    private void requireRatio(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw invalid(field + " 必须大于 0 且不超过 1");
        }
    }

    private boolean isBlank(String value) {
        return trim(value) == null;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RepairWorkOrderApplicationException invalid(String message) {
        return new RepairWorkOrderApplicationException(PARAM_INVALID, message);
    }

    private RepairWorkOrderApplicationException notFound(String message) {
        return new RepairWorkOrderApplicationException(NOT_FOUND, message);
    }

    private record ItemLocation(Long buildingId, String unitName) {
    }
}
