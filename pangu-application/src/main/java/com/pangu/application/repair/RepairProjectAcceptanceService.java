// 关联业务：按锁定实施方案编排工程验收参与、结论确认，并登记后续完工披露与质保期。
package com.pangu.application.repair;

import com.pangu.application.repair.RepairProjectApplicationSupport.Context;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.CreateCompletionDisclosure;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.FinalizeAcceptance;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.RecordAcceptanceParty;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.RecordOwnerAcceptance;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.SealCommunityAcceptance;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.model.repair.RepairAcceptanceConclusion;
import com.pangu.domain.model.repair.RepairAcceptanceDecision;
import com.pangu.domain.model.repair.RepairAcceptancePartyRole;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceParty;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptancePolicy;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRequirement;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRound;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceSummary;
import com.pangu.domain.model.repair.RepairProjectExecution.CompletionDisclosure;
import com.pangu.domain.model.repair.RepairProjectExecution.OwnerAcceptanceTask;
import com.pangu.domain.model.repair.RepairProjectExecution.Settlement;
import com.pangu.domain.policy.repair.RepairProjectAcceptancePolicy;
import com.pangu.domain.repository.RepairProjectExecutionRepository;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.domain.repository.RepairWorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RepairProjectAcceptanceService {

    private static final Set<String> BUILDING_LEADER_ROLES = Set.of("OWNER_REPRESENTATIVE");
    private static final Set<String> COMMITTEE_ROLES = Set.of("COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");
    private static final Set<String> PROPERTY_ROLES = Set.of("PROPERTY_MANAGER", "PROPERTY_STAFF");
    private static final Set<String> THIRD_PARTY_RECORD_ROLES = Set.of(
            "PROPERTY_MANAGER", "COMMITTEE_DIRECTOR", "COMMITTEE_MEMBER");
    private static final RepairProjectAcceptancePolicy ACCEPTANCE_POLICY =
            new RepairProjectAcceptancePolicy.Configured();

    private final RepairProjectApplicationSupport support;
    private final RepairProjectExecutionRepository executionRepository;
    private final RepairProjectGovernanceRepository governanceRepository;
    private final RepairWorkOrderRepository workOrderRepository;

    @Transactional(readOnly = true)
    public OwnerAcceptanceTask ownerTask(Long projectId) {
        UserContext actor = support.requireOwnerActor();
        return ownerTask(projectId, actor);
    }

    @Transactional(readOnly = true)
    public List<OwnerAcceptanceTask> listOwnerTasks() {
        UserContext actor = support.requireOwnerActor();
        return executionRepository.listOpenAcceptanceProjectIds(actor.tenantId(), actor.uid()).stream()
                .map(projectId -> ownerTask(projectId, actor))
                .toList();
    }

    private OwnerAcceptanceTask ownerTask(Long projectId, UserContext actor) {
        Context context = support.load(projectId, actor.tenantId());
        if (context.project().status() != Status.PENDING_ACCEPTANCE) {
            throw support.forbidden("当前业主没有该项目的验收任务");
        }
        AcceptancePolicy policy = policy(context);
        requireRoleConfigured(policy, RepairAcceptancePartyRole.AFFECTED_OWNER);
        AcceptanceRound round = round(context);
        List<Long> roomIds = executionRepository.listAffectedOwnerRoomIds(
                policy.policyId(), actor.tenantId(), actor.uid());
        if (roomIds.isEmpty()) {
            throw support.forbidden("当前业主不在项目锁定的受影响验收范围内");
        }
        String participantKey = "AFFECTED_OWNER:" + actor.uid();
        AcceptanceParty current = executionRepository.listAcceptanceParties(
                        round.acceptanceId(), actor.tenantId()).stream()
                .filter(party -> party.participantKey().equals(participantKey))
                .reduce((first, second) -> second)
                .orElse(null);
        return new OwnerAcceptanceTask(
                projectId, context.project().projectName(), policy, round, roomIds, current,
                executionRepository.summarizeAcceptance(round.acceptanceId(), actor.tenantId()));
    }

    @Transactional
    public AcceptanceParty recordBuildingLeader(Long projectId, RecordAcceptanceParty command) {
        UserContext actor = support.requireSysActor(
                BUILDING_LEADER_ROLES, "仅本楼栋楼组长可提交楼组长验收");
        require(command != null, "验收命令不能为空");
        Context context = acceptanceContext(projectId, actor.tenantId());
        AcceptancePolicy policy = policy(context);
        requireRoleConfigured(policy, RepairAcceptancePartyRole.BUILDING_LEADER);
        if (context.project().buildingId() == null
                || (!actor.authorizedBuildingIds().contains(context.project().buildingId())
                && actor.authorizedBuildingScopes().stream().noneMatch(scope ->
                scope.tenantId().equals(context.project().tenantId())
                        && scope.buildingId().equals(context.project().buildingId())))) {
            throw support.forbidden("当前楼组长不负责本项目楼栋");
        }
        AcceptanceRound round = round(context);
        AcceptanceParty party = new AcceptanceParty(
                null, round.acceptanceId(), "BUILDING_LEADER:" + actor.userId(),
                RepairAcceptancePartyRole.BUILDING_LEADER, null, null, actor.accountId(), actor.userId(),
                requiredText(command.participantName(), "participantName"), null, null,
                conclusion(command), text(command.opinion()), "ONLINE_SELF",
                evidenceForRole(context, policy, RepairAcceptancePartyRole.BUILDING_LEADER,
                        command.evidenceAttachmentId()), null, actor.userId(), null);
        executionRepository.insertAcceptanceParty(actor.tenantId(), party);
        support.event(context, actor, "PROJECT_BUILDING_LEADER_ACCEPTANCE", Map.of(
                "acceptanceId", round.acceptanceId(), "conclusion", party.conclusion().name()));
        return latest(round, actor.tenantId(), party.participantKey());
    }

    @Transactional
    public AcceptanceParty recordOwner(Long projectId, RecordOwnerAcceptance command) {
        UserContext actor = support.requireOwnerActor();
        Context context = acceptanceContext(projectId, actor.tenantId());
        require(command != null && command.roomId() != null, "roomId 必填");
        AcceptancePolicy policy = policy(context);
        requireRoleConfigured(policy, RepairAcceptancePartyRole.AFFECTED_OWNER);
        if (!executionRepository.affectedOwnerIncluded(
                policy.policyId(), actor.tenantId(), command.roomId(), actor.uid())) {
            throw support.forbidden("当前业主及房屋不在项目锁定的受影响验收范围内");
        }
        RepairAcceptanceConclusion conclusion = conclusion(command.conclusion(), command.opinion());
        AcceptanceRound round = round(context);
        AcceptanceParty party = new AcceptanceParty(
                null, round.acceptanceId(), "AFFECTED_OWNER:" + actor.uid(),
                RepairAcceptancePartyRole.AFFECTED_OWNER, command.roomId(), actor.uid(),
                actor.accountId(), null, requiredText(command.participantName(), "participantName"),
                null, null, conclusion, text(command.opinion()), "ONLINE_SELF",
                evidenceForRole(context, policy, RepairAcceptancePartyRole.AFFECTED_OWNER,
                        command.evidenceAttachmentId()), null, null, null);
        executionRepository.insertAcceptanceParty(actor.tenantId(), party);
        support.ownerEvent(context, actor, "PROJECT_AFFECTED_OWNER_ACCEPTANCE", Map.of(
                "acceptanceId", round.acceptanceId(), "roomId", command.roomId(),
                "conclusion", conclusion.name()));
        return latest(round, actor.tenantId(), party.participantKey());
    }

    @Transactional
    public AcceptanceParty recordCommitteeExecutive(Long projectId, RecordAcceptanceParty command) {
        UserContext actor = support.requireSysActor(COMMITTEE_ROLES, "仅在任业委会主任或副主任可在线验收");
        require(command != null, "验收命令不能为空");
        Context context = acceptanceContext(projectId, actor.tenantId());
        AcceptancePolicy policy = policy(context);
        requireRoleConfigured(policy, RepairAcceptancePartyRole.COMMITTEE_EXECUTIVE_APPROVER);
        String position = workOrderRepository.findActiveCommitteePosition(actor.tenantId(), actor.userId())
                .orElseThrow(() -> support.forbidden("仅在任业委会主任或副主任可在线验收"));
        if (!Set.of("DIRECTOR", "VICE_DIRECTOR").contains(position)) {
            throw support.forbidden("仅在任业委会主任或副主任可在线验收");
        }
        AcceptanceRound round = round(context);
        AcceptanceParty party = new AcceptanceParty(
                null, round.acceptanceId(), "COMMITTEE_EXECUTIVE:" + actor.userId(),
                RepairAcceptancePartyRole.COMMITTEE_EXECUTIVE_APPROVER,
                null, null, actor.accountId(), actor.userId(),
                requiredText(command.participantName(), "participantName"), null, position,
                conclusion(command), text(command.opinion()), "ONLINE_SELF",
                evidenceForRole(context, policy, RepairAcceptancePartyRole.COMMITTEE_EXECUTIVE_APPROVER,
                        command.evidenceAttachmentId()), null, actor.userId(), null);
        executionRepository.insertAcceptanceParty(actor.tenantId(), party);
        support.event(context, actor, "PROJECT_COMMITTEE_EXECUTIVE_ACCEPTANCE", Map.of(
                "acceptanceId", round.acceptanceId(), "position", position,
                "conclusion", party.conclusion().name()));
        return latest(round, actor.tenantId(), party.participantKey());
    }

    @Transactional
    public AcceptanceParty recordPropertyTechnical(Long projectId, RecordAcceptanceParty command) {
        UserContext actor = support.requireSysActor(PROPERTY_ROLES, "仅物业项目负责人可专业共同签署");
        require(command != null, "验收命令不能为空");
        Context context = acceptanceContext(projectId, actor.tenantId());
        AcceptancePolicy policy = policy(context);
        requireRoleConfigured(policy, RepairAcceptancePartyRole.PROPERTY_TECHNICAL_COSIGNER);
        AcceptanceRound round = round(context);
        AcceptanceParty party = new AcceptanceParty(
                null, round.acceptanceId(), "PROPERTY_TECHNICAL:" + actor.userId(),
                RepairAcceptancePartyRole.PROPERTY_TECHNICAL_COSIGNER,
                null, null, actor.accountId(), actor.userId(),
                requiredText(command.participantName(), "participantName"),
                requiredText(command.participantOrganization(), "participantOrganization"), null,
                conclusion(command), text(command.opinion()), "ONLINE_SELF",
                evidenceForRole(context, policy, RepairAcceptancePartyRole.PROPERTY_TECHNICAL_COSIGNER,
                        command.evidenceAttachmentId()), null, actor.userId(), null);
        executionRepository.insertAcceptanceParty(actor.tenantId(), party);
        support.event(context, actor, "PROJECT_PROPERTY_TECHNICAL_ACCEPTANCE", Map.of(
                "acceptanceId", round.acceptanceId(), "conclusion", party.conclusion().name()));
        return latest(round, actor.tenantId(), party.participantKey());
    }

    @Transactional
    public AcceptanceParty recordThirdPartyTechnical(Long projectId, RecordAcceptanceParty command) {
        UserContext actor = support.requireSysActor(
                THIRD_PARTY_RECORD_ROLES, "仅物业经理或业委会可依据原件登记第三方专业签署");
        require(command != null, "验收命令不能为空");
        Context context = acceptanceContext(projectId, actor.tenantId());
        AcceptancePolicy policy = policy(context);
        requireRoleConfigured(policy, RepairAcceptancePartyRole.THIRD_PARTY_TECHNICAL_COSIGNER);
        String participantName = requiredText(command.participantName(), "participantName");
        String organization = requiredText(command.participantOrganization(), "participantOrganization");
        AcceptanceRound round = round(context);
        AcceptanceParty party = new AcceptanceParty(
                null, round.acceptanceId(), "THIRD_PARTY:" + sha256(participantName + "|" + organization),
                RepairAcceptancePartyRole.THIRD_PARTY_TECHNICAL_COSIGNER,
                null, null, null, null, participantName, organization, null,
                conclusion(command), text(command.opinion()), "OFFLINE_RECORDED",
                evidenceForRole(context, policy, RepairAcceptancePartyRole.THIRD_PARTY_TECHNICAL_COSIGNER,
                        command.evidenceAttachmentId()), null, actor.userId(), null);
        executionRepository.insertAcceptanceParty(actor.tenantId(), party);
        support.event(context, actor, "PROJECT_THIRD_PARTY_TECHNICAL_ACCEPTANCE", Map.of(
                "acceptanceId", round.acceptanceId(), "organization", organization,
                "conclusion", party.conclusion().name()));
        return latest(round, actor.tenantId(), party.participantKey());
    }

    @Transactional
    public AcceptanceParty sealCommunityAcceptance(Long projectId, SealCommunityAcceptance command) {
        UserContext actor = support.requireSysActor(COMMITTEE_ROLES, "仅业委会成员可办理验收用印");
        if (!actor.hasPermission("committee:seal:use")) {
            throw support.forbidden("当前业委会成员未获用印权限");
        }
        Context context = acceptanceContext(projectId, actor.tenantId());
        AcceptancePolicy policy = policy(context);
        requireRoleConfigured(policy, RepairAcceptancePartyRole.COMMITTEE_SEAL_OPERATOR);
        require(command != null, "验收用印命令不能为空");
        AcceptanceRound round = round(context);
        Attachment source = support.attachment(context, command.sourceAttachmentId(), "验收签前文件");
        Attachment sealed = support.attachment(context, command.sealedAttachmentId(), "验收盖章文件");
        Long usageId = governanceRepository.insertProjectSealUsage(
                actor.tenantId(), context.project().projectId(), context.project().projectName() + "竣工验收",
                source.attachmentId(), sealed.attachmentId(), source.sha256(), sealed.sha256(),
                actor.userId(), text(command.remark()));
        AcceptanceParty party = new AcceptanceParty(
                null, round.acceptanceId(), "COMMITTEE_SEAL:" + usageId,
                RepairAcceptancePartyRole.COMMITTEE_SEAL_OPERATOR,
                null, null, actor.accountId(), actor.userId(), "业委会用印经办人",
                null, null, RepairAcceptanceConclusion.PASSED, null,
                "SEAL_USAGE", sealed.attachmentId(), usageId, actor.userId(), null);
        executionRepository.insertAcceptanceParty(actor.tenantId(), party);
        support.event(context, actor, "PROJECT_ACCEPTANCE_SEALED", Map.of(
                "acceptanceId", round.acceptanceId(), "sealUsageId", usageId));
        return latest(round, actor.tenantId(), party.participantKey());
    }

    @Transactional
    public AcceptanceRound finalizeAcceptance(Long projectId, FinalizeAcceptance command) {
        require(command != null && command.expectedProjectVersion() != null,
                "expectedProjectVersion 必填");
        UserContext actor = support.requireActor();
        if (!actor.isSysUser() || actor.userId() == null) {
            throw support.forbidden("管理端工作身份才能完成验收定案");
        }
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.PENDING_ACCEPTANCE);
        if (!context.project().version().equals(command.expectedProjectVersion())) {
            throw support.conflict("项目版本已变化，请刷新后重试");
        }
        AcceptancePolicy policy = policy(context);
        assertFinalizer(actor, context, policy);
        AcceptanceRound round = round(context);
        Long resultAttachmentId = support.attachment(
                context, command.resultAttachmentId(), "验收定案文件").attachmentId();
        AcceptanceSummary summary = executionRepository.summarizeAcceptance(
                round.acceptanceId(), actor.tenantId());
        RepairAcceptanceDecision decision = ACCEPTANCE_POLICY.evaluate(policy, summary);
        if (decision.outcome() == RepairAcceptanceDecision.Outcome.INCOMPLETE) {
            throw support.invalid(decision.reason());
        }
        if (decision.outcome() == RepairAcceptanceDecision.Outcome.RECTIFICATION_REQUIRED) {
            completeRound(round, actor, "RECTIFICATION_REQUIRED", resultAttachmentId, text(command.remark()));
            executionRepository.invalidateVerifiedSettlement(
                    round.settlementId(), projectId, actor.tenantId(), "验收要求整改，原结算失效");
            executionRepository.supersedeAcceptancePolicy(
                    policy.policyId(), projectId, actor.tenantId());
            support.advance(context, Status.IN_PROGRESS);
        } else {
            completeRound(round, actor, "PASSED", resultAttachmentId, text(command.remark()));
            support.advance(context, Status.COMPLETED);
        }
        support.event(context, actor, "PROJECT_ACCEPTANCE_FINALIZED", Map.of(
                "acceptanceId", round.acceptanceId(), "outcome", decision.outcome().name()));
        return new AcceptanceRound(
                round.acceptanceId(), round.projectId(), round.policyId(), round.settlementId(),
                round.tenantId(), round.roundNo(),
                decision.outcome() == RepairAcceptanceDecision.Outcome.PASSED
                        ? com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceStatus.PASSED
                        : com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceStatus.RECTIFICATION_REQUIRED,
                resultAttachmentId, round.submittedAt(), LocalDateTime.now());
    }

    @Transactional
    public CompletionDisclosure createCompletionDisclosure(
            Long projectId, CreateCompletionDisclosure command) {
        UserContext actor = support.requireSysActor(PROPERTY_ROLES, "仅物业项目人员可归档完工告示和维修报告");
        require(command != null && command.expectedProjectVersion() != null,
                "expectedProjectVersion 必填");
        Context context = support.loadForUpdate(projectId, actor.tenantId(), Status.COMPLETED);
        if (!context.project().version().equals(command.expectedProjectVersion())) {
            throw support.conflict("项目版本已变化，请刷新后重试");
        }
        if (executionRepository.findCompletionDisclosure(projectId, actor.tenantId()).isPresent()) {
            throw support.conflict("完工披露已经归档");
        }
        require(command.noticeStartDate() != null && command.noticeEndDate() != null
                        && !command.noticeEndDate().isBefore(command.noticeStartDate()),
                "完工告示开始和结束日期不合法");
        LocalDate warrantyStart = command.warrantyStartDate();
        if (warrantyStart == null || warrantyStart.isAfter(LocalDate.now())) {
            throw support.invalid("warrantyStartDate 必填且不能晚于当前日期");
        }
        support.attachment(context, command.noticeAttachmentId(), "完工告示文件");
        support.attachment(context, command.propertyReportAttachmentId(), "物业书面维修报告");
        support.attachments(context, command.sitePhotoAttachmentIds(), "完工告示现场照片");
        CompletionDisclosure disclosure = executionRepository.insertCompletionDisclosure(new CompletionDisclosure(
                null, projectId, actor.tenantId(), command.noticeStartDate(), command.noticeEndDate(),
                requiredText(command.postingScope(), "postingScope"), command.noticeAttachmentId(),
                command.propertyReportAttachmentId(), command.sitePhotoAttachmentIds(), warrantyStart,
                warrantyStart.plusDays(context.plan().warrantyDays()), actor.userId(), null));
        executionRepository.insertCompletionDisclosurePhotos(
                disclosure.disclosureId(), actor.tenantId(), command.sitePhotoAttachmentIds());
        support.advance(context, Status.WARRANTY);
        support.event(context, actor, "PROJECT_COMPLETION_DISCLOSED", Map.of(
                "disclosureId", disclosure.disclosureId(),
                "warrantyEndDate", disclosure.warrantyEndDate().toString()));
        return executionRepository.findCompletionDisclosure(projectId, actor.tenantId()).orElseThrow();
    }

    private Context acceptanceContext(Long projectId, Long tenantId) {
        return support.loadForUpdate(projectId, tenantId, Status.PENDING_ACCEPTANCE);
    }

    private AcceptancePolicy policy(Context context) {
        return executionRepository.findAcceptancePolicy(
                        context.project().projectId(), context.project().tenantId())
                .orElseThrow(() -> support.conflict("项目没有锁定验收规则"));
    }

    private AcceptanceRound round(Context context) {
        return executionRepository.findCollectingAcceptance(
                        context.project().projectId(), context.project().tenantId())
                .orElseThrow(() -> support.conflict("项目没有进行中的验收轮次"));
    }

    private AcceptanceParty latest(AcceptanceRound round, Long tenantId, String participantKey) {
        return executionRepository.listAcceptanceParties(round.acceptanceId(), tenantId).stream()
                .filter(party -> party.participantKey().equals(participantKey))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private void assertFinalizer(UserContext actor, Context context, AcceptancePolicy policy) {
        Set<RepairAcceptancePartyRole> actorRoles = new java.util.LinkedHashSet<>();
        if (BUILDING_LEADER_ROLES.contains(actor.roleKey())) {
            if (context.project().buildingId() == null
                    || (!actor.authorizedBuildingIds().contains(context.project().buildingId())
                    && actor.authorizedBuildingScopes().stream().noneMatch(scope ->
                    scope.tenantId().equals(context.project().tenantId())
                            && scope.buildingId().equals(context.project().buildingId())))) {
                throw support.forbidden("当前楼组长不负责本项目楼栋");
            }
            actorRoles.add(RepairAcceptancePartyRole.BUILDING_LEADER);
        }
        if (COMMITTEE_ROLES.contains(actor.roleKey())) {
            String position = workOrderRepository.findActiveCommitteePosition(actor.tenantId(), actor.userId())
                    .orElse(null);
            if (Set.of("DIRECTOR", "VICE_DIRECTOR").contains(position)) {
                actorRoles.add(RepairAcceptancePartyRole.COMMITTEE_EXECUTIVE_APPROVER);
            }
            if (actor.hasPermission("committee:seal:use")) {
                actorRoles.add(RepairAcceptancePartyRole.COMMITTEE_SEAL_OPERATOR);
            }
        }
        if (PROPERTY_ROLES.contains(actor.roleKey())) {
            actorRoles.add(RepairAcceptancePartyRole.PROPERTY_TECHNICAL_COSIGNER);
        }
        if (actorRoles.stream().noneMatch(policy.finalizerRoles()::contains)) {
            throw support.forbidden("当前工作身份不是实施方案约定的验收结论确认人");
        }
    }

    private void requireRoleConfigured(AcceptancePolicy policy, RepairAcceptancePartyRole role) {
        boolean configured = policy.requirements().stream()
                .map(AcceptanceRequirement::eligibleRoles)
                .anyMatch(roles -> roles.contains(role));
        if (!configured) {
            throw support.forbidden("本实施方案未安排当前身份参与验收");
        }
    }

    private Long evidenceForRole(
            Context context, AcceptancePolicy policy,
            RepairAcceptancePartyRole role, Long attachmentId) {
        boolean required = policy.requirements().stream()
                .filter(requirement -> requirement.eligibleRoles().contains(role))
                .anyMatch(AcceptanceRequirement::evidenceRequired);
        return required ? requiredEvidence(context, attachmentId) : onlineEvidence(context, attachmentId);
    }

    private void completeRound(
            AcceptanceRound round, UserContext actor, String status,
            Long resultAttachmentId, String remark) {
        if (executionRepository.completeAcceptance(
                round.acceptanceId(), actor.tenantId(), status, resultAttachmentId,
                actor.userId(), remark) != 1) {
            throw support.conflict("验收轮次已被其他人定案，请刷新后重试");
        }
    }

    private RepairAcceptanceConclusion conclusion(RecordAcceptanceParty command) {
        require(command != null, "验收命令不能为空");
        return conclusion(command.conclusion(), command.opinion());
    }

    private RepairAcceptanceConclusion conclusion(
            RepairAcceptanceConclusion conclusion, String opinion) {
        if (conclusion == null) {
            throw support.invalid("conclusion 必填");
        }
        if (conclusion == RepairAcceptanceConclusion.RECTIFICATION_REQUIRED && text(opinion) == null) {
            throw support.invalid("要求整改必须填写具体问题");
        }
        return conclusion;
    }

    private Long requiredEvidence(Context context, Long attachmentId) {
        return support.attachment(context, attachmentId, "验收签署证据").attachmentId();
    }

    private Long onlineEvidence(Context context, Long attachmentId) {
        return attachmentId == null
                ? null
                : support.attachment(context, attachmentId, "验收补充证据").attachmentId();
    }

    private String requiredText(String value, String field) {
        String normalized = text(value);
        if (normalized == null) {
            throw support.invalid(field + " 必填");
        }
        return normalized;
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw support.invalid(message);
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
}
