// 关联业务：映射可信资金来源、承担范围和账簿快照；建项草稿不直接写入该表。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RepairFundingSliceRow {
    private Long fundingSliceId;
    private Long decisionScopeId;
    private Long projectId;
    private Long tenantId;
    private String sourceType;
    private String sourceRecordType;
    private String sourceRecordId;
    private String ledgerReference;
    private String allocationSnapshotHash;
    private BigDecimal approvedAmount;
    private String verificationStatus;
    private Boolean legacyReadOnly;
    private LocalDateTime verifiedAt;
    private LocalDateTime createTime;
}
