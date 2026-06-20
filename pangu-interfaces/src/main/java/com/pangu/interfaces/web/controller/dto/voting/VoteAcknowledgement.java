package com.pangu.interfaces.web.controller.dto.voting;

/**
 * 业主投票回执（M3-2）。仅返回 voteId + voted=true，刻意不返回当前票数。
 */
public record VoteAcknowledgement(
        Long voteId,
        boolean voted
) {
    public static VoteAcknowledgement of(long voteId) {
        return new VoteAcknowledgement(voteId, true);
    }
}
