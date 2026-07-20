// 关联业务：把已复核纸票逐事项转成计入、无效或重复材料结果，并保持跨渠道唯一有效票。
package com.pangu.application.voting;

import com.pangu.domain.model.voting.PaperBallot;
import com.pangu.domain.model.voting.PaperBallotEntry;
import com.pangu.domain.model.voting.PaperBallotOutcome;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VotingBallotRecord;
import com.pangu.domain.repository.VotingExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PaperVotingFinalizationService {

    private final VotingExecutionRepository votingExecutionRepository;
    private final PaperVoteCastService voteCastService;
    private final PaperVotingStateService stateService;

    public PaperVotingService.BallotReviewResult finalizeConfirmed(
            PaperVotingStateService.ReviewPreparation preparation,
            Long reviewedByUserId,
            Instant reviewedAt) {
        PaperBallot ballot = preparation.ballot();
        PaperBallotEntry entry = preparation.entry();
        for (PaperBallotEntry.Item item : entry.items()) {
            if (item.determination() == PaperBallotEntry.Determination.INVALID) {
                stateService.recordOutcome(new PaperBallotOutcome(
                        null, ballot.paperBallotId(), entry.entryId(), item.subjectId(),
                        PaperBallotOutcome.Status.INVALID, null, null,
                        invalidReason(item), reviewedAt), ballot.packageId(), ballot.tenantId());
                continue;
            }
            VotingBallotRecord existing = votingExecutionRepository.findActiveBallot(
                    item.subjectId(), ballot.electorateItemId(), ballot.tenantId()).orElse(null);
            if (existing != null && samePaper(ballot, existing)) {
                stateService.recordOutcome(
                        existingOutcome(ballot, entry, item, existing, reviewedAt),
                        ballot.packageId(), ballot.tenantId());
                continue;
            }
            try {
                VotingBallotRecord counted = voteCastService.cast(ballot, item, reviewedByUserId);
                stateService.recordOutcome(new PaperBallotOutcome(
                        null, ballot.paperBallotId(), entry.entryId(), item.subjectId(),
                        PaperBallotOutcome.Status.COUNTED, counted.ballotId(), null, null, reviewedAt),
                        ballot.packageId(), ballot.tenantId());
            } catch (VotingExecutionService.VotingExecutionException ex) {
                if (ex.getReason() != VotingExecutionService.Reason.DUPLICATE_BALLOT) {
                    throw translate(ex);
                }
                VotingBallotRecord conflict = votingExecutionRepository.findActiveBallot(
                                item.subjectId(), ballot.electorateItemId(), ballot.tenantId())
                        .orElseThrow(() -> translate(ex));
                stateService.recordOutcome(existingOutcome(ballot, entry, item, conflict, reviewedAt),
                        ballot.packageId(), ballot.tenantId());
            }
        }
        return stateService.completeBallot(
                ballot.packageId(), ballot.paperBallotId(), ballot.tenantId(), entry.entryId());
    }

    private PaperBallotOutcome existingOutcome(PaperBallot ballot,
                                                PaperBallotEntry entry,
                                                PaperBallotEntry.Item item,
                                                VotingBallotRecord existing,
                                                Instant finalizedAt) {
        if (samePaper(ballot, existing)) {
            return new PaperBallotOutcome(
                    null, ballot.paperBallotId(), entry.entryId(), item.subjectId(),
                    PaperBallotOutcome.Status.COUNTED, existing.ballotId(), null, null, finalizedAt);
        }
        return new PaperBallotOutcome(
                null, ballot.paperBallotId(), entry.entryId(), item.subjectId(),
                PaperBallotOutcome.Status.DUPLICATE, null, existing.ballotId(),
                "该专有部分对本事项已有有效票，本纸票作为重复材料留存", finalizedAt);
    }

    private boolean samePaper(PaperBallot ballot, VotingBallotRecord existing) {
        return existing.voteChannel().paperLike()
                && ballot.materialHash().equals(existing.ballotFileHash());
    }

    private String invalidReason(PaperBallotEntry.Item item) {
        String base = item.invalidReasonCode().name();
        return item.invalidReasonDescription() == null
                ? base
                : base + "：" + item.invalidReasonDescription();
    }

    private PaperVotingException translate(VotingExecutionService.VotingExecutionException exception) {
        PaperVotingException.Reason reason = switch (exception.getReason()) {
            case NOT_FOUND, ELECTORATE_NOT_FOUND -> PaperVotingException.Reason.NOT_FOUND;
            case DUPLICATE_BALLOT -> PaperVotingException.Reason.DUPLICATE;
            case CONCURRENT_MODIFICATION -> PaperVotingException.Reason.CONCURRENT_MODIFICATION;
            case INVALID_STATUS, CHANNEL_NOT_ALLOWED, DELIVERY_REQUIRED -> PaperVotingException.Reason.INVALID_STATUS;
            case INVALID_COMMAND -> PaperVotingException.Reason.INVALID_ARGUMENT;
        };
        return new PaperVotingException(reason, exception.getMessage(), exception);
    }
}
