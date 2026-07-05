package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class DenominatorReviewRequestRow {
    private Long requestId;
    private Long tenantId;
    private BigDecimal requestedTotalArea;
    private Long requestedOwnerCount;
    private Long requestedUnitCount;
    private String reason;
    private String status;
    private Long requestedBy;
    private Long reviewedBy;
    private String reviewComment;
    private Instant createTime;
    private Instant reviewTime;
}
