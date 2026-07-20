// 关联业务：编排纸质表决送达核对、纸票回收、录入复核及逐事项统一计票。
package com.pangu.application.voting;

import com.pangu.domain.model.voting.PaperBallot;
import com.pangu.domain.model.voting.PaperBallotEntry;
import com.pangu.domain.model.voting.PaperBallotOutcome;
import com.pangu.domain.model.voting.PaperVotingDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaperVotingService {

    private final PaperVotingStateService stateService;
    private final PaperVotingFinalizationService finalizationService;

    public PaperVotingDelivery registerDelivery(RegisterDeliveryCommand command) {
        return stateService.registerDelivery(command);
    }

    public PaperVotingDelivery reviewDelivery(ReviewDeliveryCommand command) {
        return stateService.reviewDelivery(command);
    }

    public PaperBallot registerBallot(RegisterBallotCommand command) {
        return stateService.registerBallot(command);
    }

    public PaperBallot voidBallot(VoidBallotCommand command) {
        return stateService.voidBallot(command);
    }

    public PaperBallotEntry submitEntry(SubmitEntryCommand command) {
        return stateService.submitEntry(command);
    }

    public BallotReviewResult reviewEntry(ReviewEntryCommand command) {
        PaperVotingStateService.ReviewPreparation preparation = stateService.reviewEntry(command);
        if (preparation.entry().status() == PaperBallotEntry.Status.REJECTED) {
            return new BallotReviewResult(preparation.ballot(), preparation.entry(), List.of());
        }
        if (preparation.ballot().status() == PaperBallot.Status.COMPLETED) {
            return stateService.completeBallot(
                    preparation.ballot().packageId(), preparation.ballot().paperBallotId(),
                    preparation.ballot().tenantId(), preparation.entry().entryId());
        }
        return finalizationService.finalizeConfirmed(
                preparation, preparation.entry().reviewedByUserId(), preparation.entry().reviewedAt());
    }

    public Workbench getWorkbench(Long packageId, Long tenantId) {
        return stateService.getWorkbench(packageId, tenantId);
    }

    public enum ReviewDecision {
        CONFIRM,
        REJECT
    }

    public record RegisterDeliveryCommand(
            Long packageId,
            Long tenantId,
            Long opid,
            Long proxyAuthorizationId,
            String recipientName,
            String deliveryMethod,
            String evidenceSourceType,
            Long evidenceSourceId,
            String evidenceHash,
            Long deliveredByUserId,
            Instant deliveredAt
    ) {
    }

    public record ReviewDeliveryCommand(
            Long packageId,
            Long paperDeliveryId,
            Long tenantId,
            ReviewDecision decision,
            String reviewNote,
            Long reviewedByUserId,
            Instant reviewedAt
    ) {
    }

    public record RegisterBallotCommand(
            Long packageId,
            Long tenantId,
            Long opid,
            Long proxyAuthorizationId,
            String ballotNumber,
            String templateHash,
            String materialSourceType,
            Long materialSourceId,
            String materialHash,
            Long receivedByUserId,
            Instant receivedAt
    ) {
    }

    public record SubmitEntryCommand(
            Long packageId,
            Long paperBallotId,
            Long tenantId,
            String templateHashGuard,
            List<PaperBallotEntry.Item> items,
            Long enteredByUserId,
            Instant enteredAt
    ) {
    }

    public record VoidBallotCommand(
            Long packageId,
            Long paperBallotId,
            Long tenantId,
            String reason,
            Long voidedByUserId,
            Instant voidedAt
    ) {
    }

    public record ReviewEntryCommand(
            Long packageId,
            Long paperBallotId,
            Long entryId,
            Long tenantId,
            ReviewDecision decision,
            String reviewNote,
            Long reviewedByUserId,
            Instant reviewedAt
    ) {
    }

    public record BallotReviewResult(
            PaperBallot ballot,
            PaperBallotEntry entry,
            List<PaperBallotOutcome> outcomes
    ) {
        public BallotReviewResult {
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }
    }

    public record BallotWorkbenchItem(
            PaperBallot ballot,
            PaperBallotEntry latestEntry,
            List<PaperBallotOutcome> outcomes
    ) {
        public BallotWorkbenchItem {
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }
    }

    public record Workbench(
            List<PaperVotingDelivery> deliveries,
            List<BallotWorkbenchItem> ballots
    ) {
        public Workbench {
            deliveries = deliveries == null ? List.of() : List.copyOf(deliveries);
            ballots = ballots == null ? List.of() : List.copyOf(ballots);
        }
    }
}
