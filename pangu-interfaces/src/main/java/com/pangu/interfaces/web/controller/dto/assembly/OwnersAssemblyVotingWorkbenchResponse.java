// 关联业务：向业主大会经办人员展示纸票送达、回收复核和互联网表决纸质协助进度。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.application.assembly.OwnersAssemblyApplicationService;
import com.pangu.application.voting.PaperVotingService;

import java.time.Instant;
import java.util.List;

/** 管理端办理投影不包含任何线上票面选择。 */
public record OwnersAssemblyVotingWorkbenchResponse(
        List<PaperVotingDeliveryResponse> deliveries,
        List<BallotItem> ballots,
        List<PaperAssistanceItem> paperAssistance,
        OnlineProgress online,
        long duplicatePaperDecisionCount
) {
    public static OwnersAssemblyVotingWorkbenchResponse from(
            OwnersAssemblyApplicationService.VotingWorkbench workbench) {
        return new OwnersAssemblyVotingWorkbenchResponse(
                workbench.paper().deliveries().stream().map(PaperVotingDeliveryResponse::from).toList(),
                workbench.paper().ballots().stream().map(BallotItem::from).toList(),
                workbench.paperAssistance().stream().map(PaperAssistanceItem::from).toList(),
                new OnlineProgress(
                        workbench.online().completedPropertyCount(), workbench.online().conflictCount()),
                workbench.duplicatePaperDecisionCount());
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

    public record PaperAssistanceItem(
            Long requestId,
            Long opid,
            Long buildingId,
            Long roomId,
            String stage,
            Instant requestedAt,
            Instant fulfilledAt,
            Instant withdrawnAt,
            Long paperDeliveryId
    ) {
        private static PaperAssistanceItem from(
                OwnersAssemblyApplicationService.PaperAssistanceWorkbenchItem item) {
            return new PaperAssistanceItem(
                    item.requestId(), item.opid(), item.buildingId(), item.roomId(), item.stage().name(),
                    item.requestedAt(), item.fulfilledAt(), item.withdrawnAt(), item.paperDeliveryId());
        }
    }

    public record OnlineProgress(long completedPropertyCount, long conflictCount) {
    }
}
