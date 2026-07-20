// 关联业务：将维修工程的纸质办理材料和统一表决包连接起来，不复制纸票、线上票或计票逻辑。
package com.pangu.application.repair;

import com.pangu.application.voting.OnlineVotingService;
import com.pangu.application.voting.PaperVotingException;
import com.pangu.application.voting.PaperVotingService;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProjectVoting;
import com.pangu.domain.model.voting.PaperBallot;
import com.pangu.domain.model.voting.PaperBallotEntry;
import com.pangu.domain.model.voting.PaperVotingDelivery;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.repository.OwnersAssemblyRuleRepository;
import com.pangu.domain.repository.PropertyBindingRepository;
import com.pangu.domain.repository.RepairProjectRepository;
import com.pangu.domain.repository.RepairProjectVotingRepository;
import com.pangu.domain.repository.VotingExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.INVALID_STATUS;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.NOT_FOUND;
import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.PARAM_INVALID;

/**
 * 维修表决渠道适配器。
 *
 * <p>纸质送达凭证和回收原件必须来自当前维修项目的原始附件；正式选票模板由冻结表决包生成，
 * 因而统一使用表决包摘要作模板版本守卫。个人线上选择不会出现在管理端进度中。
 */
@Service
@RequiredArgsConstructor
public class RepairProjectVotingChannelService {

    private final RepairProjectRepository projectRepository;
    private final RepairProjectVotingRepository repairVotingRepository;
    private final OwnersAssemblyRuleRepository ruleRepository;
    private final PropertyBindingRepository propertyBindingRepository;
    private final VotingExecutionRepository votingExecutionRepository;
    private final PaperVotingService paperVotingService;
    private final OnlineVotingService onlineVotingService;
    private final UserContextHolder userContextHolder;

    @Transactional
    public PaperVotingDelivery recordDelivery(Long projectId, RecordDeliveryCommand command) {
        Context context = context(projectId);
        requirePaperOpen(context.executionPackage());
        if (command == null || command.opid() == null || command.deliveryMethod() == null
                || command.evidenceAttachmentId() == null || command.deliveredAt() == null) {
            throw invalid("专有部分、送达方式、送达凭证和送达时间均为必填项");
        }
        requireAllowedDeliveryMethod(context, command.deliveryMethod());
        RepairProject.Attachment evidence = attachment(context, command.evidenceAttachmentId(), "送达凭证");
        try {
            return paperVotingService.registerDelivery(new PaperVotingService.RegisterDeliveryCommand(
                    context.executionPackage().getPackageId(), context.project().tenantId(), command.opid(),
                    command.proxyAuthorizationId(),
                    requireText(command.recipientName(), "签收人"), command.deliveryMethod().name(),
                    "REPAIR_PROJECT_ATTACHMENT", evidence.attachmentId(), evidence.sha256(),
                    context.actor().userId(), command.deliveredAt()));
        } catch (PaperVotingException ex) {
            throw translate(ex);
        }
    }

    @Transactional
    public PaperVotingDelivery reviewDelivery(Long projectId, Long deliveryId, ReviewCommand command) {
        Context context = context(projectId);
        requirePaperOpen(context.executionPackage());
        requireReview(command);
        try {
            return paperVotingService.reviewDelivery(new PaperVotingService.ReviewDeliveryCommand(
                    context.executionPackage().getPackageId(), deliveryId, context.project().tenantId(),
                    command.decision(), command.reviewNote(), context.actor().userId(), Instant.now()));
        } catch (PaperVotingException ex) {
            throw translate(ex);
        }
    }

    @Transactional
    public PaperBallot registerBallot(Long projectId, RegisterBallotCommand command) {
        Context context = context(projectId);
        requirePaperOpen(context.executionPackage());
        if (command == null || command.opid() == null || command.attachmentId() == null
                || command.receivedAt() == null) {
            throw invalid("专有部分、选票编号、选票原件和回收时间均为必填项");
        }
        RepairProject.Attachment ballot = attachment(context, command.attachmentId(), "纸质表决票原件");
        try {
            return paperVotingService.registerBallot(new PaperVotingService.RegisterBallotCommand(
                    context.executionPackage().getPackageId(), context.project().tenantId(), command.opid(),
                    command.proxyAuthorizationId(),
                    requireText(command.ballotNumber(), "选票编号"), requireTemplateHash(context),
                    "REPAIR_PROJECT_ATTACHMENT", ballot.attachmentId(), ballot.sha256(),
                    context.actor().userId(), command.receivedAt()));
        } catch (PaperVotingException ex) {
            throw translate(ex);
        }
    }

    @Transactional
    public PaperBallot voidBallot(Long projectId, Long ballotId, String reason) {
        Context context = context(projectId);
        requirePaperOpen(context.executionPackage());
        try {
            return paperVotingService.voidBallot(new PaperVotingService.VoidBallotCommand(
                    context.executionPackage().getPackageId(), ballotId, context.project().tenantId(),
                    requireText(reason, "作废原因"), context.actor().userId(), Instant.now()));
        } catch (PaperVotingException ex) {
            throw translate(ex);
        }
    }

