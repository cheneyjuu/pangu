package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 断路器对账记录 entity（与 t_waiver_snapshot_comparison 一一对应）。
 */
@Data
public class WaiverSnapshotComparisonRow {
    private Long comparisonId;
    private Long waiverId;
    private Long subjectId;
    private Integer triggerPhase;
    private long recordedPartyCount;
    private long recordedEligibleCount;
    private BigDecimal recordedRatio;
    private long currentPartyCount;
    private long currentEligibleCount;
    private BigDecimal currentNaturalRatio;
    private long deltaParty;
    private long deltaEligible;
    private Integer actionTaken;
    private String auditHash;
}
