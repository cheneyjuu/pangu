package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.repository.VotingResultRepository;

import java.math.BigDecimal;

/**
 * 议题结算结果响应体。
 *
 * <p>{@code resultPayloadJson} 是强类型结果（候选人当选名单、赞成/反对面积等）的 JSON 序列化串，
 * 由前端按议题类型自行解析；本期不做 server-side 反序列化，避免接口契约绑死实现细节。
 */
public record VotingResultResponse(
        Long subjectId,
        int settleVersion,
        BigDecimal totalArea,
        long totalOwnerCount,
        BigDecimal participatingArea,
        long participatingOwnerCount,
        boolean quorumSatisfied,
        boolean passed,
        String resultPayloadJson,
        Long denominatorSnapshotId,
        String attestationTxHash
) {
    public static VotingResultResponse from(VotingResultRepository.Snapshot snapshot) {
        return new VotingResultResponse(
                snapshot.subjectId(),
                snapshot.settleVersion(),
                snapshot.totalArea(),
                snapshot.totalOwnerCount(),
                snapshot.participatingArea(),
                snapshot.participatingOwnerCount(),
                snapshot.quorumSatisfied(),
                snapshot.passed(),
                snapshot.resultPayloadJson(),
                snapshot.denominatorSnapshotId(),
                snapshot.attestationTxHash());
    }
}
