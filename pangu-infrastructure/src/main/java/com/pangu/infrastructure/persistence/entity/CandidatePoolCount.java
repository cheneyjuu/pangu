package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

/**
 * 候选人池当前快照（断路器对账时刻 COUNT 结果）。
 */
@Data
public class CandidatePoolCount {
    private long partyCount;
    private long eligibleCount;
}
