// 关联业务：编排楼栋/单元维修接龙、物业报审、业委会审价审批盖章和授权依据。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.repair.command.ApproveBuildingRepairCommand;
import com.pangu.application.repair.command.CompleteBuildingRepairDecisionCommand;
import com.pangu.application.repair.command.ReviewBuildingRepairPriceCommand;
import com.pangu.application.repair.command.SealBuildingRepairCommand;
import com.pangu.application.repair.command.SealBuildingRepairCommand.SupplierSelectionAuthorization;
import com.pangu.application.repair.command.StartBuildingRepairDecisionCommand;
import com.pangu.application.repair.command.SubmitBuildingRepairOfficialDocumentCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.community.TenantCommunity;
import com.pangu.domain.model.repair.RepairLocalDecision;
import com.pangu.domain.model.repair.RepairLocalDecisionChannel;
import com.pangu.domain.model.repair.RepairLocalDecisionScopeType;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
import com.pangu.domain.model.repair.RepairProject.AllocationBasis;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProjectGovernance;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingDecision;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcess;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcessDetails;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcessStatus;
import com.pangu.domain.model.repair.RepairProjectGovernance.DecisionEntry;
import com.pangu.domain.model.repair.RepairProjectGovernance.DecisionPolicySnapshot;
import com.pangu.domain.model.repair.RepairProjectGovernance.DecisionRoomParticipation;
import com.pangu.domain.model.repair.RepairProjectGovernance.GovernanceResult;
import com.pangu.domain.model.repair.RepairProjectGovernance.NonResponseRule;
import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairDecisionRule;
import com.pangu.domain.repository.RepairDecisionRuleRepository;
import com.pangu.domain.model.repair.RepairVoteChoice;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import com.pangu.domain.repository.CommunitySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

@Service
@RequiredArgsConstructor
public class BuildingRepairWorkflowService {

    private static final Set<String> PROPERTY_ROLES = Set.of("PROPERTY_STAFF", "PROPERTY_MANAGER");
    private static final Set<String> COMMITTEE_REVIEW_ROLES = Set.of("COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");
    private static final Set<String> COMMITTEE_SEAL_ROLES = Set.of("COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");
    private static final Set<String> REVIEW_MODES = Set.of(
            "INTERNAL_PRICE_REVIEW", "THIRD_PARTY_AUDIT", "NOT_REQUIRED");
    private static final Set<String> REVIEW_CONCLUSIONS = Set.of("APPROVED", "REJECTED");
    private static final Set<RepairSupplierSelectionMethod> NON_COMPETITIVE_SELECTION_METHODS = Set.of(
            RepairSupplierSelectionMethod.FRAMEWORK_SUPPLIER,
            RepairSupplierSelectionMethod.DIRECT_AWARD,
            RepairSupplierSelectionMethod.EMERGENCY_APPOINTMENT);
    private final RepairProjectRepository projectRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
    private final RepairDecisionRuleRepository decisionRuleRepository;
    private final CommunitySettingsRepository communitySettingsRepository;
    private final RepairWorkOrderRepository workOrderRepository;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;

