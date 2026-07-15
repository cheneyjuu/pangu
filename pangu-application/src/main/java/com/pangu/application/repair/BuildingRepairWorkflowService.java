// 关联业务：编排楼栋/单元维修接龙、物业报审、业委会审价审批盖章和授权依据。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.repair.command.ApproveBuildingRepairCommand;
import com.pangu.application.repair.command.CompleteBuildingRepairDecisionCommand;
import com.pangu.application.repair.command.ReviewBuildingRepairPriceCommand;
import com.pangu.application.repair.command.SealBuildingRepairCommand;
import com.pangu.application.repair.command.StartBuildingRepairDecisionCommand;
import com.pangu.application.repair.command.SubmitBuildingRepairOfficialDocumentCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.repair.RepairLocalDecision;
import com.pangu.domain.model.repair.RepairLocalDecisionChannel;
import com.pangu.domain.model.repair.RepairLocalDecisionScopeType;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
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
import com.pangu.domain.model.repair.RepairProjectGovernance.NonResponseRule;
import com.pangu.domain.model.repair.RepairVoteChoice;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairWorkOrderRepository;
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
import java.util.LinkedHashSet;
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
    private static final Set<RepairVoteChoice> EXPLICIT_CHOICES = Set.of(
            RepairVoteChoice.AGREE, RepairVoteChoice.DISAGREE,
            RepairVoteChoice.ABSTAIN, RepairVoteChoice.INVALID);

    private final RepairProjectRepository projectRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
    private final RepairWorkOrderRepository workOrderRepository;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;

    @Transactional
    public BuildingProcessDetails startDecision(Long projectId, StartBuildingRepairDecisionCommand command) {
        UserContext actor = requireRole(PROPERTY_ROLES, "仅物业可发起楼栋维修征询");
        if (command == null || command.expectedProjectVersion() == null
                || command.ruleDocumentAttachmentId() == null || command.nonResponseRule() == null) {
            throw invalid("expectedProjectVersion、ruleDocumentAttachmentId 和 nonResponseRule 均为必填项");
        }
        if (command.nonResponseRule() != NonResponseRule.NOT_PARTICIPATED) {
            throw invalid("尚未接入备案规则推导票能力，未表态房屋只能保持未参与");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireBuildingProject(project, Status.PLAN_LOCKED);
        if (!project.version().equals(command.expectedProjectVersion())) {
            throw conflict("项目版本已变化，请刷新后再发起楼栋维修征询");
        }
        PlanVersion plan = activeLockedPlan(project);
        Attachment ruleDocument = attachment(
                project, command.ruleDocumentAttachmentId(), this::isDocument, "备案议事规则附件");
        List<AllocationRoom> allocation = projectRepository.listAllocationRooms(plan.planId(), project.tenantId());
        if (allocation.isEmpty()) {
            throw conflict("实施方案没有费用承担房屋快照");
        }
        DecisionPolicySnapshot policy = governanceRepository.insertPolicySnapshot(new DecisionPolicySnapshot(
                null, project.projectId(), plan.planId(), project.tenantId(), ruleDocument.attachmentId(),
                requireText(command.ruleVersion(), "ruleVersion"), ruleDocument.sha256(),
                RepairLocalDecisionChannel.WECHAT, requireText(command.deliveryRule(), "deliveryRule"),
                command.nonResponseRule(), "LOCKED", actor.userId(), null));
        BigDecimal totalArea = allocation.stream()
                .map(AllocationRoom::buildArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BuildingDecision decision = governanceRepository.insertBuildingDecision(new BuildingDecision(
                null, project.projectId(), plan.planId(), project.tenantId(), project.buildingId(),
                project.scopeType() == RepairProject.ScopeType.BUILDING_UNIT
                        ? RepairLocalDecisionScopeType.BUILDING_UNIT
                        : RepairLocalDecisionScopeType.BUILDING,
                RepairLocalDecisionChannel.WECHAT, project.unitName(),
                requireText(command.scopeLabel(), "scopeLabel"), allocation.size(), totalArea,
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
                "policySnapshotId", policy.policySnapshotId()));
        return details(project, process);
    }

    @Transactional
    public BuildingProcessDetails completeDecision(
            Long projectId, CompleteBuildingRepairDecisionCommand command) {
        UserContext actor = requireRole(PROPERTY_ROLES, "仅物业可核验楼栋维修接龙");
        if (command == null || command.expectedProcessVersion() == null
                || command.evidenceAttachmentId() == null) {
            throw invalid("expectedProcessVersion 和 evidenceAttachmentId 均为必填项");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireBuildingProject(project, Status.GOVERNANCE_IN_PROGRESS);
        PlanVersion plan = activeLockedPlan(project);
        BuildingProcess process = activeProcessForUpdate(project, plan);
        requireProcess(process, BuildingProcessStatus.DECISION_COLLECTING, command.expectedProcessVersion());
        Attachment evidence = attachment(project, command.evidenceAttachmentId(), this::isImageOrPdf, "接龙截图");
        List<AllocationRoom> allocation = projectRepository.listAllocationRooms(plan.planId(), project.tenantId());
        List<DecisionEntry> entries = verifiedEntries(allocation, command.entries());
        Tally tally = tally(entries);
        BuildingDecision decision = governanceRepository.findBuildingDecision(
                        process.decisionId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修决定不存在"));
        boolean passed = RepairLocalDecision.passesThreshold(
                tally.participatedOwnerCount(), tally.participatedArea(),
                tally.agreeOwnerCount(), tally.agreeArea(),
                decision.totalOwnerCount(), decision.totalArea());
        for (DecisionEntry entry : entries) {
            governanceRepository.insertDecisionEntry(
                    decision.decisionId(), project.tenantId(), entry, actor.userId());
        }
        governanceRepository.insertDecisionEvidence(
                decision.decisionId(), project.tenantId(), evidence.sha256(), actor.accountId(), actor.userId());
        String result = passed ? "PASSED" : "FAILED";
        if (governanceRepository.completeBuildingDecision(
                decision.decisionId(), project.tenantId(),
                tally.participatedOwnerCount(), tally.participatedArea(),
                tally.agreeOwnerCount(), tally.agreeArea(),
                tally.disagreeOwnerCount(), tally.disagreeArea(),
                tally.abstainOwnerCount(), tally.abstainArea(),
                tally.invalidOwnerCount(), tally.invalidArea(), evidence.sha256(), result) != 1) {
            throw conflict("楼栋维修决定已结算，请刷新后重试");
        }
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
        event(project, actor, "BUILDING_DECISION_COMPLETED", Map.of(
                "processId", process.processId(), "result", result,
                "participatedOwnerCount", tally.participatedOwnerCount(),
                "agreeOwnerCount", tally.agreeOwnerCount(),
                "participatedArea", tally.participatedArea(), "agreeArea", tally.agreeArea()));
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
        if (command == null || command.expectedProcessVersion() == null || command.sealedAttachmentId() == null) {
            throw invalid("expectedProcessVersion 和 sealedAttachmentId 均为必填项");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireBuildingProject(project, Status.GOVERNANCE_IN_PROGRESS);
        PlanVersion plan = activeLockedPlan(project);
        BuildingProcess process = activeProcessForUpdate(project, plan);
        requireProcess(process, BuildingProcessStatus.COMMITTEE_APPROVED, command.expectedProcessVersion());
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
                plan.snapshotHash(), process.processId().toString(), process.decisionId().toString(),
                process.reviewedAmount().toPlainString(), sealed.sha256()));
        governanceRepository.insertGovernanceBasis(
                project.projectId(), plan.planId(), project.tenantId(),
                "BUILDING_REPAIR_DECISION", "BUILDING_PROCESS", process.processId(),
                basisHash, actor.userId());
        if (projectRepository.advanceStatus(
                project.projectId(), project.tenantId(), Status.GOVERNANCE_IN_PROGRESS,
                Status.AUTHORIZED, project.version()) != 1) {
            throw conflict("项目状态已变化，请刷新后重试");
        }
        event(project, actor, "BUILDING_GOVERNANCE_AUTHORIZED", Map.of(
                "processId", process.processId(), "sealUsageId", usageId, "basisHash", basisHash));
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

    private List<DecisionEntry> verifiedEntries(
            List<AllocationRoom> allocation,
            List<CompleteBuildingRepairDecisionCommand.Entry> submitted) {
        Map<Long, AllocationRoom> byRoom = new LinkedHashMap<>();
        for (AllocationRoom room : allocation) {
            byRoom.put(room.roomId(), room);
        }
        Map<Long, CompleteBuildingRepairDecisionCommand.Entry> submittedByRoom = new LinkedHashMap<>();
        for (CompleteBuildingRepairDecisionCommand.Entry entry : submitted) {
            if (entry == null || entry.roomId() == null || entry.choice() == null) {
                throw invalid("接龙明细必须包含 roomId 和 choice");
            }
            if (!EXPLICIT_CHOICES.contains(entry.choice())) {
                throw invalid("物业只能核验同意、不同意、弃权或无效原始接龙项");
            }
            if (!byRoom.containsKey(entry.roomId())) {
                throw invalid("接龙房屋不在锁定分摊范围 roomId=" + entry.roomId());
            }
            if (submittedByRoom.put(entry.roomId(), entry) != null) {
                throw invalid("同一房屋不能重复提交接龙意见 roomId=" + entry.roomId());
            }
            requireText(entry.originalText(), "entries.originalText");
        }
        List<DecisionEntry> verified = new ArrayList<>();
        for (AllocationRoom room : allocation) {
            CompleteBuildingRepairDecisionCommand.Entry source = submittedByRoom.get(room.roomId());
            verified.add(new DecisionEntry(
                    room.roomId(), room.ownerUid(), source == null ? RepairVoteChoice.NOT_VOTED : source.choice(),
                    room.buildArea(), source == null ? null : trim(source.originalText())));
        }
        return verified;
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
        DecisionPolicySnapshot policy = governanceRepository.findPolicySnapshot(
                        process.policySnapshotId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修规则快照不存在"));
        BuildingDecision decision = governanceRepository.findBuildingDecision(
                        process.decisionId(), project.tenantId())
                .orElseThrow(() -> notFound("楼栋维修决定不存在"));
        return new BuildingProcessDetails(
                process, policy, decision,
                governanceRepository.listDecisionEntries(decision.decisionId(), project.tenantId()));
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

    private RepairWorkOrderApplicationException invalid(String message) {
        return new RepairWorkOrderApplicationException(PARAM_INVALID, message);
    }

    private RepairWorkOrderApplicationException conflict(String message) {
        return new RepairWorkOrderApplicationException(INVALID_STATUS, message);
    }

    private RepairWorkOrderApplicationException notFound(String message) {
        return new RepairWorkOrderApplicationException(NOT_FOUND, message);
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
