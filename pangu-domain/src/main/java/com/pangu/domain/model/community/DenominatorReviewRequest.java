package com.pangu.domain.model.community;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 业委会主任提交、G 端复核的法定计票基数变更申请。
 */
public record DenominatorReviewRequest(
        Long requestId,
        Long tenantId,
        BigDecimal requestedTotalArea,
        long requestedOwnerCount,
        long requestedUnitCount,
        String reason,
        String status,
        Long requestedBy,
        Long reviewedBy,
        String reviewComment,
        Instant createTime,
        Instant reviewTime
) {
}
