// 关联业务：编排全体共用维修授权提案与正式业主大会会议、表决包和单个事项的关联及结算。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.repair.command.LinkCommunityRepairAssemblySubjectCommand;
import com.pangu.application.repair.command.SettleCommunityRepairAssemblySubjectCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProjectGovernance;
import com.pangu.domain.model.repair.RepairProjectGovernance.AssemblyLinkStatus;
import com.pangu.domain.model.repair.RepairProjectGovernance.AssemblySubjectLink;
import com.pangu.domain.model.repair.RepairProjectGovernance.GovernanceResult;
import com.pangu.domain.model.repair.RepairWorkflowType;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.VotingResultRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

@Service
@RequiredArgsConstructor
public class CommunityAssemblyRepairWorkflowService {

    private static final Set<String> ORGANIZER_ROLES = Set.of("COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");
    private static final Set<String> RESULT_VERIFIER_ROLES = Set.of(
            "PROPERTY_MANAGER", "PROPERTY_STAFF", "COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");
    private static final Set<String> LINKABLE_PACKAGE_STATUSES = Set.of(
            "PACKAGE_DRAFT", "PUBLIC_NOTICE", "VOTING", "SETTLED");

    private final RepairProjectRepository projectRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
    private final OwnersAssemblyRepository ownersAssemblyRepository;
    private final VotingSubjectRepository votingSubjectRepository;
    private final VotingResultRepository votingResultRepository;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;