    @Transactional
    public PaperBallotEntry submitEntry(Long projectId, Long ballotId, List<PaperBallotEntry.Item> items) {
        Context context = context(projectId);
        requirePaperOpen(context.executionPackage());
        if (items == null || items.isEmpty()) {
            throw invalid("请录入纸质表决票上的全部事项");
        }
        try {
            return paperVotingService.submitEntry(new PaperVotingService.SubmitEntryCommand(
                    context.executionPackage().getPackageId(), ballotId, context.project().tenantId(),
                    requireTemplateHash(context), items, context.actor().userId(), Instant.now()));
        } catch (PaperVotingException ex) {
            throw translate(ex);
        }
    }

    /**
     * 纸票复核先提交原始复核状态，再由统一表决内核分事务写入有效票和逐事项结果。
     * 此处不能包裹外层事务，否则内核的独立事务会等待本请求自己持有的纸票行锁。
     */
    public PaperVotingService.BallotReviewResult reviewEntry(
            Long projectId, Long ballotId, Long entryId, ReviewCommand command) {
        Context context = context(projectId);
        requirePaperOpen(context.executionPackage());
        requireReview(command);
        try {
            return paperVotingService.reviewEntry(new PaperVotingService.ReviewEntryCommand(
                    context.executionPackage().getPackageId(), ballotId, entryId, context.project().tenantId(),
                    command.decision(), command.reviewNote(), context.actor().userId(), Instant.now()));
        } catch (PaperVotingException ex) {
            throw translate(ex);
        }
    }

    @Transactional(readOnly = true)
    public Workbench workbench(Long projectId) {
        Context context = context(projectId);
        RepairProject.Attachment template = projectRepository.findAttachment(
                        context.voting().paperBallotTemplateAttachmentId(),
                        context.project().projectId(), context.project().tenantId())
                .filter(item -> Objects.equals(item.sha256(), context.voting().paperBallotTemplateHash()))
                .orElseThrow(() -> conflict("本次表决锁定的纸质表决票模板无法核对"));
        PaperVotingService.Workbench paperWorkbench;
        try {
            paperWorkbench = paperVotingService.getWorkbench(
                    context.executionPackage().getPackageId(), context.project().tenantId());
        } catch (PaperVotingException ex) {
            throw translate(ex);
        }
        return new Workbench(
                votingExecutionRepository.findElectorateSnapshot(
                                context.executionPackage().getElectorateSnapshotId(), context.project().tenantId())
                        .map(snapshot -> snapshot.items().stream()
                                .map(item -> toElectorateWorkbenchItem(item, context.project().tenantId()))
                                .toList())
                        .orElseGet(List::of),
                paperWorkbench,
                onlineVotingService.managementProgress(
                        context.executionPackage().getPackageId(), context.project().tenantId()),
                onlineVotingService.listPaperAssistanceRequests(
                        context.executionPackage().getPackageId(), context.project().tenantId()),
                context.rule().configuration().validDeliveryMethods(),
                Boolean.TRUE.equals(context.rule().configuration().paperBallotSealRequired()),
                new PaperBallotTemplate(
                        template.attachmentId(), template.originalFileName(), template.contentType(),
                        template.fileSize(), template.sha256()),
                context.actor().userId());
    }

    private ElectorateWorkbenchItem toElectorateWorkbenchItem(
            VotingElectorateSnapshot.Item item, Long tenantId) {
        PropertyBindingRepository.Roster roster = propertyBindingRepository.findRosterById(item.rosterId());
        if (roster == null || !tenantId.equals(roster.tenantId())
                || !item.roomId().equals(roster.roomId())
                || !item.buildingId().equals(roster.buildingId())) {
            throw conflict("冻结房屋名册与当前建筑名册无法对应，请先核对名册");
        }
        return new ElectorateWorkbenchItem(
                item.snapshotItemId(), item.roomId(), item.buildingId(), item.certifiedArea(),
                item.representativeOpid(), roster.buildingName(), roster.unitName(), roster.roomName());
    }

    private Context context(Long projectId) {
        UserContext actor = requireManagementActor();
        RepairProject project = projectRepository.findProject(projectId, actor.tenantId())
                .orElseThrow(() -> notFound("维修工程项目不存在"));
        if (project.activePlanId() == null) {
            throw notFound("当前项目尚未准备相关业主表决");
        }
        RepairProjectVoting voting = repairVotingRepository.find(
                        project.projectId(), project.activePlanId(), project.tenantId())
                .orElseThrow(() -> notFound("当前项目尚未准备相关业主表决"));
        VotingExecutionPackage executionPackage = votingExecutionRepository.findPackage(
                        voting.executionPackageId(), project.tenantId())
                .filter(candidate -> candidate.getBusinessType()
                        == VotingExecutionPackage.BusinessType.REPAIR_PROJECT)
                .filter(candidate -> Objects.equals(candidate.getBusinessReferenceId(), voting.planId()))
                .orElseThrow(() -> conflict("维修项目与正式表决记录不一致"));
        OwnersAssemblyRule rule = ruleRepository.findById(voting.ruleId(), project.tenantId())
                .filter(candidate -> Objects.equals(
                        candidate.configurationSha256(), voting.ruleConfigurationHash()))
                .orElseThrow(() -> conflict("本次表决所依据的议事规则版本无法核对"));
        return new Context(actor, project, voting, executionPackage, rule);
    }

