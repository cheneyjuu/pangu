package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.repository.VoteDetailQueryRepository.VoteDetailRow;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 逐户投票明细视图（M4-2）。
 *
 * <p>数据缺口占位（见端口文档）：无房号标签表，{@code buildingId/roomId} 为裸 ID，
 * 前端以 {@code buildingId-roomId} 展示；业主姓名为 SM4 密文本期不解密，前端以 {@code 业主#uid} 占位。
 *
 * @param choice 投票选项枚举 name（{@code SUPPORT/AGAINST/ABSTAIN}）；未投时为 null
 */
public record VoteDetailResponse(
        Long opid,
        Long uid,
        Long buildingId,
        Long roomId,
        BigDecimal propertyArea,
        Integer authLevel,
        boolean voted,
        VoteChoice choice,
        Instant votedAt
) {
    public static VoteDetailResponse from(VoteDetailRow r) {
        return new VoteDetailResponse(
                r.opid(),
                r.uid(),
                r.buildingId(),
                r.roomId(),
                r.propertyArea(),
                r.authLevel(),
                r.voted(),
                r.choice(),
                r.votedAt());
    }
}
