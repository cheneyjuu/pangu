package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RepairSupplierQuoteRow {
    private Long quoteId;
    private Long workOrderId;
    private Long tenantId;
    private String supplierName;
    private BigDecimal quoteAmount;
    private String quoteSummary;
    private Long attachmentId;
    private String attachmentHash;
    private Long submittedByUserId;
    private String submittedByRoleKey;
    private Integer submittedBySupplier;
    private Integer supplierConfirmed;
    private Long supplierDeptId;
    private Long quoteInvitationId;
    private String submissionSource;
    private String confirmationStatus;
    private String originalSource;
    private String originalAttachmentHash;
    private LocalDateTime createTime;
}
