package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 党员放宽申请生效行（policy resolver 查询用）。
 *
 * <p>仅承载断路器对账所需的最小列，绕开聚合根 rehydrate 的全字段加载开销。
 */
@Data
public class WaiverPolicyRow {
    private Long waiverId;
    private BigDecimal requestedRatio;
    private long recordedPartyCount;
    private long recordedEligibleCount;
    private Integer status;
}
