// 关联业务：汇总一次业主大会的纸质送达、纸票回收、录入复核和最终处理结果。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.application.voting.PaperVotingService;

import java.util.List;

public record PaperVotingWorkbenchResponse(
        List<PaperVotingDeliveryResponse> deliveries,
        List<BallotItem> ballots
) {
    public static PaperVotingWorkbenchResponse from(PaperVotingService.Workbench workbench) {
        return new PaperVotingWorkbenchResponse(
                workbench.deliveries().stream().map(PaperVotingDeliveryResponse::from).toList(),
                workbench.ballots().stream().map(BallotItem::from).toList());
    }

    public record BallotItem(
            PaperBallotResponse ballot,
            PaperBallotEntryResponse latestEntry,
            List<PaperBallotReviewResponse.Outcome> outcomes
    ) {
        private static BallotItem from(PaperVotingService.BallotWorkbenchItem item) {
            return new BallotItem(
                    PaperBallotResponse.from(item.ballot()),
                    PaperBallotEntryResponse.from(item.latestEntry()),
                    item.outcomes().stream().map(PaperBallotReviewResponse.Outcome::from).toList());
        }
    }
}
