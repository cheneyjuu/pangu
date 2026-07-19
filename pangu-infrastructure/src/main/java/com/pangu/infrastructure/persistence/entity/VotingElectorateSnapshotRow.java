// 关联业务：映射正式表决锁定时形成的包级表决人名册汇总。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class VotingElectorateSnapshotRow {
    private Long snapshotId;
    private Long packageId;
    private Long tenantId;
    private Integer scope;
    private Long scopeReferenceId;
    private BigDecimal totalArea;
    private Long totalOwnerCount;
    private Long itemCount;
    private String aggregateHash;
    private Instant frozenAt;
}
