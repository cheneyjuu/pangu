// 关联业务：返回纸票录入复核后的票据状态及每个表决事项是否计入、无效或重复。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.application.voting.PaperVotingService;
import com.pangu.domain.model.voting.PaperBallotOutcome;

import java.time.Instant;
import java.util.List;

public record PaperBallotReviewResponse(
        String status,
        PaperBallotResponse ballot,
        PaperBallotEntryResponse entry,
        List<Outcome> outcomes
) {
    public static PaperBallotReviewResponse from(PaperVotingService.BallotReviewResult result) {
        return new PaperBallotReviewResponse(
                result.ballot().status().name(),
                PaperBallotResponse.from(result.ballot()),
                PaperBallotEntryResponse.from(result.entry()),
                result.outcomes().stream().map(Outcome::from).toList());
    }

    public record Outcome(
            Long outcomeId,
            Long subjectId,
            String status,
            Long unifiedBallotId,
            Long conflictingBallotId,
            String reason,
            Instant finalizedAt
    ) {
        public static Outcome from(PaperBallotOutcome outcome) {
            return new Outcome(
                    outcome.outcomeId(),
                    outcome.subjectId(),
                    outcome.status().name(),
                    outcome.unifiedBallotId(),
                    outcome.conflictingBallotId(),
                    outcome.reason(),
                    outcome.finalizedAt());
        }
    }
}
