// 关联业务：承接维修工程项目级邀价、报价版本和中选供应商数据库行。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class RepairProjectSourcingRows {

    private RepairProjectSourcingRows() {
    }

    @Data
    public static class InvitationRow {
        private Long invitationId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private Long supplierDeptId;
        private String supplierName;
        private Long invitedByUserId;
        private LocalDateTime deadline;
        private String status;
        private Integer invitationRound;
        private String invitationType;
        private String revisionReason;
        private LocalDateTime sentAt;
        private LocalDateTime respondedAt;
    }

    @Data
    public static class QuoteRow {
        private Long quoteId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private Long supplierDeptId;
        private String supplierName;
        private BigDecimal amountExcludingTax;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        private BigDecimal quoteAmount;
        private String quoteSummary;
        private Long attachmentId;
        private String attachmentHash;
        private Long submittedByUserId;
        private String submittedByRoleKey;
        private String submissionSource;
        private String confirmationStatus;
        private String originalSource;
        private Integer constructionPeriodDays;
        private Integer warrantyDays;
        private Boolean originalAmountConfirmed;
        private String quoteStatus;
        private Integer revisionNo;
        private Long supersededByQuoteId;
        private LocalDateTime createTime;
    }

    @Data
    public static class QuoteLineRow {
        private Long quoteLineId;
        private Long quoteId;
        private Long projectItemId;
        private String projectItemNo;
        private Integer lineNo;
        private String itemName;
        private String lineType;
        private String workDescription;
        private String specificationModel;
        private String brand;
        private String procurementMethod;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal unitPriceExcludingTax;
        private BigDecimal amountExcludingTax;
        private String remark;
    }

    @Data
    public static class SelectionRow {
        private Long selectionId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private Long quoteId;
        private Long supplierDeptId;
        private String supplierName;
        private BigDecimal quoteAmount;
        private String selectionMethod;
        private String recommendationReason;
        private String insufficientQuoteReason;
        private Long frameworkRelationId;
        private Long recommendedByUserId;
        private LocalDateTime createTime;
    }
}
