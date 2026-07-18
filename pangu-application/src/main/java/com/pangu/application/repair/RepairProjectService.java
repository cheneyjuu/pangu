// 关联业务：编排单一决定范围维修工程的筹备草稿、维修点位、可信资金切片校验、附件引用及方案冻结。
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
import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import com.pangu.domain.model.repair.RepairProject.DecisionScope;
import com.pangu.domain.model.repair.RepairProject.DecisionScopeVerificationStatus;
import com.pangu.domain.model.repair.RepairProject.FundingSlice;
import com.pangu.domain.model.repair.RepairProject.FundingSliceVerificationStatus;
import com.pangu.domain.model.repair.RepairProject.PlanAttachment;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.ScopeType;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProject.WorkPoint;
import com.pangu.domain.model.repair.RepairProject.WorkPointCauseStatus;
import com.pangu.domain.model.repair.RepairProject.WorkPointLocationType;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderEvent;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.policy.RepairCaseLifecyclePolicy;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
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

    private final RepairProjectRepository projectRepository;
    private final RepairWorkOrderRepository workOrderRepository;
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
        validateScopeShape(command.scopeType(), command.buildingId(), unitName);
        validateBuilding(actor.tenantId(), command.scopeType(), command.buildingId());
        RepairWorkflowType technicalWorkflow = command.scopeType() == ScopeType.COMMUNITY
                ? RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR
                : RepairWorkflowType.BUILDING_REPAIR;
        RepairProject project = projectRepository.insertProject(new RepairProject(
                null, null, actor.tenantId(), projectName, technicalWorkflow, command.scopeType(),
                command.buildingId(), unitName, null, null,
                Status.DRAFT, null, 0, actor.accountId(), actor.userId(), null, null));
        DecisionScope decisionScope = projectRepository.insertDecisionScope(
                resolveDecisionScope(project, command.plan()));
        PlanVersion plan = insertPlan(project, decisionScope, command.plan(), 1, actor);
        event(project, actor, "PROJECT_CREATED", Map.of(
                "workflowType", project.workflowType().name(),
                "workflowTypeSource", "SCOPE_TECHNICAL_PATH_ONLY",
                "decisionScopeId", decisionScope.decisionScopeId(),
                "decisionScopeVerificationStatus", decisionScope.verificationStatus().name(),
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
        if (project.status() != Status.DRAFT) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "当前项目状态不能修改实施方案 status=" + project.status());
        }
        List<PlanVersion> plans = projectRepository.listPlans(projectId, actor.tenantId());
        if (plans.stream().anyMatch(plan -> plan.status() == PlanStatus.DRAFT)) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "当前项目已有未锁定的实施方案草稿");
        }
        int nextVersion = plans.stream().mapToInt(PlanVersion::versionNo).max().orElse(0) + 1;
        DecisionScope decisionScope = projectRepository.findDecisionScope(projectId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "项目缺少唯一决定范围快照，不能创建新方案"));
        if (decisionScope.legacyReadOnly()) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "历史只读项目不能写入新维修点位方案");
        }
        PlanVersion plan = insertPlan(project, decisionScope, command.plan(), nextVersion, actor);
        event(project, actor, "PLAN_VERSION_CREATED", Map.of(
                "planId", plan.planId(), "planVersion", plan.versionNo()));
        return details(projectId, actor.tenantId());
    }

    /**
     * 待核验范围只能在项目仍是草稿时按已关联的原始来源重新核验；锁定后不得回写事实。
     */
    @Transactional
    public RepairProject.Details reverifyDecisionScope(Long projectId, Integer expectedProjectVersion) {
        UserContext actor = requireActor();
        if (expectedProjectVersion == null) {
            throw invalid("expectedProjectVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        if (!Objects.equals(project.version(), expectedProjectVersion)) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "项目版本已变化，请刷新后再重新核验决定范围");
        }
        if (project.status() != Status.DRAFT) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "只有草稿项目可以重新核验决定范围");
        }
        DecisionScope current = projectRepository.findDecisionScope(projectId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "项目缺少唯一决定范围快照"));
        if (current.legacyReadOnly()) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "历史只读项目不能重新核验决定范围");
        }
        PlanVersion draftPlan = projectRepository.listPlans(projectId, actor.tenantId()).stream()
                .filter(plan -> plan.status() == PlanStatus.DRAFT)
                .findFirst()
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "项目没有可重新核验的草稿实施方案"));
        DecisionScope reverified = resolveDecisionScope(
                project,
                projectRepository.listWorkPoints(draftPlan.planId(), actor.tenantId()).stream()
                        .flatMap(workPoint -> workPoint.linkedWorkOrderIds().stream())
                        .toList());
        if (projectRepository.updateDecisionScopeVerification(
                projectId, actor.tenantId(), reverified.verificationStatus(), reverified.verificationBasis()) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "决定范围重新核验失败，请刷新后重试");
        }
        event(project, actor, "DECISION_SCOPE_REVERIFIED", Map.of(
                "decisionScopeId", current.decisionScopeId(),
                "previousVerificationStatus", current.verificationStatus().name(),
                "verificationStatus", reverified.verificationStatus().name(),
                "verificationBasis", reverified.verificationBasis()));
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
        if (project.status() != Status.DRAFT) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "当前项目状态不能锁定实施方案 status=" + project.status());
        }
        PlanVersion plan = projectRepository.findPlanForUpdate(planId, projectId, actor.tenantId())
                .orElseThrow(() -> notFound("实施方案不存在"));
        if (plan.status() != PlanStatus.DRAFT) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "实施方案已经锁定或被替代");
        }
        DecisionScope decisionScope = projectRepository.findDecisionScope(projectId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "项目缺少唯一决定范围快照，不能冻结实施方案"));
        if (decisionScope.verificationStatus() != DecisionScopeVerificationStatus.CONFIRMED) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "决定范围尚待核验，不能锁定实施方案");
        }
        List<WorkPoint> workPoints = projectRepository.listWorkPoints(planId, actor.tenantId());
        if (workPoints.isEmpty()) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "维修点位为空，禁止锁定实施方案");
        }
        List<FundingSlice> fundingSlices = projectRepository.listFundingSlices(
                decisionScope.decisionScopeId(), actor.tenantId());
        validateTrustedFundingSlices(fundingSlices);
        assertTrustedGovernanceAndExecutionSnapshots();
        String snapshotHash = snapshotHash(
                project, decisionScope, plan, workPoints, fundingSlices);
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
                "decisionScopeId", decisionScope.decisionScopeId(),
                "decisionScopeVerificationStatus", decisionScope.verificationStatus().name()));
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

    private PlanVersion insertPlan(
            RepairProject project,
            DecisionScope decisionScope,
            RepairPlanDraftCommand draft,
            int versionNo,
            UserContext actor) {
        if (draft == null) {
            throw invalid("plan 必填");
        }
        // 草稿只保存预算、点位与来源；资金、治理、验收、付款和定商必须由后续可信快照提供。
        PlanNarratives narratives = sanitizeNarratives(draft);
        validateDraft(project, draft);
        narrativeImageService.assertDraftImagesUsable(narratives.imageIds(), actor);
        PlanVersion plan = projectRepository.insertPlan(new PlanVersion(
                null, project.projectId(), project.tenantId(), versionNo,
                narratives.planDescription(),
                draft.budgetTotal(), null, null, null, null, null,
                null, List.of(), null, null, List.of(), null, null,
                null, null, null, null, null, null, null, false, List.of(),
                PlanStatus.DRAFT, null, actor.accountId(), actor.userId(), null, null, null));
        narrativeImageService.bindDraftImages(
                narratives.imageIds(), actor, project.projectId(), plan.planId());
        insertWorkPoints(project, decisionScope, plan, draft.workPoints(), actor);
        linkInitialAttachments(project, plan, draft.attachments());
        return plan;
    }

    /** 维修点位与报价行分离；来源工单在写入前必须先核对到项目唯一决定范围。 */
    private void insertWorkPoints(
            RepairProject project,
            DecisionScope decisionScope,
            PlanVersion plan,
            List<RepairPlanDraftCommand.WorkPointDraft> drafts,
            UserContext actor) {
        int sortOrder = 0;
        Map<Long, RepairWorkOrder> linkedWorkOrders = new LinkedHashMap<>();
        for (RepairPlanDraftCommand.WorkPointDraft draft : drafts) {
            sortOrder++;
            WorkPointLocation location = validateWorkPointLocation(project, draft);
            for (Long workOrderId : new LinkedHashSet<>(draft.linkedWorkOrderIds())) {
                if (workOrderId == null) {
                    throw invalid("workPoints.linkedWorkOrderIds 不能包含空值");
                }
                linkedWorkOrders.computeIfAbsent(
                        workOrderId, ignored -> validateLinkedWorkOrder(project, decisionScope, workOrderId));
            }
            WorkPoint workPoint = projectRepository.insertWorkPoint(new WorkPoint(
                    null, project.projectId(), plan.planId(), project.tenantId(),
                    requireText(draft.businessName(), "workPoints.businessName"),
                    location.buildingId(), location.unitName(), location.locationType(),
                    location.referenceRoomId(), location.commonAreaName(), trim(draft.spaceName()),
                    trim(draft.orientation()), trim(draft.component()), trim(draft.specificPart()),
                    requireText(draft.symptom(), "workPoints.symptom"), draft.causeStatus(), trim(draft.causeBasis()),
                    requireText(draft.proposedMeasure(), "workPoints.proposedMeasure"),
                    trim(draft.technicalRequirements()), draft.quantity(), trim(draft.unit()),
                    draft.preliminaryEstimatedAmount(), trim(draft.estimateSource()), sortOrder,
                    false, List.of(), null));
            for (Long workOrderId : new LinkedHashSet<>(draft.linkedWorkOrderIds())) {
                projectRepository.linkWorkPointToWorkOrder(
                        workPoint.workPointId(), workOrderId, project.tenantId());
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
        if (draft.workPoints().isEmpty()) {
            throw invalid("实施方案至少包含一个维修点位");
        }
        validateWorkPoints(draft.workPoints());
    }

    /**
     * 点位可选地记录范围量和初步估算；这两项不能替代报价行，更不能要求与送审预算相加相等。
     */
    private void validateWorkPoints(List<RepairPlanDraftCommand.WorkPointDraft> workPoints) {
        for (RepairPlanDraftCommand.WorkPointDraft workPoint : workPoints) {
            if (workPoint == null) {
                throw invalid("workPoints 不能包含空值");
            }
            requireText(workPoint.businessName(), "workPoints.businessName");
            requireText(workPoint.symptom(), "workPoints.symptom");
            requireText(workPoint.proposedMeasure(), "workPoints.proposedMeasure");
            if (workPoint.locationType() == null) {
                throw invalid("workPoints.locationType 必填");
            }
            if (workPoint.causeStatus() == null) {
                throw invalid("workPoints.causeStatus 必填");
            }
            if (workPoint.causeStatus() == WorkPointCauseStatus.CONFIRMED
                    && trim(workPoint.causeBasis()) == null) {
                throw invalid("已确认原因必须填写勘验或鉴定依据");
            }
            if ((workPoint.quantity() == null) != (trim(workPoint.unit()) == null)) {
                throw invalid("维修点位数量和单位必须同时填写或同时留空");
            }
            if (workPoint.quantity() != null) {
                requirePositive(workPoint.quantity(), "workPoints.quantity");
            }
            if ((workPoint.preliminaryEstimatedAmount() == null) != (trim(workPoint.estimateSource()) == null)) {
                throw invalid("点位初步估算金额和估算来源必须同时填写或同时留空");
            }
            if (workPoint.preliminaryEstimatedAmount() != null) {
                requireNonNegative(workPoint.preliminaryEstimatedAmount(), "workPoints.preliminaryEstimatedAmount");
            }
        }
    }

    private WorkPointLocation validateWorkPointLocation(
            RepairProject project, RepairPlanDraftCommand.WorkPointDraft draft) {
        Long buildingId = draft.buildingId();
        String unitName = trim(draft.unitName());
        if (project.scopeType() == ScopeType.BUILDING || project.scopeType() == ScopeType.BUILDING_UNIT) {
            buildingId = buildingId == null ? project.buildingId() : buildingId;
            if (!project.buildingId().equals(buildingId)) {
                throw invalid("楼栋维修点位不能使用其他楼栋 buildingId=" + buildingId);
            }
            if (project.scopeType() == ScopeType.BUILDING_UNIT) {
                unitName = unitName == null ? project.unitName() : unitName;
                if (!project.unitName().equals(unitName)) {
                    throw invalid("单元维修点位不能使用其他单元 unitName=" + unitName);
                }
            }
        } else if (buildingId != null && !workOrderRepository.buildingExists(project.tenantId(), buildingId)) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.BUILDING_NOT_IN_SCOPE,
                    "维修点位楼栋不在当前小区 buildingId=" + buildingId);
        }
        if (draft.locationType() == WorkPointLocationType.REFERENCE_ROOM) {
            if (draft.referenceRoomId() == null || trim(draft.commonAreaName()) != null) {
                throw invalid("参照房屋点位必须只填写 referenceRoomId");
            }
            if (buildingId == null || !workOrderRepository.roomExists(
                    project.tenantId(), buildingId, draft.referenceRoomId())) {
                throw invalid("参照房屋不在所选楼栋 roomId=" + draft.referenceRoomId());
            }
            return new WorkPointLocation(buildingId, unitName, draft.locationType(), draft.referenceRoomId(), null);
        }
        if (draft.locationType() == WorkPointLocationType.COMMON_AREA) {
            if (draft.referenceRoomId() != null || trim(draft.commonAreaName()) == null) {
                throw invalid("公区点位必须只填写 commonAreaName，不能伪造房屋编号");
            }
            return new WorkPointLocation(
                    buildingId, unitName, draft.locationType(), null, requireText(
                            draft.commonAreaName(), "workPoints.commonAreaName"));
        }
        throw invalid("不支持的维修点位位置类型");
    }

    private RepairWorkOrder validateLinkedWorkOrder(
            RepairProject project, DecisionScope decisionScope, Long workOrderId) {
        RepairWorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> notFound("关联报修事项不存在 workOrderId=" + workOrderId));
        if (!project.tenantId().equals(workOrder.tenantId())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "禁止跨小区关联报修事项");
        }
        assertSourceScopeMatches(project, workOrder, workOrderId);
        RepairCaseLifecyclePolicy.Decision decision = caseLifecyclePolicy.assessProjectLink(
                workOrder, project.workflowType(), project.buildingId());
        if (!decision.allowed()) {
            throw invalid(decision.reason() + " workOrderId=" + workOrderId);
        }
        if (decisionScope.verificationStatus() == DecisionScopeVerificationStatus.CONFIRMED
                && !sourceScopeConfirmed(project, workOrder)) {
            throw invalid("已确认决定范围不能关联尚待核验的来源报修事项 workOrderId=" + workOrderId);
        }
        return workOrder;
    }

    /**
     * 选定的项目范围不是前端勾选结果。只有已锁定位置、公共范围和楼栋均能对应时才可确认；
     * 任何明确不一致都会拒绝，缺失事实只允许保留草稿。
     */
    private DecisionScope resolveDecisionScope(RepairProject project, RepairPlanDraftCommand draft) {
        return resolveDecisionScope(project, draft.workPoints().stream()
                .filter(Objects::nonNull)
                .flatMap(workPoint -> workPoint.linkedWorkOrderIds().stream())
                .toList());
    }

    private DecisionScope resolveDecisionScope(RepairProject project, Iterable<Long> workOrderIds) {
        Map<Long, RepairWorkOrder> sources = new LinkedHashMap<>();
        for (Long workOrderId : new LinkedHashSet<>(toSourceIds(workOrderIds))) {
            if (workOrderId == null) {
                throw invalid("workPoints.linkedWorkOrderIds 不能包含空值");
            }
            RepairWorkOrder source = workOrderRepository.findById(workOrderId)
                    .orElseThrow(() -> notFound("关联报修事项不存在 workOrderId=" + workOrderId));
            if (!project.tenantId().equals(source.tenantId())) {
                throw new RepairWorkOrderApplicationException(FORBIDDEN, "禁止跨小区关联报修事项");
            }
            assertSourceScopeMatches(project, source, workOrderId);
            sources.putIfAbsent(workOrderId, source);
        }
        boolean confirmed = !sources.isEmpty() && sources.values().stream()
                .allMatch(source -> sourceScopeConfirmed(project, source));
        String basis = confirmed
                ? "已核验 " + sources.size() + " 条来源报修事项的公共范围和楼栋归属。"
                : sources.isEmpty()
                ? "未关联可核验来源，决定范围待人工核验。"
                : "部分来源报修事项的公共范围、楼栋或勘验状态尚未核验。";
        return new DecisionScope(
                null, project.projectId(), project.tenantId(), project.scopeType(), project.buildingId(),
                project.unitName(), confirmed ? DecisionScopeVerificationStatus.CONFIRMED
                : DecisionScopeVerificationStatus.PENDING_VERIFICATION, basis, false, null);
    }

    private List<Long> toSourceIds(Iterable<Long> workOrderIds) {
        List<Long> ids = new java.util.ArrayList<>();
        workOrderIds.forEach(ids::add);
        return ids;
    }

    private void assertSourceScopeMatches(RepairProject project, RepairWorkOrder source, Long workOrderId) {
        if (source.publicAreaScope() == null) {
            return;
        }
        boolean expectedCommunity = project.scopeType() == ScopeType.COMMUNITY;
        boolean sourceCommunity = source.publicAreaScope().name().equals("COMMUNITY");
        if (expectedCommunity != sourceCommunity) {
            throw invalid("来源报修事项不属于当前决定范围 workOrderId=" + workOrderId);
        }
        if (!expectedCommunity && source.buildingId() != null
                && !project.buildingId().equals(source.buildingId())) {
            throw invalid("来源报修事项不属于当前决定范围 workOrderId=" + workOrderId);
        }
    }

    private boolean sourceScopeConfirmed(RepairProject project, RepairWorkOrder source) {
        if (!source.locationLocked() || source.publicAreaScope() == null
                || source.status() != RepairWorkOrderStatus.SURVEY_COMPLETED
                && source.status() != RepairWorkOrderStatus.PROJECT_LINKED) {
            return false;
        }
        if (project.scopeType() == ScopeType.COMMUNITY) {
            return source.publicAreaScope().name().equals("COMMUNITY");
        }
        // 工单尚未保存单元字段；单元部分共用不能被当前工单事实自动确认。
        return project.scopeType() == ScopeType.BUILDING
                && source.publicAreaScope().name().equals("BUILDING")
                && project.buildingId().equals(source.buildingId());
    }

    /** 项目与维修点位创建在同一事务内，只有全部结构化方案校验通过后才交接报修事项状态。 */
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

    private void validateScopeShape(ScopeType scopeType, Long buildingId, String unitName) {
        if (scopeType == null) {
            throw invalid("scopeType 必填");
        }
        if (scopeType == ScopeType.COMMUNITY) {
            if (buildingId != null || unitName != null) {
                throw invalid("全体共用范围不能绑定单一楼栋或单元");
            }
            return;
        }
        if (buildingId == null) {
            throw invalid("楼栋或单元共有范围必须选择楼栋");
        }
        if (scopeType == ScopeType.BUILDING_UNIT && unitName == null) {
            throw invalid("单元共有范围必须填写 unitName");
        }
    }

    /**
     * 资金承担范围只能由可信的账簿、责任认定或有效决定适配器写入；附件、范围和前端文本都不能替代该快照。
     */
    private void validateTrustedFundingSlices(List<FundingSlice> fundingSlices) {
        if (fundingSlices.isEmpty() || fundingSlices.stream().anyMatch(slice ->
                slice.verificationStatus() != FundingSliceVerificationStatus.CONFIRMED
                        || slice.sourceType() == null
                        || trim(slice.sourceRecordType()) == null
                        || trim(slice.sourceRecordId()) == null
                        || trim(slice.ledgerReference()) == null
                        || trim(slice.allocationSnapshotHash()) == null
                        || slice.approvedAmount() == null
                        || slice.approvedAmount().signum() < 0
                        || slice.verifiedAt() == null)) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS,
                    "当前版本尚未接入可信的资金切片、承担范围和账簿快照，不能锁定实施方案");
        }
    }

    /**
     * 当前系统尚未接入决定/授权、验收和实施证据的可信快照来源，因此不能把表单文本或现场照片伪装成冻结依据。
     */
    private void assertTrustedGovernanceAndExecutionSnapshots() {
        throw new RepairWorkOrderApplicationException(
                INVALID_STATUS,
                "当前版本尚未接入可信的决定或授权、验收和实施证据快照，不能锁定实施方案");
    }

    private String snapshotHash(
            RepairProject project,
            DecisionScope decisionScope,
            PlanVersion plan,
            List<WorkPoint> workPoints,
            List<FundingSlice> fundingSlices) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectId", project.projectId());
        snapshot.put("workflowType", project.workflowType());
        snapshot.put("scopeType", project.scopeType());
        snapshot.put("buildingId", project.buildingId());
        snapshot.put("unitName", project.unitName());
        snapshot.put("decisionScope", decisionScope);
        snapshot.put("plan", immutablePlanSnapshot(plan));
        snapshot.put("workPoints", workPoints.stream().sorted(Comparator.comparing(WorkPoint::sortOrder)).toList());
        snapshot.put("fundingSlices", fundingSlices.stream()
                .sorted(Comparator.comparing(FundingSlice::fundingSliceId)).toList());
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
                projectRepository.findDecisionScope(projectId, tenantId).orElse(null),
                plans,
                currentPlanId == null ? List.of() : projectRepository.listWorkPoints(currentPlanId, tenantId),
                projectRepository.findDecisionScope(projectId, tenantId)
                        .map(scope -> projectRepository.listFundingSlices(scope.decisionScopeId(), tenantId))
                        .orElseGet(List::of),
                currentPlanId == null ? List.of() : projectRepository.listPlanAffectedOwners(currentPlanId, tenantId),
                projectRepository.listAttachments(projectId, tenantId),
                currentPlanId == null ? List.of() : projectRepository.listPlanAttachments(currentPlanId, tenantId));
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
        return new PlanNarratives(planDescription.html(), planDescription.narrativeImageIds());
    }

    private SanitizedRichText requireRichText(String value, String field) {
        SanitizedRichText sanitized = richTextSanitizer.sanitize(value);
        if (sanitized.isBlank()) {
            throw invalid(field + " 必填");
        }
        return sanitized;
    }

    private record PlanNarratives(
            String planDescription,
            java.util.Set<Long> imageIds) {
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

    private record WorkPointLocation(
            Long buildingId,
            String unitName,
            WorkPointLocationType locationType,
            Long referenceRoomId,
            String commonAreaName) {
    }
}
