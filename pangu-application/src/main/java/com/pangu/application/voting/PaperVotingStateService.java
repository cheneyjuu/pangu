// 关联业务：在事务内保存纸质送达、纸票和录入复核状态，不把未复核材料写成有效票。
package com.pangu.application.voting;

import com.pangu.domain.model.voting.PaperBallot;
import com.pangu.domain.model.voting.PaperBallotEntry;
import com.pangu.domain.model.voting.PaperBallotOutcome;
import com.pangu.domain.model.voting.PaperVotingDelivery;
import com.pangu.domain.model.voting.OnlinePaperAssistanceRequest;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VotingDeliveryRecord;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.repository.PaperVotingRepository;
import com.pangu.domain.repository.OnlineVotingRepository;
import com.pangu.domain.repository.VotingExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.pangu.application.voting.PaperVotingException.Reason.CONCURRENT_MODIFICATION;
import static com.pangu.application.voting.PaperVotingException.Reason.DUPLICATE;
import static com.pangu.application.voting.PaperVotingException.Reason.INVALID_ARGUMENT;
import static com.pangu.application.voting.PaperVotingException.Reason.INVALID_STATUS;
import static com.pangu.application.voting.PaperVotingException.Reason.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class PaperVotingStateService {

    private final PaperVotingRepository paperVotingRepository;
    private final OnlineVotingRepository onlineVotingRepository;
    private final VotingExecutionRepository votingExecutionRepository;
    private final VotingExecutionService votingExecutionService;

    @Transactional
    public PaperVotingDelivery registerDelivery(PaperVotingService.RegisterDeliveryCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        VotingExecutionPackage ballotPackage = requirePaperPackage(command.packageId(), command.tenantId(), false);
        VotingElectorateSnapshot.Item electorate = requireElectorateItem(
                command.packageId(), command.tenantId(), command.opid());
        OnlinePaperAssistanceRequest assistance = requirePaperRoute(ballotPackage, electorate);
        Instant deliveredAt = requireInstant(command.deliveredAt(), "送达时间");
        if (!deliveredAt.isBefore(ballotPackage.getVoteEndAt())) {
            throw new PaperVotingException(INVALID_ARGUMENT, "送达时间必须早于本次表决截止时间");
        }
        PaperVotingDelivery delivery = new PaperVotingDelivery(
                null, command.packageId(), electorate.snapshotItemId(), command.tenantId(),
                electorate.representativeOpid(), requireText(command.recipientName(), "接收人"),
                requireText(command.deliveryMethod(), "送达方式"),
                requireText(command.evidenceSourceType(), "送达证据来源"),
                requirePositive(command.evidenceSourceId(), "送达证据材料"),
                requireText(command.evidenceHash(), "送达证据摘要"),
                requirePositive(command.deliveredByUserId(), "送达经办人"), deliveredAt,
                PaperVotingDelivery.Status.PENDING_REVIEW,
                null, null, null, null, null, null, 0L);
        try {
            PaperVotingDelivery inserted = paperVotingRepository.insertDelivery(delivery);
            if (assistance != null && onlineVotingRepository.fulfillPaperAssistanceRequest(
                    assistance.requestId(), assistance.tenantId(), inserted.paperDeliveryId(), deliveredAt) != 1) {
                throw new PaperVotingException(
                        CONCURRENT_MODIFICATION, "纸质办理申请状态已变化，请刷新后重试");
            }
            return inserted;
        } catch (DataIntegrityViolationException ex) {
            throw new PaperVotingException(DUPLICATE, "该送达证据已经登记，不能重复提交", ex);
        }
    }

    @Transactional
    public PaperVotingDelivery reviewDelivery(PaperVotingService.ReviewDeliveryCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        requirePaperPackage(command.packageId(), command.tenantId(), false);
        PaperVotingDelivery delivery = paperVotingRepository.findDeliveryForUpdate(
                        command.paperDeliveryId(), command.packageId(), command.tenantId())
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "纸质送达登记不存在"));
        if (delivery.status() != PaperVotingDelivery.Status.PENDING_REVIEW) {
            throw new PaperVotingException(INVALID_STATUS, "该送达登记已经完成核对");
        }
        Long reviewer = requirePositive(command.reviewedByUserId(), "核对人");
        if (reviewer.equals(delivery.deliveredByUserId())) {
            throw new PaperVotingException(INVALID_ARGUMENT, "送达登记人不能核对自己的送达记录");
        }
        Instant reviewedAt = requireInstant(command.reviewedAt(), "核对时间");
        int updated;
        if (command.decision() == PaperVotingService.ReviewDecision.REJECT) {
            String note = requireText(command.reviewNote(), "不通过原因");
            updated = paperVotingRepository.rejectDelivery(
                    delivery.paperDeliveryId(), delivery.tenantId(),
                    reviewer, reviewedAt, note, delivery.version());
        } else if (command.decision() == PaperVotingService.ReviewDecision.CONFIRM) {
            VotingDeliveryRecord unified;
            try {
                unified = votingExecutionService.recordDelivery(new VotingExecutionService.RecordDeliveryCommand(
                        delivery.packageId(), delivery.tenantId(), delivery.opid(), VoteChannel.PAPER,
                        delivery.deliveryMethod(), delivery.evidenceHash(), delivery.deliveredByUserId(),
                        delivery.deliveredAt()));
            } catch (VotingExecutionService.VotingExecutionException ex) {
                throw translate(ex);
            }
            updated = paperVotingRepository.confirmDelivery(
                    delivery.paperDeliveryId(), delivery.tenantId(),
                    reviewer, reviewedAt,
                    unified.deliveryId(), delivery.version());
        } else {
            throw new PaperVotingException(INVALID_ARGUMENT, "请选择送达核对结论");
        }
        if (updated != 1) {
            throw new PaperVotingException(CONCURRENT_MODIFICATION, "送达登记已被其他人员处理，请刷新后重试");
        }
        return paperVotingRepository.findDelivery(
                        delivery.paperDeliveryId(), delivery.packageId(), delivery.tenantId())
                .orElseThrow();
    }

    @Transactional
    public PaperBallot registerBallot(PaperVotingService.RegisterBallotCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        VotingExecutionPackage ballotPackage = requirePaperPackage(command.packageId(), command.tenantId(), true);
        VotingElectorateSnapshot.Item electorate = requireElectorateItem(
                command.packageId(), command.tenantId(), command.opid());
        requirePaperBallotRoute(ballotPackage, electorate);
        Instant receivedAt = requireInstant(command.receivedAt(), "回收时间");
        if (receivedAt.isBefore(ballotPackage.getVoteStartAt()) || !receivedAt.isBefore(ballotPackage.getVoteEndAt())) {
            throw new PaperVotingException(INVALID_ARGUMENT, "纸票回收时间必须在本次表决时间内");
        }
        PaperBallot ballot = new PaperBallot(
                null, command.packageId(), electorate.snapshotItemId(), command.tenantId(),
                electorate.representativeOpid(), requireText(command.ballotNumber(), "表决票编号"),
                requireSha256(command.templateHash(), "纸质表决票模板摘要"),
                requireText(command.materialSourceType(), "纸票原件来源"),
                requirePositive(command.materialSourceId(), "纸票原件材料"),
                requireText(command.materialHash(), "纸票原件摘要"),
                requirePositive(command.receivedByUserId(), "回收经办人"), receivedAt,
                PaperBallot.Status.RECEIVED, null, null, null, null, null, 0L);
        try {
            return paperVotingRepository.insertBallot(ballot);
        } catch (DataIntegrityViolationException ex) {
            throw new PaperVotingException(DUPLICATE, "表决票编号或纸票原件已经登记", ex);
        }
    }

    @Transactional
    public PaperBallot voidBallot(PaperVotingService.VoidBallotCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        requirePaperPackage(command.packageId(), command.tenantId(), true);
        PaperBallot ballot = paperVotingRepository.findBallotForUpdate(
                        command.paperBallotId(), command.packageId(), command.tenantId())
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "回收纸票不存在"));
        if (ballot.status() != PaperBallot.Status.RECEIVED) {
            throw new PaperVotingException(INVALID_STATUS, "只有尚未提交录入的纸票可以作废");
        }
        if (paperVotingRepository.voidBallot(
                ballot.paperBallotId(), ballot.tenantId(),
                requirePositive(command.voidedByUserId(), "作废经办人"),
                requireInstant(command.voidedAt(), "作废时间"),
                requireText(command.reason(), "作废原因"), ballot.version()) != 1) {
            throw new PaperVotingException(CONCURRENT_MODIFICATION, "纸票已被其他人员处理，请刷新后重试");
        }
        return paperVotingRepository.findBallot(ballot.paperBallotId(), ballot.packageId(), ballot.tenantId())
                .orElseThrow();
    }

    @Transactional
    public PaperBallotEntry submitEntry(PaperVotingService.SubmitEntryCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        requirePaperPackage(command.packageId(), command.tenantId(), true);
        PaperBallot ballot = paperVotingRepository.findBallotForUpdate(
                        command.paperBallotId(), command.packageId(), command.tenantId())
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "回收纸票不存在"));
        if (ballot.status() != PaperBallot.Status.RECEIVED && ballot.status() != PaperBallot.Status.IN_ENTRY) {
            throw new PaperVotingException(INVALID_STATUS, "该纸票当前不能录入");
        }
        if (!ballot.templateHash().equals(requireSha256(
                command.templateHashGuard(), "本次锁定纸质表决票模板摘要"))) {
            throw new PaperVotingException(INVALID_STATUS, "纸票模板与本次锁定模板不一致");
        }
        List<Long> subjectIds = votingExecutionRepository.listSubjectIds(command.packageId(), command.tenantId());
        List<PaperBallotEntry.Item> items = requireCompleteItems(command.items(), subjectIds);
        PaperBallotEntry entry = new PaperBallotEntry(
                null, ballot.paperBallotId(), ballot.tenantId(),
                paperVotingRepository.nextEntryVersion(ballot.paperBallotId(), ballot.tenantId()),
                PaperBallotEntry.Status.PENDING_REVIEW,
                requirePositive(command.enteredByUserId(), "录入人"),
                requireInstant(command.enteredAt(), "录入时间"),
                null, null, null, items);
        try {
            PaperBallotEntry inserted = paperVotingRepository.insertEntry(entry);
            if (paperVotingRepository.markBallotInEntry(
                    ballot.paperBallotId(), ballot.tenantId(), ballot.version()) != 1) {
                throw new PaperVotingException(CONCURRENT_MODIFICATION, "纸票已被其他人员处理，请刷新后重试");
            }
            return inserted;
        } catch (DataIntegrityViolationException ex) {
            throw new PaperVotingException(DUPLICATE, "该纸票已有待复核录入，请先完成核对", ex);
        }
    }

    @Transactional
    public ReviewPreparation reviewEntry(PaperVotingService.ReviewEntryCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        requirePaperPackage(command.packageId(), command.tenantId(), true);
        PaperBallot ballot = paperVotingRepository.findBallotForUpdate(
                        command.paperBallotId(), command.packageId(), command.tenantId())
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "回收纸票不存在"));
        PaperBallotEntry entry = paperVotingRepository.findEntryForUpdate(
                        command.entryId(), ballot.paperBallotId(), command.tenantId())
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "纸票录入版本不存在"));
        if (entry.status() == PaperBallotEntry.Status.CONFIRMED
                && command.decision() == PaperVotingService.ReviewDecision.CONFIRM
                && Objects.equals(entry.reviewedByUserId(), command.reviewedByUserId())) {
            return new ReviewPreparation(ballot, entry);
        }
        if (entry.status() != PaperBallotEntry.Status.PENDING_REVIEW) {
            throw new PaperVotingException(INVALID_STATUS, "该纸票录入已经完成复核");
        }
        Long reviewer = requirePositive(command.reviewedByUserId(), "复核人");
        if (reviewer.equals(entry.enteredByUserId())) {
            throw new PaperVotingException(INVALID_ARGUMENT, "纸票录入人不能复核自己的录入");
        }
        Instant reviewedAt = requireInstant(command.reviewedAt(), "复核时间");
        int updated;
        if (command.decision() == PaperVotingService.ReviewDecision.REJECT) {
            updated = paperVotingRepository.rejectEntry(
                    entry.entryId(), entry.tenantId(), reviewer, reviewedAt,
                    requireText(command.reviewNote(), "退回原因"));
        } else if (command.decision() == PaperVotingService.ReviewDecision.CONFIRM) {
            updated = paperVotingRepository.confirmEntry(entry.entryId(), entry.tenantId(), reviewer, reviewedAt);
        } else {
            throw new PaperVotingException(INVALID_ARGUMENT, "请选择纸票复核结论");
        }
        if (updated != 1) {
            throw new PaperVotingException(CONCURRENT_MODIFICATION, "纸票录入已被其他人员处理，请刷新后重试");
        }
        PaperBallotEntry reviewed = paperVotingRepository.findEntry(
                        entry.entryId(), entry.paperBallotId(), entry.tenantId())
                .orElseThrow();
        return new ReviewPreparation(ballot, reviewed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOutcome(PaperBallotOutcome outcome, Long packageId, Long tenantId) {
        PaperBallot ballot = paperVotingRepository.findBallotForUpdate(
                        outcome.paperBallotId(), packageId, tenantId)
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "回收纸票不存在"));
        if (ballot.status() != PaperBallot.Status.IN_ENTRY) {
            throw new PaperVotingException(INVALID_STATUS, "纸票不在复核处理状态");
        }
        paperVotingRepository.insertOutcome(outcome);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaperVotingService.BallotReviewResult completeBallot(Long packageId,
                                                                Long paperBallotId,
                                                                Long tenantId,
                                                                Long entryId) {
        PaperBallot ballot = paperVotingRepository.findBallotForUpdate(paperBallotId, packageId, tenantId)
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "回收纸票不存在"));
        PaperBallotEntry entry = paperVotingRepository.findEntry(entryId, paperBallotId, tenantId)
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "纸票录入版本不存在"));
        List<PaperBallotOutcome> outcomes = paperVotingRepository.listOutcomes(paperBallotId, tenantId);
        int subjectCount = votingExecutionRepository.listSubjectIds(packageId, tenantId).size();
        if (outcomes.size() != subjectCount) {
            throw new PaperVotingException(INVALID_STATUS, "纸票仍有表决事项尚未完成处理");
        }
        if (ballot.status() == PaperBallot.Status.IN_ENTRY
                && paperVotingRepository.markBallotCompleted(paperBallotId, tenantId) != 1) {
            throw new PaperVotingException(CONCURRENT_MODIFICATION, "纸票完成状态已被其他人员更新");
        }
        PaperBallot completed = paperVotingRepository.findBallot(paperBallotId, packageId, tenantId).orElseThrow();
        return new PaperVotingService.BallotReviewResult(completed, entry, outcomes);
    }

    public PaperVotingService.Workbench getWorkbench(Long packageId, Long tenantId) {
        requirePaperPackage(packageId, tenantId, false);
        List<PaperVotingService.BallotWorkbenchItem> ballots = paperVotingRepository.listBallots(packageId, tenantId)
                .stream()
                .map(ballot -> new PaperVotingService.BallotWorkbenchItem(
                        ballot,
                        paperVotingRepository.findLatestEntry(ballot.paperBallotId(), tenantId).orElse(null),
                        paperVotingRepository.listOutcomes(ballot.paperBallotId(), tenantId)))
                .toList();
        return new PaperVotingService.Workbench(
                paperVotingRepository.listDeliveries(packageId, tenantId), ballots);
    }

    private VotingExecutionPackage requirePaperPackage(Long packageId, Long tenantId, boolean votingRequired) {
        VotingExecutionPackage ballotPackage = votingExecutionRepository.findPackage(packageId, tenantId)
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "正式表决包不存在"));
        if (!ballotPackage.accepts(VoteChannel.PAPER)) {
            throw new PaperVotingException(INVALID_STATUS, "本次表决未开放纸质办理");
        }
        if (votingRequired && ballotPackage.getStatus() != VotingExecutionPackage.Status.VOTING) {
            throw new PaperVotingException(INVALID_STATUS, "当前不在纸质表决收票阶段");
        }
        if (!votingRequired && ballotPackage.getStatus() != VotingExecutionPackage.Status.FROZEN
                && ballotPackage.getStatus() != VotingExecutionPackage.Status.VOTING) {
            throw new PaperVotingException(INVALID_STATUS, "表决包尚未锁定或已经结束");
        }
        return ballotPackage;
    }

    private VotingElectorateSnapshot.Item requireElectorateItem(Long packageId, Long tenantId, Long opid) {
        return votingExecutionRepository.findElectorateItem(packageId, tenantId, opid)
                .orElseThrow(() -> new PaperVotingException(NOT_FOUND, "该专有部分不在本次冻结表决人名册中"));
    }

    /** 互联网为主的表决只为已经提出请求的专有部分办理纸质送达。 */
    private OnlinePaperAssistanceRequest requirePaperRoute(
            VotingExecutionPackage ballotPackage, VotingElectorateSnapshot.Item electorate) {
        if (ballotPackage.getCollectionMode()
                != VotingExecutionPackage.CollectionMode.ONLINE_WITH_PAPER_ASSISTANCE) {
            return null;
        }
        OnlinePaperAssistanceRequest assistance = onlineVotingRepository.findPaperAssistanceRequest(
                        ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId())
                .orElseThrow(() -> new PaperVotingException(
                        INVALID_STATUS, "该业主尚未申请纸质办理，不能登记纸质送达"));
        if (assistance.status() != OnlinePaperAssistanceRequest.Status.REQUESTED) {
            throw new PaperVotingException(INVALID_STATUS, "该纸质办理申请已经处理或撤回");
        }
        return assistance;
    }

    private void requirePaperBallotRoute(
            VotingExecutionPackage ballotPackage, VotingElectorateSnapshot.Item electorate) {
        if (ballotPackage.getCollectionMode()
                != VotingExecutionPackage.CollectionMode.ONLINE_WITH_PAPER_ASSISTANCE) {
            return;
        }
        OnlinePaperAssistanceRequest assistance = onlineVotingRepository.findPaperAssistanceRequest(
                        ballotPackage.getPackageId(), electorate.snapshotItemId(), ballotPackage.getTenantId())
                .orElseThrow(() -> new PaperVotingException(
                        INVALID_STATUS, "该业主尚未申请纸质办理，不能登记纸质表决票"));
        if (assistance.status() != OnlinePaperAssistanceRequest.Status.FULFILLED) {
            throw new PaperVotingException(INVALID_STATUS, "请先完成该业主的纸质材料送达登记");
        }
    }

    private List<PaperBallotEntry.Item> requireCompleteItems(List<PaperBallotEntry.Item> items,
                                                              List<Long> subjectIds) {
        if (items == null || items.isEmpty()) {
            throw new PaperVotingException(INVALID_ARGUMENT, "请录入纸票中的全部表决事项");
        }
        Set<Long> supplied = new HashSet<>();
        for (PaperBallotEntry.Item item : items) {
            if (!supplied.add(item.subjectId())) {
                throw new PaperVotingException(INVALID_ARGUMENT, "同一表决事项不能重复录入");
            }
        }
        if (!supplied.equals(Set.copyOf(subjectIds))) {
            throw new PaperVotingException(INVALID_ARGUMENT, "纸票录入必须覆盖本次表决包的全部事项");
        }
        return List.copyOf(items);
    }

    private PaperVotingException translate(VotingExecutionService.VotingExecutionException exception) {
        PaperVotingException.Reason reason = switch (exception.getReason()) {
            case NOT_FOUND, ELECTORATE_NOT_FOUND -> NOT_FOUND;
            case DUPLICATE_BALLOT -> DUPLICATE;
            case CONCURRENT_MODIFICATION -> CONCURRENT_MODIFICATION;
            case INVALID_STATUS, CHANNEL_NOT_ALLOWED, DELIVERY_REQUIRED -> INVALID_STATUS;
            case INVALID_COMMAND -> INVALID_ARGUMENT;
        };
        return new PaperVotingException(reason, exception.getMessage(), exception);
    }

    private Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new PaperVotingException(INVALID_ARGUMENT, field + "不能为空");
        }
        return value;
    }

    private Instant requireInstant(Instant value, String field) {
        if (value == null) {
            throw new PaperVotingException(INVALID_ARGUMENT, field + "不能为空");
        }
        return value;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PaperVotingException(INVALID_ARGUMENT, field + "不能为空");
        }
        return value.trim();
    }

    private String requireSha256(String value, String field) {
        String normalized = requireText(value, field).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new PaperVotingException(INVALID_ARGUMENT, field + "格式不正确");
        }
        return normalized;
    }

    public record ReviewPreparation(PaperBallot ballot, PaperBallotEntry entry) {
    }
}
