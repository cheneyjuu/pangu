// 关联业务：将冻结的维修授权提案按本小区已启用议事规则接入统一表决，并把结算结果写回工程授权。
package com.pangu.application.repair;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.support.PayloadHasher;
import com.pangu.application.voting.FormalVotingRulePolicy;
import com.pangu.application.voting.VotingDecisionResultProjector;
import com.pangu.application.voting.VotingExecutionService;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.AllocationRoom;
import com.pangu.domain.model.repair.RepairProject.PlanStatus;
import com.pangu.domain.model.repair.RepairProject.PlanVersion;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProjectVoting;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSettlementPolicy;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.VotingSubjectActions;
import com.pangu.domain.repository.OwnersAssemblyRuleRepository;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectVotingRepository;
import com.pangu.domain.repository.VotingResultRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

/**
 * 维修工程正式表决防腐层。
 *
 * <p>维修模块只确认冻结提案、精确费用承担房屋和项目状态；名册、送达、收票、去重及计票全部复用
 * 统一表决内核，禁止再写旧楼栋接龙或手工业主大会结果。
 */
@Service
@RequiredArgsConstructor
public class RepairProjectVotingService {

    private static final Set<String> GOVERNANCE_ROLES = Set.of("COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");
    private static final Set<String> PREPARATION_VIEW_ROLES = Set.of(
            "PROPERTY_MANAGER", "PROPERTY_STAFF", "COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");

    private final RepairProjectRepository projectRepository;
    private final RepairProjectVotingRepository votingRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
    private final OwnersAssemblyRuleRepository ruleRepository;
    private final VotingSubjectRepository subjectRepository;
    private final VotingResultRepository resultRepository;
    private final VotingDecisionResultProjector votingDecisionResultProjector;
    private final VotingExecutionService votingExecutionService;
    private final FormalVotingRulePolicy rulePolicy;
    private final UserContextHolder userContextHolder;
    private final ObjectMapper objectMapper;

