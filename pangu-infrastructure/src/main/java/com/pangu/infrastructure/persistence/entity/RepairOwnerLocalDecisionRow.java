package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RepairOwnerLocalDecisionRow {
    private Long decisionId;
    private Long workOrderId;
    private String orderNo;
    private String title;
    private String locationText;
    private String surveySummary;
    private String scopeLabel;
    private Long opid;
    private Long roomId;
    private String roomName;
    private BigDecimal buildArea;
    private String supplierName;
    private BigDecimal quoteAmount;
    private String quoteSummary;
    private Long quoteAttachmentId;
    private String myChoice;
}
