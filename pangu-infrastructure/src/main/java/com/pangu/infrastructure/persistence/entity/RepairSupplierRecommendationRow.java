package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairSupplierRecommendationRow {
    private Long recommendationId;
    private Long workOrderId;
    private Long tenantId;
    private Long quoteId;
    private Long recommendedByUserId;
    private String recommendationReason;
    private Integer singleSource;
    private String singleSourceReason;
    private String selectionMethod;
    private String insufficientQuoteReason;
    private Long frameworkRelationId;
    private LocalDateTime createTime;
}