    /**
     * 确认本次办理安排并一次性冻结规则、提案和精确表决人名册。
     */
    @Transactional
    public Details prepare(Long projectId, PrepareCommand command) {
        UserContext actor = requireGovernanceActor();
        if (command == null || command.expectedProjectVersion() == null
                || command.collectionMode() == null || command.paperBallotTemplateAttachmentId() == null
                || command.voteStartAt() == null
                || command.voteEndAt() == null) {
            throw invalid("项目版本、本次办理方式、纸质表决票模板、开始时间和截止时间均为必填项");
        }
        if (!command.voteEndAt().isAfter(command.voteStartAt())) {
            throw invalid("表决截止时间必须晚于开始时间");
        }
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        if (project.status() != Status.AUTHORIZATION_IN_PROGRESS
                || !Objects.equals(project.version(), command.expectedProjectVersion())) {
            throw conflict("当前项目或方案已变化，请刷新后重新准备相关业主表决");
        }
        PlanVersion plan = requireFrozenPlan(project);
        if (votingRepository.find(project.projectId(), plan.planId(), project.tenantId()).isPresent()) {
            throw conflict("当前授权提案已经准备过相关业主表决，不能重复发起");
        }
        OwnersAssemblyRule rule = ruleRepository.findActive(project.tenantId())
                .orElseThrow(() -> conflict("本小区尚未启用可用于维修事项的议事规则"));
        Instant preparedAt = Instant.now();
        try {
            rulePolicy.requireExecutable(rule, command.collectionMode(), preparedAt, command.voteStartAt());
        } catch (FormalVotingRulePolicy.UnsupportedRuleException ex) {
            throw conflict(ex.getMessage(), ex);
        }
        RepairProject.Attachment paperBallotTemplate = requirePaperBallotTemplate(
                project, command.paperBallotTemplateAttachmentId());
        String votingSourceHash = PayloadHasher.sha256Hex(String.join("|",
                plan.authorizationSnapshotHash(),
                "PAPER_BALLOT_TEMPLATE",
                paperBallotTemplate.attachmentId().toString(),
                paperBallotTemplate.sha256()));

        List<AllocationRoom> allocationRooms = projectRepository.listAllocationRooms(
                plan.planId(), project.tenantId());
        if (allocationRooms.isEmpty()) {
            throw conflict("当前授权提案没有冻结费用承担房屋，不能发起相关业主表决");
        }
        List<VotingElectorateSnapshot.Candidate> candidates = votingExecutionService
                .listElectorateCandidatesByRoomIds(
                        project.tenantId(), allocationRooms.stream().map(AllocationRoom::roomId).toList());
        validateExactElectorate(allocationRooms, candidates);

        VotingSubject subject = VotingSubjectActions.open(
                project.tenantId(), SubjectType.GENERAL, VotingScope.REPAIR_ALLOCATION, plan.planId(),
                project.projectName() + "实施方案表决", command.voteStartAt(), command.voteEndAt(),
                actor.userId(), null);
        subject.setContent(buildSubjectContent(project, plan, allocationRooms));
        VotingSubject insertedSubject = subjectRepository.insert(subject);
        VotingExecutionPackage executionPackage = votingExecutionService.create(
                new VotingExecutionService.CreatePackageCommand(
                        project.tenantId(), VotingExecutionPackage.BusinessType.REPAIR_PROJECT, plan.planId(),
                        "REPAIR_AUTHORIZATION_PROPOSAL", plan.planId(), votingSourceHash,
                        "OWNERS_ASSEMBLY_RULE_VERSION", rule.ruleId(), rule.configurationSha256(),
                        VotingScope.REPAIR_ALLOCATION, plan.planId(), command.collectionMode(),
                        rulePolicy.duplicateBallotPolicy(rule.configuration(), command.collectionMode()),
                        command.voteStartAt(), command.voteEndAt(), actor.userId()));
        votingExecutionService.attachSubject(
                executionPackage.getPackageId(), project.tenantId(), insertedSubject.getSubjectId(), actor.userId());
        VotingExecutionPackage frozenPackage = votingExecutionService.freezeExactElectorate(
                executionPackage.getPackageId(), project.tenantId(), candidates, actor.userId(), preparedAt);
        RepairProjectVoting link;
        try {
            link = votingRepository.insert(new RepairProjectVoting(
                    null, project.projectId(), plan.planId(), project.tenantId(), insertedSubject.getSubjectId(),
                    frozenPackage.getPackageId(), rule.ruleId(), rule.configurationSha256(),
                    paperBallotTemplate.attachmentId(), paperBallotTemplate.sha256(),
                    command.collectionMode(), RepairProjectVoting.Status.PREPARED, null,
                    actor.userId(), preparedAt, null, null, null, null, 0L));
        } catch (DataIntegrityViolationException ex) {
            throw conflict("当前授权提案已经由其他经办人准备表决，请刷新后查看", ex);
        }
        if (projectRepository.advanceVersion(
                project.projectId(), project.tenantId(), project.version()) != 1) {
            throw conflict("项目已被其他操作更新，请刷新后重试");
        }
        event(project, actor, "REPAIR_VOTING_PREPARED", Map.of(
                "linkId", link.linkId(),
                "planId", plan.planId(),
                "subjectId", insertedSubject.getSubjectId(),
                "executionPackageId", frozenPackage.getPackageId(),
                "ruleId", rule.ruleId(),
                "paperBallotTemplateAttachmentId", paperBallotTemplate.attachmentId(),
                "paperBallotTemplateHash", paperBallotTemplate.sha256(),
                "collectionMode", command.collectionMode().name(),
                "allocationRoomCount", allocationRooms.size()));
        return details(project, plan, link, insertedSubject, frozenPackage, rule);
    }

    /** 管理端只展示服务端按当前有效规则计算出的可选方式和最早开始时间。 */
    @Transactional(readOnly = true)
    public PreparationOptions preparationOptions(Long projectId) {
        UserContext actor = requirePreparationViewer();
        RepairProject project = projectRepository.findProject(projectId, actor.tenantId())
                .orElseThrow(() -> notFound("维修工程项目不存在"));
        requireAuthorizationInProgress(project);
        requireFrozenPlan(project);
        OwnersAssemblyRule rule = ruleRepository.findActive(project.tenantId())
                .orElseThrow(() -> conflict("本小区尚未启用可用于维修事项的议事规则"));
        FormalVotingRulePolicy.PreparationOptions options = rulePolicy.preparationOptions(rule, Instant.now());
        return new PreparationOptions(
                rule.ruleName(), rule.ruleVersion(), options.ready(), options.blockingItems(),
                options.allowedModes(), options.earliestVoteStartAt(), options.validDeliveryMethods(),
                options.paperBallotSealRequired(), options.proxyVotingPolicy());
    }

