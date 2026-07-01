package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * C 端业主投票提交请求体（M3-2）。
 *
 * <p>{@code opid} 由前端显式传入，service 层校验它属于当前 {@code uid} + 在 {@code subject.scope} 范围内。
 * {@code targetId} 用于 ELECTION 等"多选一"场景，本期允许为 null（GENERAL/MAJOR 不需要）。
 */
public record CastVoteRequest(
        @NotNull Long opid,
        Long targetId,
        @NotNull VoteChoice choice,
        @Size(max = 256) String signatureHash,
        VoteChannel voteChannel
) {
}
