// 关联业务：映射维修工程不可变实施方案版本及锁定快照。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RepairPlanVersionRow {
    private Long planId;
    private Long projectId;
    private Long tenantId;
    private Integer versionNo;
    private String problemCause;
    private String implementationScope;
    private BigDecimal budgetTotal;
    private String fundSource;
    private String allocationRuleType;
    private String allocationRuleDescription;
    private String supplierSelectionMethod;
    private String supplierSelectionReason;
    private String constructionManagementRequirements;
    private String evidenceRequirementsJson;
    private String safetyRequirements;
    private String acceptanceMethod;
    private String requiredAcceptanceRolesJson;
    private String affectedOwnerScopeDescription;
    private Integer minimumAffectedOwnerAcceptors;
    private String affectedOwnerPassRule;
    private BigDecimal affectedOwnerApprovalRatio;
    private String settlementMethod;
    private LocalDate plannedStartDate;
    private LocalDate plannedCompletionDate;
    private Integer warrantyDays;
    private String governancePath;
    private Integer priceReviewRequired;
    private String paymentMilestonesJson;
    private String status;
    private String snapshotHash;
    private Long createdByAccountId;
    private Long createdByUserId;
    private Long lockedByUserId;
    private LocalDateTime createTime;
    private LocalDateTime lockedAt;
}