    /** 到达开始时间后开启同一表决包，不能通过创建第二场表决绕过公示或通知期限。 */
    @Transactional
    public Details open(Long projectId, TransitionCommand command) {
        UserContext actor = requireGovernanceActor();
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireAuthorizationInProgress(project);
        PlanVersion plan = requireFrozenPlan(project);
        RepairProjectVoting link = requireLinkForUpdate(project, plan);
        requireExpectedLinkVersion(link, command);
        if (link.status() != RepairProjectVoting.Status.PREPARED) {
            throw conflict("本次相关业主表决已开始或已结束");
        }
        OwnersAssemblyRule rule = requireFrozenRule(link);
        VotingExecutionPackage executionPackage = requireExecutionPackage(link);
        Instant openedAt = Instant.now();
        votingExecutionService.open(executionPackage.getPackageId(), project.tenantId(), actor.userId(), openedAt);
        if (votingRepository.markVoting(
                link.linkId(), project.tenantId(), actor.userId(), openedAt, link.version()) != 1) {
            throw conflict("表决办理状态已变化，请刷新后重试");
        }
        event(project, actor, "REPAIR_VOTING_OPENED", Map.of(
                "linkId", link.linkId(), "executionPackageId", executionPackage.getPackageId()));
        return details(project, plan,
                votingRepository.find(project.projectId(), plan.planId(), project.tenantId()).orElseThrow(),
                requireSubject(link.subjectId(), project.tenantId()),
                requireExecutionPackage(link), rule);
    }

    /** 截止收票并自动写入通过或未通过结果，客户端不提交统计数或结果。 */
    @Transactional
    public Details settle(Long projectId, TransitionCommand command) {
        UserContext actor = requireGovernanceActor();
        RepairProject project = loadProjectForUpdate(projectId, actor.tenantId());
        requireAuthorizationInProgress(project);
        PlanVersion plan = requireFrozenPlan(project);
        RepairProjectVoting link = requireLinkForUpdate(project, plan);
        requireExpectedLinkVersion(link, command);
        if (link.status() != RepairProjectVoting.Status.VOTING) {
            throw conflict("本次相关业主表决尚未开始或已经结算");
        }
        OwnersAssemblyRule rule = requireFrozenRule(link);
        VotingSubject subject = requireSubject(link.subjectId(), project.tenantId());
        VotingSettlementPolicy settlementPolicy;
        try {
            settlementPolicy = rulePolicy.settlementPolicy(rule, subject.getSubjectType());
        } catch (FormalVotingRulePolicy.UnsupportedRuleException ex) {
            throw conflict(ex.getMessage(), ex);
        }
        Instant settledAt = Instant.now();
        VotingExecutionPackage settledPackage = votingExecutionService.closeAndSettle(
                link.executionPackageId(), project.tenantId(), actor.userId(), settledAt,
                ignored -> settlementPolicy);
        VotingResultRepository.Snapshot result = resultRepository.findBySubjectId(subject.getSubjectId())
                .orElseThrow(() -> conflict("相关业主表决已经截止，但缺少计票结果"));
        RepairProjectVoting.Result repairResult = result.passed()
                ? RepairProjectVoting.Result.PASSED
                : RepairProjectVoting.Result.FAILED;
        if (votingRepository.settle(
                link.linkId(), project.tenantId(), repairResult, actor.userId(), settledAt, link.version()) != 1) {
            throw conflict("表决结果已由其他经办人写入，请刷新后查看");
        }
        if (result.passed()) {
            String basisHash = PayloadHasher.sha256Hex(String.join("|",
                    plan.authorizationSnapshotHash(), settledPackage.getPackageHash(),
                    subject.getSubjectId().toString(), value(result.attestationTxHash())));
            governanceRepository.insertGovernanceBasis(
                    project.projectId(), plan.planId(), project.tenantId(),
                    "OWNER_VOTING_DECISION", "VOTING_SUBJECT", subject.getSubjectId(), basisHash,
                    plan.supplierSelectionMethod(), plan.supplierSelectionEvaluationRule(),
                    plan.minimumInvitedSupplierCount(), plan.minimumValidQuoteCount(),
                    plan.nonCompetitiveSelectionBasis(), plan.budgetTotal(), actor.userId());
            if (projectRepository.advanceStatus(
                    project.projectId(), project.tenantId(), Status.AUTHORIZATION_IN_PROGRESS,
                    Status.AUTHORIZED, project.version()) != 1) {
                throw conflict("项目状态已变化，请刷新后重试");
            }
        } else if (projectRepository.reopenAfterAuthorizationFailure(
                project.projectId(), project.tenantId(), Status.AUTHORIZATION_IN_PROGRESS,
                project.version()) != 1) {
            throw conflict("项目状态已变化，请刷新后重试");
        }
        event(project, actor, "REPAIR_VOTING_SETTLED", Map.of(
                "linkId", link.linkId(),
                "subjectId", subject.getSubjectId(),
                "result", repairResult.name(),
                "quorumSatisfied", result.quorumSatisfied()));
        RepairProject updatedProject = projectRepository.findProject(project.projectId(), project.tenantId())
                .orElseThrow();
        RepairProjectVoting updatedLink = votingRepository.find(
                project.projectId(), plan.planId(), project.tenantId()).orElseThrow();
        return details(updatedProject, plan, updatedLink, subject, settledPackage, rule);
    }

