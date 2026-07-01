package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code t_voting_denominator_snapshot} 行映射。
 */
@Data
public class DenominatorSnapshotRow {
    private Long snapshotId;
    private Long subjectId;
    private Integer scope;
    private Long scopeReferenceId;
    private BigDecimal totalArea;
    private Long totalOwnerCount;
    private Long itemCount;
    private String aggregateHash;
    private LocalDateTime snapshotAt;
}
