package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.VoteCastMonitorSnapshot;

import java.math.BigDecimal;

/**
 * 管理端投票监控基线视图。
 */
public record VoteMonitorResponse(
        Long subjectId,
        long totalCount,
        long unsignedCount,
        BigDecimal unsignedRatio,
        BigDecimal unsignedRatioThreshold,
        boolean unsignedRatioAlert,
        long rapidIntervalCount,
        long rapidIntervalThreshold,
        boolean rapidIntervalAlert
) {
    public static VoteMonitorResponse from(VoteCastMonitorSnapshot snapshot) {
        return new VoteMonitorResponse(
                snapshot.subjectId(),
                snapshot.totalCount(),
                snapshot.unsignedCount(),
                snapshot.unsignedRatio(),
                snapshot.unsignedRatioThreshold(),
                snapshot.unsignedRatioAlert(),
                snapshot.rapidIntervalCount(),
                snapshot.rapidIntervalThreshold(),
                snapshot.rapidIntervalAlert());
    }
}
