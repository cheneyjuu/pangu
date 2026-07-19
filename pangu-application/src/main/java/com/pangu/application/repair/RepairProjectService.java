// 关联业务：编排维修工程草案、责任与资金初判、相关业主决定提案冻结、授权后实施方案锁定及可信资金快照。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.repair.command.CreateRepairPlanVersionCommand;
import com.pangu.application.repair.command.CreateRepairProjectCommand;
import com.pangu.application.repair.command.ConfirmRepairResponsibilityDeterminationCommand;
import com.pangu.application.repair.command.ProposeRepairResponsibilityDeterminationCommand;
import com.pangu.application.repair.command.RepairPlanDraftCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.gateway.RichTextSanitizer;
import com.pangu.domain.gateway.RichTextSanitizer.SanitizedRichText;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import com.pangu.domain.model.repair.RepairProject.AllocationBasis;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
import com.pangu.domain.model.repair.RepairProject.DecisionScope;
import com.pangu.domain.model.repair.RepairProject.DecisionScopeVerificationStatus;
import com.pangu.domain.model.repair.RepairProject.ExecutionAuthorityType;
import com.pangu.domain.model.repair.RepairProject.FundingSlice;
import com.pangu.domain.model.repair.RepairProject.FundingSourceType;
import com.pangu.domain.model.repair.RepairProject.FundingSliceVerificationStatus;
import com.pangu.domain.model.repair.RepairProject.PlanAttachment;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.ResponsibilityDetermination;
import com.pangu.domain.model.repair.RepairProject.ResponsibilityDeterminationStatus;
import com.pangu.domain.model.repair.RepairProject.ResponsibilityPath;
import com.pangu.domain.model.repair.RepairProject.ScopeType;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProject.WorkPoint;
import com.pangu.domain.model.repair.RepairProject.WorkPointCauseStatus;
import com.pangu.domain.model.repair.RepairProject.WorkPointLocationType;
import com.pangu.domain.model.repair.RepairProjectGovernance.GovernanceBasis;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderEvent;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.policy.RepairCaseLifecyclePolicy;
import com.pangu.domain.repository.MaintenanceFundAccountRepository;
import com.pangu.domain.repository.MaintenanceFundAccountRepository.Account;
import com.pangu.domain.repository.MaintenanceFundAccountRepository.AccountScope;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
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

    /** 责任与资金初判由物业基于勘验、合同或保修依据提出，不能由治理确认方代填。 */
    private static final Set<String> PROPERTY_RESPONSIBILITY_PROPOSER_ROLES = Set.of(
            "PROPERTY_MANAGER", "PROPERTY_STAFF");

    private final RepairProjectRepository projectRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
    private final MaintenanceFundAccountRepository maintenanceFundAccountRepository;
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

    /**
     * 物业提出的是待确认的责任与资金初判，而不是资金占用、决定结果或付款指令。
     * 执行状态只能由服务端按责任路径派生，不能由请求字段替代。
     */
    @Transactional
    public RepairProject.Details proposeResponsibilityDetermination(
            Long projectId, ProposeRepairResponsibilityDeterminationCommand command) {
        UserContext actor = requirePropertyResponsibilityProposer();
        if (command == null || command.expectedProjectVersion() == null) {
            throw invalid("expectedProjectVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireDraftProjectVersion(project, command.expectedProjectVersion(), "提交工程责任初判");
        DecisionScope decisionScope = projectRepository.findDecisionScope(projectId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "项目缺少唯一决定范围快照"));
        PlanVersion draftPlan = currentDraftPlan(projectId, actor.tenantId());
        projectRepository.findAttachment(command.basisAttachmentId(), projectId, actor.tenantId())
                .orElseThrow(() -> notFound("工程责任初判依据附件不存在"));
        ResponsibilityDetermination determination = new ResponsibilityDetermination(
                null, projectId, actor.tenantId(), nextResponsibilityDeterminationVersion(projectId, actor.tenantId()),
                ResponsibilityDeterminationStatus.PENDING_CONFIRMATION,
                command.responsibilityPath(), command.fundingSourceType(),
                deriveExecutionAuthority(command.responsibilityPath()),
                command.basisAttachmentId(), requireText(command.basisReference(), "basisReference"),
                trim(command.responsiblePartyName()), trim(command.responsiblePartyReference()),
                requirePositiveAmount(command.approvedAmount(), "approvedAmount"), actor.accountId(), actor.userId(),
                LocalDateTime.now(), null, null, null, null, null);
        validateResponsibilityDetermination(determination, decisionScope, draftPlan);
        if (!projectRepository.listFundingSlices(decisionScope.decisionScopeId(), actor.tenantId()).isEmpty()) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "项目已有可信资金切片，不能直接替换工程责任初判");
        }
        projectRepository.supersedeCurrentResponsibilityDeterminations(projectId, actor.tenantId());
        ResponsibilityDetermination proposed = projectRepository.insertResponsibilityDetermination(determination);
        if (projectRepository.advanceVersion(projectId, actor.tenantId(), command.expectedProjectVersion()) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "项目版本已变化，请刷新后再提交工程责任初判");
        }
        event(project, actor, "RESPONSIBILITY_DETERMINATION_PROPOSED", Map.of(
                "determinationId", proposed.determinationId(),
                "determinationVersion", proposed.versionNo(),
                "responsibilityPath", proposed.responsibilityPath().name(),
                "fundingSourceType", proposed.fundingSourceType().name(),
                "executionAuthorityType", proposed.executionAuthorityType().name(),
                "approvedAmount", proposed.approvedAmount(),
                "basisAttachmentId", proposed.basisAttachmentId()));
        return details(projectId, actor.tenantId());
    }

    /**
     * 确认人对本工程的证据、责任路径和资金承担初判负责；服务端不把附件上传或物业提交视为确认。
     */
    @Transactional
    public RepairProject.Details confirmResponsibilityDetermination(
            Long projectId,
            Long determinationId,
            ConfirmRepairResponsibilityDeterminationCommand command) {
        UserContext actor = requireGovernanceActor();
        if (command == null || command.expectedProjectVersion() == null) {
            throw invalid("expectedProjectVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireDraftProjectVersion(project, command.expectedProjectVersion(), "确认工程责任初判");
        ResponsibilityDetermination current = projectRepository
                .findCurrentResponsibilityDetermination(projectId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "项目没有待确认的工程责任初判"));
        if (!Objects.equals(current.determinationId(), determinationId)
                || current.status() != ResponsibilityDeterminationStatus.PENDING_CONFIRMATION) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "工程责任初判已变化，请刷新后再确认");
        }
        DecisionScope decisionScope = projectRepository.findDecisionScope(projectId, actor.tenantId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "项目缺少唯一决定范围快照"));
        PlanVersion draftPlan = currentDraftPlan(projectId, actor.tenantId());
        projectRepository.findAttachment(current.basisAttachmentId(), projectId, actor.tenantId())
                .orElseThrow(() -> notFound("工程责任初判依据附件不存在"));
        validateResponsibilityDetermination(current, decisionScope, draftPlan);
        if (current.responsibilityPath() == ResponsibilityPath.SHARED_COMMON_REPAIR
                && decisionScope.verificationStatus() != DecisionScopeVerificationStatus.CONFIRMED) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "共有与决定范围尚待核验，不能确认共有维修责任初判");
        }
        if (projectRepository.confirmResponsibilityDetermination(
                determinationId, projectId, actor.tenantId(), actor.accountId(), actor.userId(),
                trim(command.confirmationNote())) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "工程责任初判已变化，请刷新后再确认");
        }
        if (projectRepository.advanceVersion(projectId, actor.tenantId(), command.expectedProjectVersion()) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "项目版本已变化，请刷新后再确认工程责任初判");
        }
        event(project, actor, "RESPONSIBILITY_DETERMINATION_CONFIRMED", Map.of(
                "determinationId", current.determinationId(),
                "determinationVersion", current.versionNo(),
                "responsibilityPath", current.responsibilityPath().name(),
                "fundingSourceType", current.fundingSourceType().name(),
                "executionAuthorityType", current.executionAuthorityType().name(),
                "approvedAmount", current.approvedAmount(),
                "basisAttachmentId", current.basisAttachmentId()));
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

    /**
     * 冻结的是供相关业主决定审查的提案，不是最终实施方案。决定前必须固定预算、点位、费用承担范围，
     * 否则表决通过后仍可改写其所依据的内容；账簿余额、托管归集和支取资格只在最终锁定时核验，不能因尚未
     * 接入账户而阻止业主对已明确的责任与资金路径作出决定。
     */
    @Transactional
    public RepairProject.Details freezePlanForAuthorization(
            Long projectId, Long planId, Integer expectedProjectVersion) {
        UserContext actor = requireActor();
        if (expectedProjectVersion == null) {
            throw invalid("expectedProjectVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireDraftProjectVersion(project, expectedProjectVersion, "冻结相关业主决定提案");
        PlanVersion plan = projectRepository.findPlanForUpdate(planId, projectId, actor.tenantId())
                .orElseThrow(() -> notFound("实施方案不存在"));
        if (plan.status() != PlanStatus.DRAFT) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "当前实施方案不能冻结为相关业主决定提案");
        }
        DecisionScope decisionScope = requireConfirmedDecisionScope(projectId, actor.tenantId(), "冻结相关业主决定提案");
        ResponsibilityDetermination determination = requireConfirmedResponsibilityDetermination(
                projectId, actor.tenantId(), "冻结相关业主决定提案");
        if (determination.responsibilityPath() != ResponsibilityPath.SHARED_COMMON_REPAIR
                || determination.executionAuthorityType() != ExecutionAuthorityType.OWNER_DECISION) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "只有已确认需相关业主决定的共有维修可以冻结相关业主决定提案");
        }
        requireApprovedAmount(determination, plan, "冻结相关业主决定提案");
        List<WorkPoint> workPoints = requireWorkPoints(plan.planId(), actor.tenantId(), "冻结相关业主决定提案");
        List<AllocationRoom> allocationRooms = projectRepository.snapshotAllocationRooms(
                plan.planId(), actor.tenantId(), decisionScope.scopeType(),
                decisionScope.buildingId(), decisionScope.unitName());
        AllocationBasis allocationBasis = requireAllocationSnapshot(plan, actor.tenantId(), allocationRooms);
        String allocationSnapshotHash = allocationSnapshotHash(
                decisionScope, determination, plan, allocationRooms, allocationBasis);
        String authorizationSnapshotHash = authorizationProposalSnapshotHash(
                project, decisionScope, determination, plan, workPoints, allocationRooms, allocationBasis,
                allocationSnapshotHash);
        if (projectRepository.freezePlanForAuthorization(
                planId, projectId, actor.tenantId(), authorizationSnapshotHash, actor.userId()) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "相关业主决定提案冻结失败，请刷新后重试");
        }
        if (projectRepository.activateAuthorizationProposal(
                projectId, actor.tenantId(), planId, expectedProjectVersion) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "项目状态已变化，请刷新后重试");
        }
        event(project, actor, "AUTHORIZATION_PROPOSAL_FROZEN", Map.of(
                "planId", planId,
                "planVersion", plan.versionNo(),
                "authorizationSnapshotHash", authorizationSnapshotHash,
                "allocationSnapshotHash", allocationSnapshotHash,
                "decisionScopeId", decisionScope.decisionScopeId(),
                "responsibilityDeterminationId", determination.determinationId(),
                "allocationRoomCount", allocationRooms.size()));
        return details(projectId, actor.tenantId());
    }

    /**
     * 最终锁定只在直接责任履行已确认，或共有维修的相关业主决定已生效后进行。
     * 共有维修才可进入业主侧定商、合同和资金支取，直接责任路径不得借此生成业主侧合同或付款资格。
     */
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
        PlanVersion plan = projectRepository.findPlanForUpdate(planId, projectId, actor.tenantId())
                .orElseThrow(() -> notFound("实施方案不存在"));
        DecisionScope decisionScope = requireConfirmedDecisionScope(projectId, actor.tenantId(), "锁定实施方案");
        ResponsibilityDetermination determination = requireConfirmedResponsibilityDetermination(
                projectId, actor.tenantId(), "锁定实施方案");
        requireApprovedAmount(determination, plan, "锁定实施方案");
        List<WorkPoint> workPoints = requireWorkPoints(plan.planId(), actor.tenantId(), "锁定实施方案");

        boolean ownerDecisionRoute = determination.responsibilityPath() == ResponsibilityPath.SHARED_COMMON_REPAIR;
        if (ownerDecisionRoute && determination.executionAuthorityType() != ExecutionAuthorityType.OWNER_DECISION) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "共有维修执行状态异常，请重新确认责任初判后取得相关业主决定");
        }
        GovernanceBasis governanceBasis = null;
        List<AllocationRoom> allocationRooms;
        if (ownerDecisionRoute) {
            if (project.status() != Status.AUTHORIZED || plan.status() != PlanStatus.AUTHORIZATION_FROZEN) {
                throw new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "需先冻结相关业主决定提案并完成有效相关业主决定，不能锁定实施方案");
            }
            governanceBasis = governanceRepository.findActiveGovernanceBasis(
                            projectId, planId, actor.tenantId())
                    .orElseThrow(() -> new RepairWorkOrderApplicationException(
                            INVALID_STATUS, "项目缺少与相关业主决定提案对应的有效相关业主决定快照，不能锁定实施方案"));
            allocationRooms = projectRepository.listAllocationRooms(plan.planId(), actor.tenantId());
            if (allocationRooms.isEmpty()) {
                throw new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "相关业主决定提案缺少费用承担房屋快照，不能锁定实施方案");
            }
        } else {
            if (project.status() != Status.DRAFT || plan.status() != PlanStatus.DRAFT) {
                throw new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "当前项目状态不能按直接责任履行路径锁定实施方案 status=" + project.status());
            }
            allocationRooms = determination.responsibilityPath() == ResponsibilityPath.SHARED_COMMON_REPAIR
                    ? projectRepository.snapshotAllocationRooms(
                            plan.planId(), actor.tenantId(), decisionScope.scopeType(),
                            decisionScope.buildingId(), decisionScope.unitName())
                    : List.of();
        }
        AllocationBasis allocationBasis = determination.responsibilityPath()
                == ResponsibilityPath.SHARED_COMMON_REPAIR
                ? requireAllocationSnapshot(plan, actor.tenantId(), allocationRooms)
                : directResponsibilityAllocationBasis(determination);
        String allocationSnapshotHash = allocationSnapshotHash(
                decisionScope, determination, plan, allocationRooms, allocationBasis);
        if (ownerDecisionRoute && !Objects.equals(plan.authorizationSnapshotHash(), authorizationProposalSnapshotHash(
                project, decisionScope, determination, plan, workPoints, allocationRooms, allocationBasis,
                allocationSnapshotHash))) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "相关业主决定提案快照与当前不可变事实不一致，不能锁定实施方案");
        }
        List<FundingSlice> fundingSlices = resolveFundingSlices(
                project, decisionScope, determination, plan, allocationSnapshotHash, actor.tenantId());
        validateTrustedFundingSlices(
                fundingSlices, determination, allocationSnapshotHash, plan.budgetTotal());
        String snapshotHash = snapshotHash(
                project, decisionScope, determination, governanceBasis, plan, workPoints,
                allocationRooms, allocationBasis, fundingSlices);
        if (projectRepository.lockPlan(planId, projectId, actor.tenantId(), snapshotHash, actor.userId()) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "实施方案锁定失败，请刷新后重试");
        }
        projectRepository.supersedeLockedPlans(projectId, actor.tenantId(), planId);
        Status expectedStatus = ownerDecisionRoute ? Status.AUTHORIZED : Status.DRAFT;
        if (projectRepository.activateExecutionPlan(
                projectId, actor.tenantId(), planId, expectedStatus, Status.AUTHORIZED,
                expectedProjectVersion) != 1) {
            throw new RepairWorkOrderApplicationException(INVALID_STATUS, "项目状态已变化，请刷新后重试");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", planId);
        payload.put("planVersion", plan.versionNo());
        payload.put("snapshotHash", snapshotHash);
        payload.put("decisionScopeId", decisionScope.decisionScopeId());
        payload.put("responsibilityDeterminationId", determination.determinationId());
        payload.put("responsibilityPath", determination.responsibilityPath().name());
        payload.put("fundingSourceType", determination.fundingSourceType().name());
        payload.put("executionAuthorityType", determination.executionAuthorityType().name());
        payload.put("decisionScopeVerificationStatus", decisionScope.verificationStatus().name());
        payload.put("allocationRoomCount", allocationRooms.size());
        payload.put("fundingSliceCount", fundingSlices.size());
        if (governanceBasis != null) {
            payload.put("governanceBasisId", governanceBasis.basisId());
            payload.put("governanceBasisHash", governanceBasis.snapshotHash());
        }
        event(project, actor, "PLAN_LOCKED", payload);
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
                PlanStatus.DRAFT, null, null, null, null,
                actor.accountId(), actor.userId(), null, null, null));
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
                throw invalid("关联房屋位置必须填写关联房屋，且不能同时填写公共部位名称");
            }
            if (buildingId == null || !workOrderRepository.roomExists(
                    project.tenantId(), buildingId, draft.referenceRoomId())) {
                throw invalid("关联房屋不在所选楼栋 roomId=" + draft.referenceRoomId());
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

    private void requireDraftProjectVersion(
            RepairProject project, Integer expectedProjectVersion, String actionLabel) {
        if (!Objects.equals(project.version(), expectedProjectVersion)) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "项目版本已变化，请刷新后再" + actionLabel);
        }
        if (project.status() != Status.DRAFT) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "当前项目状态不能" + actionLabel + " status=" + project.status());
        }
    }

    private PlanVersion currentDraftPlan(Long projectId, Long tenantId) {
        return projectRepository.listPlans(projectId, tenantId).stream()
                .filter(plan -> plan.status() == PlanStatus.DRAFT)
                .findFirst()
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "项目没有可提交工程责任初判的草稿实施方案"));
    }

    private DecisionScope requireConfirmedDecisionScope(Long projectId, Long tenantId, String actionLabel) {
        DecisionScope decisionScope = projectRepository.findDecisionScope(projectId, tenantId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "项目缺少唯一决定范围快照，不能" + actionLabel));
        if (decisionScope.verificationStatus() != DecisionScopeVerificationStatus.CONFIRMED) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "决定范围尚待核验，不能" + actionLabel);
        }
        return decisionScope;
    }

    private ResponsibilityDetermination requireConfirmedResponsibilityDetermination(
            Long projectId, Long tenantId, String actionLabel) {
        return projectRepository.findCurrentResponsibilityDetermination(projectId, tenantId)
                .filter(candidate -> candidate.status() == ResponsibilityDeterminationStatus.CONFIRMED)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "工程责任或资金承担初判尚待确认，不能" + actionLabel));
    }

    private void requireApprovedAmount(
            ResponsibilityDetermination determination, PlanVersion plan, String actionLabel) {
        if (determination.approvedAmount().compareTo(plan.budgetTotal()) < 0) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "已确认责任承担上限低于实施方案预算，不能" + actionLabel);
        }
    }

    private List<WorkPoint> requireWorkPoints(Long planId, Long tenantId, String actionLabel) {
        List<WorkPoint> workPoints = projectRepository.listWorkPoints(planId, tenantId);
        if (workPoints.isEmpty()) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "维修点位为空，不能" + actionLabel);
        }
        return workPoints;
    }

    private int nextResponsibilityDeterminationVersion(Long projectId, Long tenantId) {
        return projectRepository.listResponsibilityDeterminations(projectId, tenantId).stream()
                .map(ResponsibilityDetermination::versionNo)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
    }

    /**
     * 责任路径与资金承担由物业提出、治理主体确认；执行状态由服务端固定派生，
     * 不得由项目范围、设备名称、角色名称或前端字段伪造。
     */
    private void validateResponsibilityDetermination(
            ResponsibilityDetermination determination,
            DecisionScope decisionScope,
            PlanVersion draftPlan) {
        if (determination.responsibilityPath() == null || determination.fundingSourceType() == null
                || determination.executionAuthorityType() == null) {
            throw invalid("责任路径、资金承担和服务端派生执行状态均为必填项");
        }
        if (determination.approvedAmount().compareTo(draftPlan.budgetTotal()) < 0) {
            throw invalid("责任承担上限不得低于当前草案预算");
        }
        switch (determination.responsibilityPath()) {
            case PROPERTY_SERVICE_CONTRACT -> {
                requireDirectResponsibility(
                        determination, FundingSourceType.PROPERTY_SERVICE_CONTRACT,
                        ExecutionAuthorityType.CONTRACTUAL_EXECUTION, "物业服务合同责任方");
            }
            case DEVELOPER_WARRANTY -> {
                requireDirectResponsibility(
                        determination, FundingSourceType.DEVELOPER_WARRANTY,
                        ExecutionAuthorityType.WARRANTY_EXECUTION, "建设单位保修责任方");
            }
            case LIABLE_PARTY -> {
                requireDirectResponsibility(
                        determination, FundingSourceType.LIABLE_PARTY,
                        ExecutionAuthorityType.LIABILITY_EXECUTION, "责任人或第三方");
            }
            case SHARED_COMMON_REPAIR -> {
                if (!Set.of(
                        FundingSourceType.SPECIAL_MAINTENANCE_LEDGER,
                        FundingSourceType.PUBLIC_REVENUE_LEDGER,
                        FundingSourceType.OWNER_SELF_FUNDING).contains(determination.fundingSourceType())) {
                    throw invalid("共有维修不能使用当前责任路径不支持的资金承担类型");
                }
                if (determination.executionAuthorityType() != ExecutionAuthorityType.OWNER_DECISION) {
                    throw invalid("共有维修只能进入取得相关业主决定的流程");
                }
                if (determination.fundingSourceType() == FundingSourceType.PUBLIC_REVENUE_LEDGER
                        && decisionScope.scopeType() != ScopeType.COMMUNITY) {
                    throw invalid("公共收益资金承担路径只支持全体共用范围，不能作为楼栋维修资金替代");
                }
            }
        }
    }

    /**
     * 共有维修的初判并不等于已有决定：它只能进入后续的相关业主决定流程。
     * 既有授权和紧急维修须由各自可信事实链路接入，不允许通过本表单声明。
     */
    private ExecutionAuthorityType deriveExecutionAuthority(ResponsibilityPath responsibilityPath) {
        if (responsibilityPath == null) {
            throw invalid("responsibilityPath 必填");
        }
        return switch (responsibilityPath) {
            case PROPERTY_SERVICE_CONTRACT -> ExecutionAuthorityType.CONTRACTUAL_EXECUTION;
            case DEVELOPER_WARRANTY -> ExecutionAuthorityType.WARRANTY_EXECUTION;
            case LIABLE_PARTY -> ExecutionAuthorityType.LIABILITY_EXECUTION;
            case SHARED_COMMON_REPAIR -> ExecutionAuthorityType.OWNER_DECISION;
        };
    }

    private void requireDirectResponsibility(
            ResponsibilityDetermination determination,
            FundingSourceType expectedFundingSource,
            ExecutionAuthorityType expectedExecutionAuthority,
            String responsiblePartyLabel) {
        if (determination.fundingSourceType() != expectedFundingSource
                || determination.executionAuthorityType() != expectedExecutionAuthority) {
            throw invalid(responsiblePartyLabel + "必须使用与责任依据相匹配的资金承担和后续执行路径");
        }
        if (trim(determination.responsiblePartyName()) == null) {
            throw invalid(responsiblePartyLabel + "名称必填");
        }
    }

    /**
     * 相关业主决定提案冻结或直接执行锁定时首次把已核验产权名册固化为费用承担房屋。该快照是后续决定的分母，
     * 不能再用项目端描述、受影响房屋或当前名册替代。
     */
    private AllocationBasis requireAllocationSnapshot(
            PlanVersion plan, Long tenantId, List<AllocationRoom> allocationRooms) {
        if (allocationRooms.isEmpty()) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "已核验产权名册中没有决定范围对应的费用承担房屋，不能锁定实施方案");
        }
        AllocationBasis basis = projectRepository.findAllocationSnapshotBasis(plan.planId(), tenantId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS, "费用承担房屋快照缺少汇总依据，不能锁定实施方案"));
        long roomCount = allocationRooms.stream().map(AllocationRoom::roomId).distinct().count();
        long ownerCount = allocationRooms.stream().map(AllocationRoom::ownerUid).distinct().count();
        BigDecimal totalArea = allocationRooms.stream()
                .map(AllocationRoom::buildArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (basis.roomCount() != roomCount
                || basis.ownerCount() != ownerCount
                || basis.totalBuildArea().compareTo(totalArea) != 0) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "费用承担房屋快照与汇总依据不一致，不能锁定实施方案");
        }
        return basis;
    }

    /** 直接责任路径没有业主分摊房屋；其费用承担快照仍须绑定已确认的责任方和依据。 */
    private AllocationBasis directResponsibilityAllocationBasis(ResponsibilityDetermination determination) {
        String responsibleParty = trim(determination.responsiblePartyName());
        String label = responsibleParty == null
                ? "经确认的责任承担方"
                : "经确认的责任承担方：" + responsibleParty;
        return new AllocationBasis(label, 0, 0, BigDecimal.ZERO);
    }

    /**
     * 资金切片只从已确认的责任与资金初判产生。专项维修资金仍需服务端核验对应账簿；其他责任路径由确认依据
     * 作为承担上限快照，绝不因为项目所在楼栋而回退到专项维修资金账户。
     */
    private List<FundingSlice> resolveFundingSlices(
            RepairProject project,
            DecisionScope decisionScope,
            ResponsibilityDetermination determination,
            PlanVersion plan,
            String allocationSnapshotHash,
            Long tenantId) {
        List<FundingSlice> existing = projectRepository.listFundingSlices(
                decisionScope.decisionScopeId(), tenantId);
        if (!existing.isEmpty()) {
            List<FundingSlice> current = existing.stream()
                    .filter(slice -> Objects.equals(
                            slice.responsibilityDeterminationId(), determination.determinationId()))
                    .toList();
            if (current.size() == existing.size()) {
                return current;
            }
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "项目资金切片与当前工程责任初判不一致，不能锁定实施方案");
        }
        if (determination.fundingSourceType() == FundingSourceType.SPECIAL_MAINTENANCE_LEDGER) {
            return List.of(resolveSpecialMaintenanceFundingSlice(
                    project, decisionScope, determination, plan, allocationSnapshotHash, tenantId));
        }
        if (determination.fundingSourceType() == FundingSourceType.PUBLIC_REVENUE_LEDGER) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "公共收益账簿尚未接入可信余额与授权快照，不能锁定实施方案");
        }
        if (determination.fundingSourceType() == FundingSourceType.OWNER_SELF_FUNDING) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "业主自筹资金归集或托管账簿尚未接入可信快照，不能锁定实施方案");
        }
        FundingSlice fundingSlice = projectRepository.insertFundingSlice(new FundingSlice(
                null, determination.determinationId(), decisionScope.decisionScopeId(), project.projectId(), tenantId,
                determination.fundingSourceType(), "RESPONSIBILITY_DETERMINATION",
                determination.determinationId().toString(), directResponsibilityReference(determination),
                allocationSnapshotHash,
                // 记录的是经确认的责任承担上限内、本方案可使用的预算，不是合同、结算或付款金额。
                plan.budgetTotal(), FundingSliceVerificationStatus.CONFIRMED, false,
                LocalDateTime.now(), null));
        return List.of(fundingSlice);
    }

    private FundingSlice resolveSpecialMaintenanceFundingSlice(
            RepairProject project,
            DecisionScope decisionScope,
            ResponsibilityDetermination determination,
            PlanVersion plan,
            String allocationSnapshotHash,
            Long tenantId) {
        Account account = requireSpecialMaintenanceAccount(decisionScope, plan, tenantId, "锁定实施方案");
        String scopeLabel = decisionScope.scopeType() == ScopeType.COMMUNITY ? "全小区" : "楼栋";
        String ledgerReference = "专项维修资金账户账簿快照（" + scopeLabel
                + "范围，账户版本 " + account.version()
                + "，总余额 " + account.totalBalance().toPlainString()
                + "，已冻结 " + account.frozenBalance().toPlainString()
                + "，可用余额 " + account.availableBalance().toPlainString() + "）";
        return projectRepository.insertFundingSlice(new FundingSlice(
                null, determination.determinationId(), decisionScope.decisionScopeId(), project.projectId(), tenantId,
                FundingSourceType.SPECIAL_MAINTENANCE_LEDGER,
                "MAINTENANCE_FUND_ACCOUNT", account.accountId().toString(), ledgerReference,
                allocationSnapshotHash, plan.budgetTotal(), FundingSliceVerificationStatus.CONFIRMED, false,
                LocalDateTime.now(), null));
    }

    /**
     * 专项维修资金只有在当前工程已确认走该路径时才读取。这里检验的是已有账簿，不会因楼栋范围自动创建账户。
     */
    private Account requireSpecialMaintenanceAccount(
            DecisionScope decisionScope, PlanVersion plan, Long tenantId, String actionLabel) {
        if (decisionScope.scopeType() == ScopeType.BUILDING_UNIT) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS,
                    "已确认专项维修资金路径尚未接入可核验的单元账户标识，不能" + actionLabel);
        }
        AccountScope accountScope = decisionScope.scopeType() == ScopeType.COMMUNITY
                ? AccountScope.COMMUNITY
                : AccountScope.BUILDING;
        Long referenceId = decisionScope.scopeType() == ScopeType.COMMUNITY
                ? tenantId
                : decisionScope.buildingId();
        Account account = maintenanceFundAccountRepository.findByScopeForUpdate(
                        tenantId, accountScope, referenceId)
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        INVALID_STATUS,
                        "已确认专项维修资金路径缺少对应的可信账簿账户，不能" + actionLabel));
        if (account.availableBalance().compareTo(plan.budgetTotal()) < 0) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "专项维修资金账户可用余额不足，不能" + actionLabel);
        }
        return account;
    }

    private String directResponsibilityReference(ResponsibilityDetermination determination) {
        String responsibleParty = trim(determination.responsiblePartyName());
        String prefix = responsibleParty == null
                ? "经确认的责任承担/资金依据："
                : "经确认的责任承担方 " + responsibleParty + "，依据：";
        return prefix + determination.basisReference();
    }

    /**
     * 资金承担范围只能由可信账簿或已确认责任初判写入；附件、范围和前端文本都不能替代该快照。
     * 决定授权、定商、合同和验收证据仍由各自状态机产生，不能通过资金切片倒置生成。
     */
    private void validateTrustedFundingSlices(
            List<FundingSlice> fundingSlices,
            ResponsibilityDetermination determination,
            String allocationSnapshotHash,
            BigDecimal budgetTotal) {
        if (fundingSlices.isEmpty() || fundingSlices.stream().anyMatch(slice ->
                slice.verificationStatus() != FundingSliceVerificationStatus.CONFIRMED
                        || slice.sourceType() == null
                        || slice.sourceType() != determination.fundingSourceType()
                        || !Objects.equals(slice.responsibilityDeterminationId(), determination.determinationId())
                        || trim(slice.sourceRecordType()) == null
                        || trim(slice.sourceRecordId()) == null
                        || trim(slice.ledgerReference()) == null
                        || !allocationSnapshotHash.equals(slice.allocationSnapshotHash())
                        || slice.approvedAmount() == null
                        || slice.approvedAmount().signum() <= 0
                        || slice.verifiedAt() == null)) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "可信资金切片、费用承担范围或账簿快照不完整，不能锁定实施方案");
        }
        BigDecimal totalFundingAmount = fundingSlices.stream()
                .map(FundingSlice::approvedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalFundingAmount.compareTo(budgetTotal) != 0) {
            throw new RepairWorkOrderApplicationException(
                    INVALID_STATUS, "可信资金切片金额与锁定方案预算不一致，不能锁定实施方案");
        }
    }

    private String allocationSnapshotHash(
            DecisionScope decisionScope,
            ResponsibilityDetermination determination,
            PlanVersion plan,
            List<AllocationRoom> allocationRooms,
            AllocationBasis allocationBasis) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("decisionScopeId", decisionScope.decisionScopeId());
        snapshot.put("scopeType", decisionScope.scopeType());
        snapshot.put("buildingId", decisionScope.buildingId());
        snapshot.put("unitName", decisionScope.unitName());
        snapshot.put("responsibilityDetermination", determination);
        snapshot.put("planId", plan.planId());
        snapshot.put("allocationBasis", allocationBasis);
        snapshot.put("allocationRooms", allocationRooms.stream()
                .sorted(Comparator.comparing(AllocationRoom::buildingId)
                        .thenComparing(room -> Objects.toString(room.unitName(), ""))
                        .thenComparing(AllocationRoom::roomId))
                .toList());
        return sha256(snapshot, "维修费用承担范围快照哈希生成失败");
    }

    /**
     * 该哈希只覆盖进入决定/授权程序前必须保持不变的提案事实。最终执行锁定另行加入有效授权和真实资金切片，
     * 避免把“已冻结提案”误说成“已可执行方案”。
     */
    private String authorizationProposalSnapshotHash(
            RepairProject project,
            DecisionScope decisionScope,
            ResponsibilityDetermination determination,
            PlanVersion plan,
            List<WorkPoint> workPoints,
            List<AllocationRoom> allocationRooms,
            AllocationBasis allocationBasis,
            String allocationSnapshotHash) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("snapshotPurpose", "AUTHORIZATION_PROPOSAL");
        snapshot.put("projectId", project.projectId());
        snapshot.put("workflowType", project.workflowType());
        snapshot.put("scopeType", project.scopeType());
        snapshot.put("buildingId", project.buildingId());
        snapshot.put("unitName", project.unitName());
        snapshot.put("decisionScope", decisionScope);
        snapshot.put("responsibilityDetermination", determination);
        snapshot.put("plan", immutablePlanSnapshot(plan));
        snapshot.put("workPoints", workPoints.stream().sorted(Comparator.comparing(WorkPoint::sortOrder)).toList());
        snapshot.put("allocationBasis", allocationBasis);
        snapshot.put("allocationRooms", allocationRooms.stream()
                .sorted(Comparator.comparing(AllocationRoom::buildingId)
                        .thenComparing(room -> Objects.toString(room.unitName(), ""))
                        .thenComparing(AllocationRoom::roomId))
                .toList());
        snapshot.put("allocationSnapshotHash", allocationSnapshotHash);
        return sha256(snapshot, "相关业主决定提案快照哈希生成失败");
    }

    private String snapshotHash(
            RepairProject project,
            DecisionScope decisionScope,
            ResponsibilityDetermination determination,
            GovernanceBasis governanceBasis,
            PlanVersion plan,
            List<WorkPoint> workPoints,
            List<AllocationRoom> allocationRooms,
            AllocationBasis allocationBasis,
            List<FundingSlice> fundingSlices) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectId", project.projectId());
        snapshot.put("workflowType", project.workflowType());
        snapshot.put("scopeType", project.scopeType());
        snapshot.put("buildingId", project.buildingId());
        snapshot.put("unitName", project.unitName());
        snapshot.put("decisionScope", decisionScope);
        snapshot.put("responsibilityDetermination", determination);
        snapshot.put("governanceBasis", governanceBasis);
        snapshot.put("plan", immutablePlanSnapshot(plan));
        snapshot.put("workPoints", workPoints.stream().sorted(Comparator.comparing(WorkPoint::sortOrder)).toList());
        snapshot.put("allocationBasis", allocationBasis);
        snapshot.put("allocationRooms", allocationRooms.stream()
                .sorted(Comparator.comparing(AllocationRoom::buildingId)
                        .thenComparing(room -> Objects.toString(room.unitName(), ""))
                        .thenComparing(AllocationRoom::roomId))
                .toList());
        snapshot.put("fundingSlices", fundingSlices.stream()
                .sorted(Comparator.comparing(FundingSlice::fundingSliceId)).toList());
        return sha256(snapshot, "维修实施方案快照哈希生成失败");
    }

    private String sha256(Map<String, Object> snapshot, String failureMessage) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException(failureMessage, ex);
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
                projectRepository.findCurrentResponsibilityDetermination(projectId, tenantId).orElse(null),
                projectRepository.listResponsibilityDeterminations(projectId, tenantId),
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

    private UserContext requireGovernanceActor() {
        UserContext actor = requireActor();
        if (!actor.hasPermission("repair:workorder:governance")) {
            throw new RepairWorkOrderApplicationException(
                    FORBIDDEN, "当前工作身份无权确认工程责任与资金承担初判");
        }
        return actor;
    }

    private UserContext requirePropertyResponsibilityProposer() {
        UserContext actor = requireActor();
        if (!PROPERTY_RESPONSIBILITY_PROPOSER_ROLES.contains(actor.roleKey())
                || !actor.hasPermission("repair:workorder:manage")) {
            throw new RepairWorkOrderApplicationException(
                    FORBIDDEN, "仅物业可基于勘验、合同或保修依据提出工程责任初判");
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

    private BigDecimal requirePositiveAmount(BigDecimal value, String field) {
        requirePositive(value, field);
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw invalid(field + " 最多保留 2 位小数");
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