    @Transactional(readOnly = true)
    public Details find(Long projectId) {
        UserContext actor = requireActor();
        RepairProject project = projectRepository.findProject(projectId, actor.tenantId())
                .orElseThrow(() -> notFound("维修工程项目不存在"));
        if (project.activePlanId() == null) {
            throw notFound("当前项目尚未准备相关业主表决");
        }
        PlanVersion plan = projectRepository.listPlans(project.projectId(), project.tenantId()).stream()
                .filter(candidate -> candidate.planId().equals(project.activePlanId()))
                .findFirst()
                .orElseThrow(() -> notFound("当前实施方案不存在"));
        RepairProjectVoting link = votingRepository.find(
                        project.projectId(), plan.planId(), project.tenantId())
                .orElseThrow(() -> notFound("当前项目尚未准备相关业主表决"));
        return details(project, plan, link, requireSubject(link.subjectId(), project.tenantId()),
                requireExecutionPackage(link), requireFrozenRule(link));
    }

    @Transactional(readOnly = true)
    public List<RepairProjectVoting.OwnerTask> listOwnerTasks() {
        UserContext owner = requireOwnerActor();
        return votingRepository.listOwnerTasks(owner.tenantId(), owner.uid());
    }

    private void validateExactElectorate(List<AllocationRoom> allocations,
                                         List<VotingElectorateSnapshot.Candidate> candidates) {
        Map<Long, AllocationRoom> allocationByRoom = allocations.stream().collect(Collectors.toMap(
                AllocationRoom::roomId, item -> item, (left, right) -> {
                    throw conflict("冻结费用承担范围存在重复房屋 roomId=" + left.roomId());
                }, LinkedHashMap::new));
        Map<Long, List<VotingElectorateSnapshot.Candidate>> candidatesByRoom = candidates.stream()
                .collect(Collectors.groupingBy(VotingElectorateSnapshot.Candidate::roomId,
                        LinkedHashMap::new, Collectors.toList()));
        if (!allocationByRoom.keySet().equals(candidatesByRoom.keySet())) {
            throw conflict("费用承担房屋与当前有效产权名册不一致，请重新核验并形成新方案版本");
        }
        for (AllocationRoom allocation : allocations) {
            List<VotingElectorateSnapshot.Candidate> roomRows = candidatesByRoom.get(allocation.roomId());
            Set<Long> rosterIds = roomRows.stream().map(VotingElectorateSnapshot.Candidate::rosterId)
                    .collect(Collectors.toSet());
            if (rosterIds.size() != 1) {
                throw conflict("房屋存在多条有效名册记录，请先完成名册核验 roomId=" + allocation.roomId());
            }
            VotingElectorateSnapshot.Candidate base = roomRows.getFirst();
            if (!Objects.equals(base.buildingId(), allocation.buildingId())
                    || base.certifiedArea() == null || allocation.buildArea() == null
                    || base.certifiedArea().compareTo(allocation.buildArea()) != 0) {
                throw conflict("房屋楼栋或法定面积已变化，请重新核验并形成新方案版本 roomId=" + allocation.roomId());
            }
            List<VotingElectorateSnapshot.Candidate> owners = roomRows.stream()
                    .filter(candidate -> candidate.opid() != null && candidate.uid() != null)
                    .distinct()
                    .toList();
            VotingElectorateSnapshot.Candidate representative;
            if (owners.size() == 1) {
                representative = owners.getFirst();
            } else {
                List<VotingElectorateSnapshot.Candidate> delegates = owners.stream()
                        .filter(VotingElectorateSnapshot.Candidate::votingDelegate).toList();
                if (delegates.size() != 1) {
                    throw conflict("共有产权房屋尚未唯一确认表决代表 roomId=" + allocation.roomId());
                }
                representative = delegates.getFirst();
            }
            if (!Objects.equals(representative.uid(), allocation.ownerUid())) {
                throw conflict("费用承担房屋的表决代表已变化，请重新核验并形成新方案版本 roomId=" + allocation.roomId());
            }
        }
    }