    private void requireAllowedDeliveryMethod(
            Context context, OwnersAssemblyRuleConfiguration.DeliveryMethod method) {
        if (!context.rule().configuration().validDeliveryMethods().contains(method)) {
            throw conflict("本次表决依据不认可所选送达方式");
        }
    }

    private String requireTemplateHash(Context context) {
        String hash = context.voting().paperBallotTemplateHash();
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw conflict("本次表决未锁定可核验的纸质表决票模板");
        }
        return hash;
    }

    private void requirePaperOpen(VotingExecutionPackage executionPackage) {
        if (executionPackage.getStatus() != VotingExecutionPackage.Status.VOTING
                || !executionPackage.accepts(com.pangu.domain.model.voting.VoteChannel.PAPER)) {
            throw conflict("当前尚未开始纸质表决办理，或本次未采用纸质方式");
        }
    }

    private RepairProject.Attachment attachment(Context context, Long attachmentId, String label) {
        return projectRepository.findAttachment(attachmentId, context.project().projectId(),
                        context.project().tenantId())
                .filter(item -> item.sha256() != null && item.sha256().matches("[0-9a-fA-F]{64}"))
                .orElseThrow(() -> invalid(label + "不存在或缺少文件校验信息"));
    }

    private void requireReview(ReviewCommand command) {
        if (command == null || command.decision() == null) {
            throw invalid("请选择核对结果");
        }
        if (command.decision() == PaperVotingService.ReviewDecision.REJECT
                && (command.reviewNote() == null || command.reviewNote().isBlank())) {
            throw invalid("退回时请填写原因");
        }
    }

    private UserContext requireManagementActor() {
        UserContext actor = userContextHolder.current();
        if (actor == null || !actor.isSysUser() || actor.userId() == null || actor.tenantId() == null) {
            throw new RepairWorkOrderApplicationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        return actor;
    }

    private RepairWorkOrderApplicationException translate(PaperVotingException failure) {
        RepairWorkOrderApplicationException.Reason reason = switch (failure.getReason()) {
            case NOT_FOUND -> NOT_FOUND;
            case INVALID_ARGUMENT -> PARAM_INVALID;
            case INVALID_STATUS, DUPLICATE, CONCURRENT_MODIFICATION -> INVALID_STATUS;
        };
        return new RepairWorkOrderApplicationException(reason, failure.getMessage(), failure);
    }

    private String requireText(String value, String label) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw invalid(label + "不能为空");
        }
        return normalized;
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

    public record RecordDeliveryCommand(
            Long opid,
            Long proxyAuthorizationId,
            String recipientName,
            OwnersAssemblyRuleConfiguration.DeliveryMethod deliveryMethod,
            Long evidenceAttachmentId,
            Instant deliveredAt
    ) {
    }

    public record RegisterBallotCommand(
            Long opid,
            Long proxyAuthorizationId,
            String ballotNumber,
            Long attachmentId,
            Instant receivedAt
    ) {
    }

    public record ReviewCommand(PaperVotingService.ReviewDecision decision, String reviewNote) {
    }

    public record Workbench(
            List<ElectorateWorkbenchItem> electorate,
            PaperVotingService.Workbench paper,
            OnlineVotingService.ManagementProgress online,
            List<com.pangu.domain.model.voting.OnlinePaperAssistanceRequest> paperAssistanceRequests,
            Set<OwnersAssemblyRuleConfiguration.DeliveryMethod> validDeliveryMethods,
            boolean paperBallotSealRequired,
            PaperBallotTemplate paperBallotTemplate,
            Long currentActorUserId
    ) {
        public Workbench {
            electorate = electorate == null ? List.of() : List.copyOf(electorate);
            paperAssistanceRequests = paperAssistanceRequests == null
                    ? List.of() : List.copyOf(paperAssistanceRequests);
            validDeliveryMethods = validDeliveryMethods == null ? Set.of() : Set.copyOf(validDeliveryMethods);
        }
    }

    public record ElectorateWorkbenchItem(
            Long snapshotItemId,
            Long roomId,
            Long buildingId,
            BigDecimal certifiedArea,
            Long representativeOpid,
            String buildingName,
            String unitName,
            String roomName
    ) {
    }

    public record PaperBallotTemplate(
            Long attachmentId,
            String originalFileName,
            String contentType,
            Long fileSize,
            String sha256
    ) {
    }

    private record Context(
            UserContext actor,
            RepairProject project,
            RepairProjectVoting voting,
            VotingExecutionPackage executionPackage,
            OwnersAssemblyRule rule
    ) {
    }
}
