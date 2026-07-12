package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RepairLocalDecisionRow {
    private Long decisionId;
    private Long workOrderId;
    private Long tenantId;
    private Long buildingId;
    private String scopeType;
    private String decisionChannel;
    private String unitName;
    private String scopeLabel;
    private Integer totalOwnerCount;
    private BigDecimal totalArea;
    private Integer participatedOwnerCount;
    private BigDecimal participatedArea;
    private Integer agreeOwnerCount;
    private BigDecimal agreeArea;
    private Integer disagreeOwnerCount;
    private BigDecimal disagreeArea;
    private Integer abstainOwnerCount;
    private BigDecimal abstainArea;
    private Integer invalidOwnerCount;
    private BigDecimal invalidArea;
    private String evidenceAttachmentHash;
    private Integer printedAndAttached;
    private String result;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