    private RepairProject.Attachment requirePaperBallotTemplate(
            RepairProject project, Long attachmentId) {
        RepairProject.Attachment attachment = projectRepository.findAttachment(
                        attachmentId, project.projectId(), project.tenantId())
                .orElseThrow(() -> invalid("纸质表决票模板不属于当前维修项目"));
        if (!"application/pdf".equalsIgnoreCase(attachment.contentType())) {
            throw invalid("纸质表决票模板必须上传 PDF 原件");
        }
        String hash = attachment.sha256() == null
                ? null : attachment.sha256().trim().toLowerCase(Locale.ROOT);
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw invalid("纸质表决票模板缺少可核验的文件摘要");
        }
        return new RepairProject.Attachment(
                attachment.attachmentId(), attachment.projectId(), attachment.tenantId(), attachment.objectKey(),
                attachment.originalFileName(), attachment.contentType(), attachment.fileSize(), attachment.etag(),
                hash, attachment.uploadedByAccountId(), attachment.uploadedByUserId(), attachment.createTime());
    }

    private String buildSubjectContent(RepairProject project, PlanVersion plan, List<AllocationRoom> rooms) {
        BigDecimal totalArea = rooms.stream().map(AllocationRoom::buildArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return "<p>工程：" + escape(project.projectName()) + "</p>"
                + "<p>实施方案版本：第 " + plan.versionNo() + " 版</p>"
                + "<p>方案预算（含税）：¥" + plan.budgetTotal().toPlainString() + "</p>"
                + "<p>费用承担范围：" + rooms.size() + " 个专有部分，合计 "
                + totalArea.stripTrailingZeros().toPlainString() + " 平方米</p>";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Details details(RepairProject project,
                            PlanVersion plan,
                            RepairProjectVoting link,
                            VotingSubject subject,
                            VotingExecutionPackage executionPackage,
                            OwnersAssemblyRule rule) {
        VotingDecisionResultProjector.View result = resultRepository.findBySubjectId(subject.getSubjectId())
                .map(votingDecisionResultProjector::project)
                .orElse(null);
        return new Details(project, plan, link, subject, executionPackage, rule.ruleName(), rule.ruleVersion(),
                rule.configuration().proxyVotingPolicy(), result);
    }

    private PlanVersion requireFrozenPlan(RepairProject project) {
        if (project.activePlanId() == null) {
            throw conflict("项目没有已冻结的授权提案");
        }
        return projectRepository.listPlans(project.projectId(), project.tenantId()).stream()
                .filter(plan -> plan.planId().equals(project.activePlanId())
                        && plan.status() == PlanStatus.AUTHORIZATION_FROZEN
                        && plan.authorizationSnapshotHash() != null)
                .findFirst()
                .orElseThrow(() -> conflict("项目没有有效的冻结授权提案"));
    }

    private RepairProjectVoting requireLinkForUpdate(RepairProject project, PlanVersion plan) {
        return votingRepository.findForUpdate(project.projectId(), plan.planId(), project.tenantId())
                .orElseThrow(() -> notFound("当前项目尚未准备相关业主表决"));
    }

    private OwnersAssemblyRule requireFrozenRule(RepairProjectVoting link) {
        OwnersAssemblyRule rule = ruleRepository.findById(link.ruleId(), link.tenantId())
                .orElseThrow(() -> conflict("本次表决所依据的议事规则版本不存在"));
        if (!Objects.equals(rule.configurationSha256(), link.ruleConfigurationHash())) {
            throw conflict("本次表决所依据的议事规则配置摘要不一致");
        }
        return rule;
    }

    private VotingExecutionPackage requireExecutionPackage(RepairProjectVoting link) {
        VotingExecutionPackage executionPackage = votingExecutionService.findPackageBySubjectId(link.subjectId())
                .orElseThrow(() -> conflict("本次维修表决缺少统一表决包"));
        if (!Objects.equals(executionPackage.getPackageId(), link.executionPackageId())
                || executionPackage.getBusinessType() != VotingExecutionPackage.BusinessType.REPAIR_PROJECT
                || !Objects.equals(executionPackage.getBusinessReferenceId(), link.planId())) {
            throw conflict("维修项目与统一表决包关联不一致");
        }
        return executionPackage;
    }

    private VotingSubject requireSubject(Long subjectId, Long tenantId) {
        VotingSubject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> conflict("本次维修表决事项不存在"));
        if (!Objects.equals(subject.getTenantId(), tenantId)) {
            throw conflict("本次维修表决事项不属于当前小区");
        }
        return subject;
    }

    private void requireExpectedLinkVersion(RepairProjectVoting link, TransitionCommand command) {
        if (command == null || command.expectedLinkVersion() == null
                || command.expectedLinkVersion() != link.version()) {
            throw conflict("表决办理状态已变化，请刷新后重试");
        }
    }

    private void requireAuthorizationInProgress(RepairProject project) {
        if (project.status() != Status.AUTHORIZATION_IN_PROGRESS) {
            throw conflict("当前项目不在相关业主表决办理阶段");
        }
    }

    private RepairProject loadProjectForUpdate(Long projectId, Long tenantId) {
        return projectRepository.findProjectForUpdate(projectId, tenantId)
                .orElseThrow(() -> notFound("维修工程项目不存在"));
    }

    private UserContext requireGovernanceActor() {
        UserContext actor = requireActor();
        if (!actor.isSysUser() || actor.userId() == null || !GOVERNANCE_ROLES.contains(actor.roleKey())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "仅业委会可确认并办理相关业主正式表决");
        }
        return actor;
    }

    /** 物业需要提前查看生效规则和材料要求，但确认、开始及结算仍只属于业委会。 */
    private UserContext requirePreparationViewer() {
        UserContext actor = requireActor();
        if (!actor.isSysUser() || actor.userId() == null || !PREPARATION_VIEW_ROLES.contains(actor.roleKey())) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "仅物业和业委会可查看相关业主表决准备要求");
        }
        return actor;
    }

    private UserContext requireOwnerActor() {
        UserContext actor = requireActor();
        if (!actor.isCUser() || actor.uid() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "仅业主本人可查看维修表决任务");
        }
        return actor;
    }

    private UserContext requireActor() {
        UserContext actor = userContextHolder.current();
        if (actor == null || actor.accountId() == null || actor.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到当前小区身份");
        }
        return actor;
    }

    private void event(RepairProject project, UserContext actor, String action, Map<String, Object> payload) {
        try {
            projectRepository.insertEvent(project.projectId(), project.tenantId(), action,
                    actor.accountId(), actor.userId(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("维修表决事件序列化失败", ex);
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

    private RepairWorkOrderApplicationException conflict(String message, Throwable cause) {
        return new RepairWorkOrderApplicationException(INVALID_STATUS, message, cause);
    }

    private RepairWorkOrderApplicationException notFound(String message) {
        return new RepairWorkOrderApplicationException(NOT_FOUND, message);
    }

    public record PrepareCommand(
            Integer expectedProjectVersion,
            VotingExecutionPackage.CollectionMode collectionMode,
            Long paperBallotTemplateAttachmentId,
            Instant voteStartAt,
            Instant voteEndAt
    ) {
    }

    public record PreparationOptions(
            String ruleName,
            String ruleVersion,
            boolean ready,
            List<FormalVotingRulePolicy.ReadinessIssue> blockingItems,
            Set<VotingExecutionPackage.CollectionMode> allowedModes,
            Instant earliestVoteStartAt,
            Set<com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration.DeliveryMethod> validDeliveryMethods,
            Boolean paperBallotSealRequired,
            com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration.ProxyVotingPolicy proxyVotingPolicy
    ) {
        public PreparationOptions {
            blockingItems = blockingItems == null ? List.of() : List.copyOf(blockingItems);
        }
    }

    public record TransitionCommand(Long expectedLinkVersion) {
    }

    public record Details(
            RepairProject project,
            PlanVersion plan,
            RepairProjectVoting voting,
            VotingSubject subject,
            VotingExecutionPackage executionPackage,
            String ruleName,
            String ruleVersion,
            com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration.ProxyVotingPolicy proxyVotingPolicy,
            VotingDecisionResultProjector.View result
    ) {
    }
}