    @Transactional
    public BuildingProcessDetails startDecision(Long projectId, StartBuildingRepairDecisionCommand command) {
        UserContext actor = requireRole(PROPERTY_ROLES, "仅物业可发起楼栋维修征询");
        if (command == null || command.expectedProjectVersion() == null) {
            throw invalid("expectedProjectVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireBuildingProject(project, Status.PLAN_LOCKED);
        if (!project.version().equals(command.expectedProjectVersion())) {
            throw conflict("项目版本已变化，请刷新后再发起楼栋维修征询");
        }
        PlanVersion plan = activeLockedPlan(project);
        RepairDecisionRule rule = decisionRuleRepository.findActive(project.tenantId())
                .orElseThrow(() -> conflict("当前小区尚未备案有效的维修征询规则，禁止发起征询"));
        if (rule.nonResponseRule() != NonResponseRule.NOT_PARTICIPATED) {
            throw invalid("当前备案规则包含未表态推导票，但系统尚未支持该计票方式，禁止发起征询");
        }
        List<AllocationRoom> allocation = projectRepository.listAllocationRooms(plan.planId(), project.tenantId());
        if (allocation.isEmpty()) {
            throw conflict("实施方案没有费用承担房屋快照");
        }
        AllocationBasis allocationBasis = projectRepository.findAllocationSnapshotBasis(
                        plan.planId(), project.tenantId())
                .orElseThrow(() -> conflict("实施方案没有可读取的费用承担范围快照"));
        String scopeLabel = allocationBasis.scopeLabel() + " · 费用承担范围内业主";
        RepairLocalDecisionChannel decisionChannel = decisionChannel(project.tenantId());
        DecisionPolicySnapshot policy = governanceRepository.insertPolicySnapshot(new DecisionPolicySnapshot(
                null, project.projectId(), plan.planId(), project.tenantId(), rule.ruleId(), rule.ruleName(), null,
                rule.ruleVersion(), rule.sha256(), rule.effectiveAt(), decisionChannel,
                rule.deliveryRule(), rule.nonResponseRule(), "LOCKED", actor.userId(), null));
        BigDecimal totalArea = allocation.stream()
                .map(AllocationRoom::buildArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BuildingDecision decision = governanceRepository.insertBuildingDecision(new BuildingDecision(
                null, project.projectId(), plan.planId(), project.tenantId(), project.buildingId(),
                project.scopeType() == RepairProject.ScopeType.BUILDING_UNIT
                        ? RepairLocalDecisionScopeType.BUILDING_UNIT
                        : RepairLocalDecisionScopeType.BUILDING,
                decisionChannel, project.unitName(),
                scopeLabel, Math.toIntExact(allocation.stream().map(AllocationRoom::ownerUid).distinct().count()), totalArea,
                null, null, null, null, null, null, null, null, null, null,
                null, false, "COLLECTING", null, null));
        BuildingProcess process = governanceRepository.insertBuildingProcess(new BuildingProcess(
                null, project.projectId(), plan.planId(), project.tenantId(),
                policy.policySnapshotId(), decision.decisionId(), BuildingProcessStatus.DECISION_COLLECTING,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, 0, null, null));
        if (projectRepository.advanceStatus(
                project.projectId(), project.tenantId(), Status.PLAN_LOCKED,
                Status.GOVERNANCE_IN_PROGRESS, project.version()) != 1) {
            throw conflict("项目状态已变化，请刷新后重试");
        }
        event(project, actor, "BUILDING_DECISION_STARTED", Map.of(
                "processId", process.processId(), "decisionId", decision.decisionId(),
                "policySnapshotId", policy.policySnapshotId(), "ruleId", rule.ruleId(),
                "scopeLabel", scopeLabel, "decisionChannel", decisionChannel.name()));
        return details(project, process);
    }

    @Transactional
    public BuildingProcessDetails completeDecision(
            Long projectId, CompleteBuildingRepairDecisionCommand command) {
        UserContext actor = requireRole(PROPERTY_ROLES, "仅物业可核验楼栋维修接龙");
        if (command == null || command.expectedProcessVersion() == null) {
            throw invalid("expectedProcessVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireBuildingProject(project, Status.GOVERNANCE_IN_PROGRESS);
        PlanVersion plan = activeLockedPlan(project);
        BuildingProcess process = activeProcessForUpdate(project, plan);
        requireProcess(process, BuildingProcessStatus.DECISION_COLLECTING, command.expectedProcessVersion());
        BuildingDecision decision = governanceRepository.findBuildingDecision(
                        process.decisionId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修决定不存在"));
        DecisionCompletion completion = decision.decisionChannel() == RepairLocalDecisionChannel.WECHAT
                ? completeWechatDecision(project, decision, command, actor)
                : completeOnlineDecision(project, decision, command);
        boolean passed = completion.passed();
        String result = completion.result();
        BuildingProcessStatus next = passed
                ? BuildingProcessStatus.DECISION_PASSED
                : BuildingProcessStatus.DECISION_FAILED;
        if (governanceRepository.updateBuildingProcessStatus(
                process.processId(), project.tenantId(), process.status().name(), next.name(),
                process.processVersion()) != 1) {
            throw conflict("楼栋维修流程已变化，请刷新后重试");
        }
        if (!passed && projectRepository.advanceStatus(
                project.projectId(), project.tenantId(), Status.GOVERNANCE_IN_PROGRESS,
                Status.PLAN_LOCKED, project.version()) != 1) {
            throw conflict("项目状态已变化，请刷新后重试");
        }
        event(project, actor, "BUILDING_DECISION_COMPLETED", completion.eventPayload(process.processId()));
        return details(project, reloadProcess(project, plan));
    }

    @Transactional
    public BuildingProcessDetails submitOfficialDocument(
            Long projectId, SubmitBuildingRepairOfficialDocumentCommand command) {
        UserContext actor = requireRole(PROPERTY_ROLES, "仅物业可提交楼栋维修正式报审文件");
        if (command == null || command.expectedProcessVersion() == null || command.attachmentId() == null) {
            throw invalid("expectedProcessVersion 和 attachmentId 均为必填项");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireBuildingProject(project, Status.GOVERNANCE_IN_PROGRESS);
        PlanVersion plan = activeLockedPlan(project);
        BuildingProcess process = activeProcessForUpdate(project, plan);
        requireProcess(process, BuildingProcessStatus.DECISION_PASSED, command.expectedProcessVersion());
        attachment(project, command.attachmentId(), this::isDocument, "物业正式报审文件");
        if (governanceRepository.attachOfficialDocument(
                process.processId(), project.tenantId(), command.attachmentId(), process.processVersion()) != 1) {
            throw conflict("楼栋维修流程已变化，请刷新后重试");
        }
        event(project, actor, "BUILDING_OFFICIAL_DOCUMENT_SUBMITTED", Map.of(
                "processId", process.processId(), "attachmentId", command.attachmentId()));
        return details(project, reloadProcess(project, plan));
    }

    @Transactional
    public BuildingProcessDetails reviewPrice(Long projectId, ReviewBuildingRepairPriceCommand command) {
        UserContext actor = requireRole(COMMITTEE_REVIEW_ROLES, "仅业委会可审查楼栋维修价格");
        if (command == null || command.expectedProcessVersion() == null) {
            throw invalid("expectedProcessVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireBuildingProject(project, Status.GOVERNANCE_IN_PROGRESS);
        PlanVersion plan = activeLockedPlan(project);
        BuildingProcess process = activeProcessForUpdate(project, plan);
        requireProcess(process, BuildingProcessStatus.OFFICIAL_DOCUMENT_READY, command.expectedProcessVersion());
        String reviewMode = requireAllowed(command.reviewMode(), "reviewMode", REVIEW_MODES);
        String conclusion = requireAllowed(command.conclusion(), "conclusion", REVIEW_CONCLUSIONS);
        if (command.reviewedAmount() == null || command.reviewedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid("reviewedAmount 必须大于 0");
        }
        if (command.reviewedAmount().compareTo(plan.budgetTotal()) > 0) {
            throw invalid("审价金额超过锁定方案预算，必须先形成新方案并重新履行楼栋决定");
        }
        if (plan.priceReviewRequired() && "NOT_REQUIRED".equals(reviewMode)) {
            throw invalid("锁定方案要求审价，不能标记为无需审价");
        }
        if (!"NOT_REQUIRED".equals(reviewMode)) {
            if (command.reportAttachmentId() == null) {
                throw invalid("审价报告附件必填");
            }
            attachment(project, command.reportAttachmentId(), this::isDocument, "审价报告");
        }
        if (governanceRepository.recordPriceReview(
                process.processId(), project.tenantId(), reviewMode, command.reviewedAmount(),
                command.reportAttachmentId(), conclusion, trim(command.opinion()), actor.userId(),
                process.processVersion()) != 1) {
            throw conflict("楼栋维修流程已变化，请刷新后重试");
        }
        if ("REJECTED".equals(conclusion) && projectRepository.advanceStatus(
                project.projectId(), project.tenantId(), Status.GOVERNANCE_IN_PROGRESS,
                Status.PLAN_LOCKED, project.version()) != 1) {
            throw conflict("项目状态已变化，请刷新后重试");
        }
        event(project, actor, "BUILDING_PRICE_REVIEWED", Map.of(
                "processId", process.processId(), "reviewMode", reviewMode,
                "reviewedAmount", command.reviewedAmount(), "conclusion", conclusion));
        return details(project, reloadProcess(project, plan));
    }

    @Transactional
    public BuildingProcessDetails approve(Long projectId, ApproveBuildingRepairCommand command) {
        UserContext actor = requireActor();
        if (command == null || command.expectedProcessVersion() == null) {
            throw invalid("expectedProcessVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireBuildingProject(project, Status.GOVERNANCE_IN_PROGRESS);
        PlanVersion plan = activeLockedPlan(project);
        BuildingProcess process = activeProcessForUpdate(project, plan);
        requireProcess(process, BuildingProcessStatus.PRICE_REVIEWED, command.expectedProcessVersion());
        String position = workOrderRepository.findActiveCommitteePosition(project.tenantId(), actor.userId())
                .orElseThrow(() -> new RepairWorkOrderApplicationException(
                        FORBIDDEN, "仅在任业委会主任或副主任可在线确认"));
        if (!Set.of("DIRECTOR", "VICE_DIRECTOR").contains(position)) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "仅业委会主任或副主任可在线确认");
        }
        if (governanceRepository.recordCommitteeApproval(
                process.processId(), project.tenantId(), actor.userId(), position,
                trim(command.opinion()), process.processVersion()) != 1) {
            throw conflict("楼栋维修流程已变化，请刷新后重试");
        }
        event(project, actor, "BUILDING_COMMITTEE_APPROVED", Map.of(
                "processId", process.processId(), "approverPosition", position));
        return details(project, reloadProcess(project, plan));
    }

    @Transactional
    public BuildingProcessDetails seal(Long projectId, SealBuildingRepairCommand command) {
        UserContext actor = requireRole(COMMITTEE_SEAL_ROLES, "仅业委会成员可办理楼栋维修用印");
        if (!actor.hasPermission("committee:seal:use")) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前业委会成员未获用印权限");
        }
        if (command == null || command.expectedProcessVersion() == null || command.sealedAttachmentId() == null
                || command.supplierSelectionAuthorization() == null) {
            throw invalid("expectedProcessVersion、sealedAttachmentId 和施工单位选择授权快照均为必填项");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireBuildingProject(project, Status.GOVERNANCE_IN_PROGRESS);
        PlanVersion plan = activeLockedPlan(project);
        BuildingProcess process = activeProcessForUpdate(project, plan);
        requireProcess(process, BuildingProcessStatus.COMMITTEE_APPROVED, command.expectedProcessVersion());
        SupplierSelectionAuthorization authorization = validateSupplierSelectionAuthorization(
                command.supplierSelectionAuthorization());
        DecisionPolicySnapshot policy = governanceRepository.findPolicySnapshot(
                        process.policySnapshotId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修规则快照不存在"));
        BuildingDecision decision = governanceRepository.findBuildingDecision(
                        process.decisionId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修决定不存在"));
        if (!GovernanceResult.PASSED.name().equals(decision.result())) {
            throw conflict("楼栋维修决定未通过，不能用印并授权施工单位选择");
        }
        if (process.reviewedAmount() == null || process.reviewedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw conflict("楼栋维修缺少已通过的审价金额，不能用印并授权施工单位选择");
        }
        Attachment source = attachment(
                project, process.officialDocumentAttachmentId(), this::isDocument, "物业正式报审文件");
        Attachment sealed = attachment(
                project, command.sealedAttachmentId(), this::isDocument, "业委会盖章文件");
        Long usageId = governanceRepository.insertProjectSealUsage(
                project.tenantId(), project.projectId(), project.projectName(),
                source.attachmentId(), sealed.attachmentId(), source.sha256(), sealed.sha256(),
                actor.userId(), trim(command.remark()));
        if (governanceRepository.authorizeBuildingProcess(
                process.processId(), project.tenantId(), usageId, process.processVersion()) != 1) {
            throw conflict("楼栋维修流程已变化，请刷新后重试");
        }
        String basisHash = sha256(String.join("|",
                value(plan.snapshotHash()), value(process.processId()), value(policy.policySnapshotId()),
                value(policy.ruleHash()), value(process.decisionId()), value(decision.result()),
                value(process.reviewedAmount()), value(source.sha256()), value(sealed.sha256()),
                value(authorization.selectionMethod()), value(authorization.evaluationRule()),
                value(authorization.minimumInvitedSupplierCount()), value(authorization.minimumValidQuoteCount()),
                value(authorization.nonCompetitiveSelectionBasis())));
        governanceRepository.insertGovernanceBasis(
                project.projectId(), plan.planId(), project.tenantId(),
                "BUILDING_REPAIR_DECISION", "BUILDING_PROCESS", process.processId(),
                basisHash, authorization.selectionMethod(), authorization.evaluationRule(),
                authorization.minimumInvitedSupplierCount(), authorization.minimumValidQuoteCount(),
                authorization.nonCompetitiveSelectionBasis(), actor.userId());
        if (projectRepository.advanceStatus(
                project.projectId(), project.tenantId(), Status.GOVERNANCE_IN_PROGRESS,
                Status.AUTHORIZED, project.version()) != 1) {
            throw conflict("项目状态已变化，请刷新后重试");
        }
        Map<String, Object> authorizationEvent = new LinkedHashMap<>();
        authorizationEvent.put("processId", process.processId());
        authorizationEvent.put("sealUsageId", usageId);
        authorizationEvent.put("basisHash", basisHash);
        authorizationEvent.put("selectionMethod", authorization.selectionMethod().name());
        authorizationEvent.put("evaluationRule", authorization.evaluationRule().name());
        authorizationEvent.put("minimumInvitedSupplierCount", authorization.minimumInvitedSupplierCount());
        authorizationEvent.put("minimumValidQuoteCount", authorization.minimumValidQuoteCount());
        authorizationEvent.put("nonCompetitiveSelectionBasis", authorization.nonCompetitiveSelectionBasis());
        event(project, actor, "BUILDING_GOVERNANCE_AUTHORIZED", authorizationEvent);
        return details(project, reloadProcess(project, plan));
    }

    @Transactional(readOnly = true)
    public BuildingProcessDetails find(Long projectId) {
        UserContext actor = requireActor();
        RepairProject project = projectRepository.findProject(projectId, actor.tenantId())
                .orElseThrow(() -> notFound("维修工程项目不存在"));
        if (project.workflowType() != RepairWorkflowType.BUILDING_REPAIR || project.activePlanId() == null) {
            throw notFound("楼栋维修治理流程不存在");
        }
        BuildingProcess process = governanceRepository.findBuildingProcess(
                        project.projectId(), project.activePlanId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修治理流程不存在"));
        return details(project, process);
    }

    /** 审计接口单独留痕，并且只向拥有维修表决审计能力的角色返回逐房屋选择。 */
    @Transactional
    public BuildingProcessDetails auditDecision(Long projectId) {
        UserContext actor = requireActor();
        if (!actor.hasPermission("repair:decision:audit")) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "当前角色无权查看逐房屋表决选择");
        }
        RepairProject project = projectRepository.findProject(projectId, actor.tenantId())
                .orElseThrow(() -> notFound("维修工程项目不存在"));
        if (project.workflowType() != RepairWorkflowType.BUILDING_REPAIR || project.activePlanId() == null) {
            throw notFound("楼栋维修治理流程不存在");
        }
        BuildingProcess process = governanceRepository.findBuildingProcess(
                        project.projectId(), project.activePlanId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修治理流程不存在"));
        event(project, actor, "BUILDING_DECISION_CHOICES_ACCESSED", Map.of(
                "processId", process.processId(), "decisionId", process.decisionId()));
        return details(project, process, true);
    }

    private DecisionCompletion completeWechatDecision(
            RepairProject project,
            BuildingDecision decision,
            CompleteBuildingRepairDecisionCommand command,
            UserContext actor) {
        if (command.evidenceAttachmentId() == null || command.confirmedResult() == null) {
            throw invalid("微信接龙必须上传原始截图并由物业确认通过或未通过");
        }
        Attachment evidence = attachment(
                project, command.evidenceAttachmentId(), this::isImageOrPdf, "微信接龙原始截图");
        governanceRepository.insertDecisionEvidence(
                decision.decisionId(), project.tenantId(), evidence.sha256(), actor.accountId(), actor.userId());
        String result = command.confirmedResult().name();
        if (governanceRepository.completeBuildingDecisionByConfirmation(
                decision.decisionId(), project.tenantId(), evidence.sha256(), result) != 1) {
            throw conflict("楼栋维修决定已结算，请刷新后重试");
        }
        return DecisionCompletion.wechat(command.confirmedResult() == GovernanceResult.PASSED, result);
    }

    private DecisionCompletion completeOnlineDecision(
            RepairProject project,
            BuildingDecision decision,
            CompleteBuildingRepairDecisionCommand command) {
        if (command.evidenceAttachmentId() != null || command.confirmedResult() != null) {
            throw invalid("C端在线表决由系统票仓自动结算，不接收微信截图或人工结果");
        }
        List<DecisionEntry> entries = governanceRepository.listDecisionEntries(
                decision.decisionId(), project.tenantId());
        Tally tally = tally(entries);
        boolean passed = RepairLocalDecision.passesThreshold(
                tally.participatedOwnerCount(), tally.participatedArea(),
                tally.agreeOwnerCount(), tally.agreeArea(),
                decision.totalOwnerCount(), decision.totalArea());
        String result = passed ? GovernanceResult.PASSED.name() : GovernanceResult.FAILED.name();
        if (governanceRepository.completeBuildingDecision(
                decision.decisionId(), project.tenantId(),
                tally.participatedOwnerCount(), tally.participatedArea(),
                tally.agreeOwnerCount(), tally.agreeArea(),
                tally.disagreeOwnerCount(), tally.disagreeArea(),
                tally.abstainOwnerCount(), tally.abstainArea(),
                tally.invalidOwnerCount(), tally.invalidArea(), null, result) != 1) {
            throw conflict("楼栋维修决定已结算，请刷新后重试");
        }
        return DecisionCompletion.online(passed, result, tally);
    }

    private Tally tally(List<DecisionEntry> entries) {
        Map<RepairVoteChoice, List<DecisionEntry>> groups = new EnumMap<>(RepairVoteChoice.class);
        for (DecisionEntry entry : entries) {
            groups.computeIfAbsent(entry.choice(), ignored -> new ArrayList<>()).add(entry);
        }
        int agreeCount = size(groups, RepairVoteChoice.AGREE);
        int disagreeCount = size(groups, RepairVoteChoice.DISAGREE);
        int abstainCount = size(groups, RepairVoteChoice.ABSTAIN);
        int invalidCount = size(groups, RepairVoteChoice.INVALID);
        return new Tally(
                agreeCount + disagreeCount + abstainCount,
                area(groups, RepairVoteChoice.AGREE)
                        .add(area(groups, RepairVoteChoice.DISAGREE))
                        .add(area(groups, RepairVoteChoice.ABSTAIN)),
                agreeCount, area(groups, RepairVoteChoice.AGREE),
                disagreeCount, area(groups, RepairVoteChoice.DISAGREE),
                abstainCount, area(groups, RepairVoteChoice.ABSTAIN),
                invalidCount, area(groups, RepairVoteChoice.INVALID));
    }

    private int size(Map<RepairVoteChoice, List<DecisionEntry>> groups, RepairVoteChoice choice) {
        return groups.getOrDefault(choice, List.of()).size();
    }

    private BigDecimal area(Map<RepairVoteChoice, List<DecisionEntry>> groups, RepairVoteChoice choice) {
        return groups.getOrDefault(choice, List.of()).stream()
                .map(DecisionEntry::buildArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BuildingProcessDetails details(RepairProject project, BuildingProcess process) {
        return details(project, process, false);
    }

    private BuildingProcessDetails details(
            RepairProject project, BuildingProcess process, boolean includeChoices) {
        DecisionPolicySnapshot policy = governanceRepository.findPolicySnapshot(
                        process.policySnapshotId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修规则快照不存在"));
        BuildingDecision decision = governanceRepository.findBuildingDecision(
                        process.decisionId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修决定不存在"));
        List<DecisionRoomParticipation> participation = governanceRepository
                .listDecisionRoomParticipations(decision.decisionId(), project.tenantId())
                .stream()
                .map(entry -> new DecisionRoomParticipation(
                        entry.roomId(), entry.buildArea(), entry.choice() != null,
                        includeChoices ? entry.choice() : null))
                .toList();
        return new BuildingProcessDetails(process, policy, decision, participation);
    }

    private RepairLocalDecisionChannel decisionChannel(Long tenantId) {
        String configured = communitySettingsRepository.findCommunity(tenantId)
                .map(TenantCommunity::buildingRepairDefaultDecisionChannel)
                .orElse(RepairLocalDecisionChannel.WECHAT.name());
        try {
            return RepairLocalDecisionChannel.valueOf(configured);
        } catch (IllegalArgumentException ex) {
            throw invalid("小区楼栋维修默认表决方式配置不合法：" + configured);
        }
    }

    private BuildingProcess activeProcessForUpdate(RepairProject project, PlanVersion plan) {
        return governanceRepository.findBuildingProcessForUpdate(
                        project.projectId(), plan.planId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修治理流程不存在"));
    }

    private BuildingProcess reloadProcess(RepairProject project, PlanVersion plan) {
        return governanceRepository.findBuildingProcess(project.projectId(), plan.planId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修治理流程不存在"));
    }

    private void requireProcess(BuildingProcess process, BuildingProcessStatus status, Integer expectedVersion) {
        if (process.status() != status) {
            throw conflict("当前楼栋维修流程状态不允许该动作 status=" + process.status());
        }
        if (!process.processVersion().equals(expectedVersion)) {
            throw conflict("楼栋维修流程版本已变化，请刷新后重试");
        }
    }

    private PlanVersion activeLockedPlan(RepairProject project) {
        return projectRepository.listPlans(project.projectId(), project.tenantId()).stream()
                .filter(plan -> plan.planId().equals(project.activePlanId()) && plan.status() == PlanStatus.LOCKED)
                .findFirst()
                .orElseThrow(() -> conflict("项目没有有效的锁定实施方案"));
    }

    private Attachment attachment(
            RepairProject project, Long attachmentId, Predicate<String> typeRule, String label) {
        if (attachmentId == null) {
            throw invalid(label + "必填");
        }
        Attachment attachment = projectRepository.findAttachment(
                        attachmentId, project.projectId(), project.tenantId())
                .orElseThrow(() -> notFound(label + "不存在"));
        if (!typeRule.test(attachment.contentType())) {
            throw invalid(label + "文件类型不合法");
        }
        return attachment;
    }

    private boolean isImageOrPdf(String contentType) {
        return contentType != null && (contentType.startsWith("image/") || "application/pdf".equals(contentType));
    }

    private boolean isDocument(String contentType) {
        return contentType != null && (isImageOrPdf(contentType)
                || contentType.contains("word") || contentType.contains("excel")
                || contentType.contains("spreadsheet"));
    }

    /**
     * 授权书未记载数量门槛时必须保留空值，系统不得套用三家报价等默认规则。
     */
    private SupplierSelectionAuthorization validateSupplierSelectionAuthorization(
            SupplierSelectionAuthorization authorization) {
        if (authorization.selectionMethod() == null || authorization.evaluationRule() == null) {
            throw invalid("施工单位选择方式和评审规则均为必填项");
        }
        Integer invited = authorization.minimumInvitedSupplierCount();
        Integer quotes = authorization.minimumValidQuoteCount();
        if ((invited != null && invited <= 0) || (quotes != null && quotes <= 0)) {
            throw invalid("授权文件中的最低数量必须大于 0");
        }
        if (invited != null && quotes != null && quotes > invited) {
            throw invalid("授权文件中的最低有效报价数不得大于最低邀价数");
        }
        String nonCompetitiveBasis = trim(authorization.nonCompetitiveSelectionBasis());
        if (authorization.selectionMethod() == RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION) {
            if (authorization.evaluationRule() != SupplierSelectionEvaluationRule.LOWEST_COMPLIANT_QUOTE
                    && authorization.evaluationRule() != SupplierSelectionEvaluationRule.COMPREHENSIVE_EVALUATION) {
                throw invalid("竞争性报价授权必须明确最低合格报价或综合评审规则");
            }
            if (nonCompetitiveBasis != null) {
                throw invalid("竞争性报价授权不得填写非竞争定商依据");
            }
        } else if (NON_COMPETITIVE_SELECTION_METHODS.contains(authorization.selectionMethod())) {
            if (authorization.evaluationRule() != SupplierSelectionEvaluationRule.AUTHORIZED_DIRECT_SELECTION) {
                throw invalid("框架、直接或紧急定商必须使用授权直接选择规则");
            }
            if (nonCompetitiveBasis == null) {
                throw invalid("非竞争定商必须填写授权文件中的明确依据");
            }
            if (invited != null || quotes != null) {
                throw invalid("非竞争定商授权不得伪造最低邀价或报价数量门槛");
            }
        } else {
            throw invalid("施工单位选择方式不合法");
        }
        return new SupplierSelectionAuthorization(
                authorization.selectionMethod(), authorization.evaluationRule(), invited, quotes, nonCompetitiveBasis);
    }

    private void requireBuildingProject(RepairProject project, Status expectedStatus) {
        if (project.workflowType() != RepairWorkflowType.BUILDING_REPAIR) {
            throw invalid("全小区公共维修不能进入楼栋维修治理流程");
        }
        if (project.status() != expectedStatus) {
            throw conflict("当前项目状态不允许该动作 status=" + project.status());
        }
    }

    private RepairProject loadProjectForUpdate(Long projectId, Long tenantId) {
        return projectRepository.findProjectForUpdate(projectId, tenantId)
                .orElseThrow(() -> notFound("维修工程项目不存在"));
    }

    private UserContext requireRole(Set<String> roles, String message) {
        UserContext actor = requireActor();
        if (!roles.contains(actor.roleKey())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, message);
        }
        return actor;
    }

    private UserContext requireActor() {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.accountId() == null
                || actor.userId() == null || actor.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前小区管理端工作身份");
        }
        return actor;
    }

    private void event(RepairProject project, UserContext actor, String action, Map<String, Object> payload) {
        try {
            projectRepository.insertEvent(
                    project.projectId(), project.tenantId(), action, actor.accountId(), actor.userId(),
                    objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修工程治理事件序列化失败", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 缺少 SHA-256", ex);
        }
    }

    private String requireAllowed(String value, String field, Set<String> allowed) {
        String normalized = requireText(value, field).toUpperCase();
        if (!allowed.contains(normalized)) {
            throw invalid(field + " 取值不合法");
        }
        return normalized;
    }

    private String requireText(String value, String field) {
        String normalized = trim(value);
        if (normalized == null) {
            throw invalid(field + " 必填");
        }
        return normalized;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private RepairWorkOrderApplicationException invalid(String message) {
        return new RepairWorkOrderApplicationException(PARAM_INVALID, message);
    }

    private RepairWorkOrderApplicationException conflict(String message) {
        return new RepairWorkOrderApplicationException(INVALID_STATUS, message);
    }

    private RepairWorkOrderApplicationException notFound(String message) {
        return new RepairWorkOrderApplicationException(NOT_FOUND, message);
    }

    private record DecisionCompletion(
            boolean passed,
            String result,
            Tally tally,
            RepairLocalDecisionChannel channel
    ) {
        private static DecisionCompletion wechat(boolean passed, String result) {
            return new DecisionCompletion(passed, result, null, RepairLocalDecisionChannel.WECHAT);
        }

        private static DecisionCompletion online(boolean passed, String result, Tally tally) {
            return new DecisionCompletion(passed, result, tally, RepairLocalDecisionChannel.ONLINE);
        }

        private Map<String, Object> eventPayload(Long processId) {
            if (tally == null) {
                return Map.of(
                        "processId", processId,
                        "result", result,
                        "decisionChannel", channel.name(),
                        "confirmedByProperty", true);
            }
            return Map.of(
                    "processId", processId,
                    "result", result,
                    "decisionChannel", channel.name(),
                    "participatedOwnerCount", tally.participatedOwnerCount(),
                    "agreeOwnerCount", tally.agreeOwnerCount(),
                    "participatedArea", tally.participatedArea(),
                    "agreeArea", tally.agreeArea());
        }
    }

    private record Tally(
            int participatedOwnerCount,
            BigDecimal participatedArea,
            int agreeOwnerCount,
            BigDecimal agreeArea,
            int disagreeOwnerCount,
            BigDecimal disagreeArea,
            int abstainOwnerCount,
            BigDecimal abstainArea,
            int invalidOwnerCount,
            BigDecimal invalidArea
    ) {
    }
}