    @Transactional
    public AssemblySubjectLink linkSubject(
            Long projectId, LinkCommunityRepairAssemblySubjectCommand command) {
        UserContext actor = requireOrganizer();
        if (command == null || command.expectedProjectVersion() == null
                || command.packageId() == null || command.subjectId() == null) {
            throw invalid("expectedProjectVersion、packageId 和 subjectId 均为必填项");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        boolean authorizationProposal = requireCommunityProjectForAuthorizationStart(project);
        if (!project.version().equals(command.expectedProjectVersion())) {
            throw conflict("项目版本已变化，请刷新后再关联业主大会事项");
        }
        PlanVersion plan = activeAuthorizationPlan(project);
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository
                .findPackage(command.packageId(), project.tenantId())
                .orElseThrow(() -> notFound("业主大会表决包不存在"));
        if (!LINKABLE_PACKAGE_STATUSES.contains(ballotPackage.status())) {
            throw conflict("业主大会表决包状态不允许关联 status=" + ballotPackage.status());
        }
        if (!ownersAssemblyRepository.listSubjectIds(ballotPackage.packageId(), project.tenantId())
                .contains(command.subjectId())) {
            throw notFound("表决事项不属于指定表决包");
        }
        VotingSubject subject = votingSubjectRepository.findById(command.subjectId())
                .orElseThrow(() -> notFound("业主大会表决事项不存在"));
        if (!project.tenantId().equals(subject.getTenantId()) || subject.getScope() != VotingScope.COMMUNITY) {
            throw invalid("全小区公共维修只能关联当前小区的 COMMUNITY 表决事项");
        }
        AssemblySubjectLink link = governanceRepository.insertAssemblySubjectLink(new AssemblySubjectLink(
                null, project.projectId(), plan.planId(), project.tenantId(), ballotPackage.sessionId(),
                ballotPackage.packageId(), subject.getSubjectId(), AssemblyLinkStatus.LINKED,
                null, actor.userId(), null, null, null));
        if (!authorizationProposal && projectRepository.advanceStatus(
                project.projectId(), project.tenantId(), Status.PLAN_LOCKED,
                Status.GOVERNANCE_IN_PROGRESS, project.version()) != 1) {
            throw conflict("项目状态已变化，请刷新后重试");
        }
        event(project, actor, "COMMUNITY_ASSEMBLY_SUBJECT_LINKED", Map.of(
                "linkId", link.linkId(), "sessionId", link.sessionId(),
                "packageId", link.packageId(), "subjectId", link.subjectId()));
        return link;
    }

    @Transactional
    public AssemblySubjectLink settleSubject(
            Long projectId, SettleCommunityRepairAssemblySubjectCommand command) {
        UserContext actor = requireResultVerifier();
        if (command == null || command.expectedProjectVersion() == null) {
            throw invalid("expectedProjectVersion 必填");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        boolean authorizationProposal = requireCommunityProjectInAuthorization(project);
        if (!project.version().equals(command.expectedProjectVersion())) {
            throw conflict("项目版本已变化，请刷新后再读取业主大会结果");
        }
        PlanVersion plan = activeAuthorizationPlan(project);
        AssemblySubjectLink link = governanceRepository.findAssemblySubjectLinkForUpdate(
                        project.projectId(), plan.planId(), project.tenantId())
                .orElseThrow(() -> notFound("维修项目尚未关联业主大会事项"));
        if (link.status() != AssemblyLinkStatus.LINKED) {
            throw conflict("业主大会事项结果已经写入维修项目");
        }
        OwnersAssemblyPackage ballotPackage = ownersAssemblyRepository
                .findPackage(link.packageId(), project.tenantId())
                .orElseThrow(() -> notFound("业主大会表决包不存在"));
        if (!"SETTLED".equals(ballotPackage.status())) {
            throw conflict("业主大会表决包尚未完成正式结算 status=" + ballotPackage.status());
        }
        VotingSubject subject = votingSubjectRepository.findById(link.subjectId())
                .orElseThrow(() -> notFound("业主大会表决事项不存在"));
        if (subject.getStatus() != SubjectStatus.SETTLED) {
            throw conflict("业主大会表决事项尚未结算 status=" + subject.getStatus());
        }
        VotingResultRepository.Snapshot result = votingResultRepository.findBySubjectId(link.subjectId())
                .orElseThrow(() -> conflict("业主大会表决事项缺少结算快照"));
        GovernanceResult governanceResult = result.passed()
                ? GovernanceResult.PASSED
                : GovernanceResult.FAILED;
        if (governanceRepository.settleAssemblySubjectLink(
                link.linkId(), project.tenantId(), governanceResult.name(), actor.userId()) != 1) {
            throw conflict("业主大会事项关联状态已变化，请刷新后重试");
        }
        if (result.passed()) {
            String basisHash = sha256(String.join("|",
                    authorizationProposalSnapshotHash(plan), value(ballotPackage.packageHash()), link.subjectId().toString(),
                    value(result.attestationTxHash())));
            governanceRepository.insertGovernanceBasis(
                    project.projectId(), plan.planId(), project.tenantId(),
                    "COMMUNITY_ASSEMBLY_DECISION", "ASSEMBLY_SUBJECT", link.subjectId(),
                    basisHash, plan.supplierSelectionMethod(), plan.supplierSelectionEvaluationRule(),
                    plan.minimumInvitedSupplierCount(), plan.minimumValidQuoteCount(),
                    plan.nonCompetitiveSelectionBasis(), plan.budgetTotal(), actor.userId());
        }
        int updated;
        if (result.passed()) {
            Status expectedStatus = authorizationProposal
                    ? Status.AUTHORIZATION_IN_PROGRESS
                    : Status.GOVERNANCE_IN_PROGRESS;
            updated = projectRepository.advanceStatus(
                    project.projectId(), project.tenantId(), expectedStatus,
                    Status.AUTHORIZED, project.version());
        } else if (authorizationProposal) {
            updated = projectRepository.reopenAfterAuthorizationFailure(
                    project.projectId(), project.tenantId(), Status.AUTHORIZATION_IN_PROGRESS, project.version());
        } else {
            updated = projectRepository.advanceStatus(
                    project.projectId(), project.tenantId(), Status.GOVERNANCE_IN_PROGRESS,
                    Status.PLAN_LOCKED, project.version());
        }
        if (updated != 1) {
            throw conflict("项目状态已变化，请刷新后重试");
        }
        event(project, actor, "COMMUNITY_ASSEMBLY_SUBJECT_SETTLED", Map.of(
                "linkId", link.linkId(), "subjectId", link.subjectId(),
                "result", governanceResult.name()));
        return governanceRepository.findAssemblySubjectLink(
                        project.projectId(), plan.planId(), project.tenantId())
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public AssemblySubjectLink find(Long projectId) {
        UserContext actor = requireActor();
        RepairProject project = projectRepository.findProject(projectId, actor.tenantId())
                .orElseThrow(() -> notFound("维修工程项目不存在"));
        if (project.workflowType() != RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR
                || project.activePlanId() == null) {
            throw notFound("全小区维修业主大会事项关联不存在");
        }
        return governanceRepository.findAssemblySubjectLink(
                        project.projectId(), project.activePlanId(), project.tenantId())
                .orElseThrow(() -> notFound("全小区维修业主大会事项关联不存在"));
    }

    /** 新项目以授权提案进入业主大会；PLAN_LOCKED 仅用于兼容已在办理的历史项目。 */
    private boolean requireCommunityProjectForAuthorizationStart(RepairProject project) {
        if (project.workflowType() != RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR) {
            throw invalid("楼栋维修不能进入全小区业主大会流程");
        }
        if (project.status() == Status.AUTHORIZATION_IN_PROGRESS) {
            return true;
        }
        if (project.status() == Status.PLAN_LOCKED) {
            return false;
        }
        throw conflict("当前项目状态不允许关联业主大会事项 status=" + project.status());
    }

    private boolean requireCommunityProjectInAuthorization(RepairProject project) {
        if (project.workflowType() != RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR) {
            throw invalid("楼栋维修不能进入全小区业主大会流程");
        }
        if (project.status() == Status.AUTHORIZATION_IN_PROGRESS) {
            return true;
        }
        if (project.status() == Status.GOVERNANCE_IN_PROGRESS) {
            return false;
        }
        throw conflict("当前项目状态不允许结算业主大会事项 status=" + project.status());
    }

    private PlanVersion activeAuthorizationPlan(RepairProject project) {
        PlanStatus expectedPlanStatus = project.status() == Status.AUTHORIZATION_IN_PROGRESS
                ? PlanStatus.AUTHORIZATION_FROZEN
                : PlanStatus.LOCKED;
        return projectRepository.listPlans(project.projectId(), project.tenantId()).stream()
                .filter(plan -> plan.planId().equals(project.activePlanId()) && plan.status() == expectedPlanStatus)
                .findFirst()
                .orElseThrow(() -> conflict("项目没有有效的授权提案或历史锁定实施方案"));
    }

    private String authorizationProposalSnapshotHash(PlanVersion plan) {
        if (plan.authorizationSnapshotHash() != null) {
            return plan.authorizationSnapshotHash();
        }
        if (plan.snapshotHash() != null) {
            return plan.snapshotHash();
        }
        throw conflict("项目缺少授权提案或历史锁定快照");
    }

    private RepairProject loadProjectForUpdate(Long projectId, Long tenantId) {
        return projectRepository.findProjectForUpdate(projectId, tenantId)
                .orElseThrow(() -> notFound("维修工程项目不存在"));
    }

    private UserContext requireOrganizer() {
        UserContext actor = requireActor();
        if (!ORGANIZER_ROLES.contains(actor.roleKey())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "仅业委会可关联和确认业主大会维修事项");
        }
        return actor;
    }

    private UserContext requireResultVerifier() {
        UserContext actor = requireActor();
        if (!RESULT_VERIFIER_ROLES.contains(actor.roleKey())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "仅物业或业委会可核验业主大会维修事项结果");
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
            throw new IllegalStateException("维修工程业主大会事件序列化失败", ex);
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

    private String value(String value) {
        return value == null ? "" : value;
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
}
