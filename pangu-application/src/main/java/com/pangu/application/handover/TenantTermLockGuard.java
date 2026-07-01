package com.pangu.application.handover;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * HANDOVER_LOCK 下的金额型敏感操作守卫。
 */
@Service
public class TenantTermLockGuard {

    private final HandoverCircuitBreaker handoverCircuitBreaker;

    @Getter
    private final BigDecimal largeAmountThreshold;

    public TenantTermLockGuard(
            HandoverCircuitBreaker handoverCircuitBreaker,
            @Value("${platform.handover.large-amount-threshold:10000.00}") String threshold) {
        this.handoverCircuitBreaker = handoverCircuitBreaker;
        this.largeAmountThreshold = new BigDecimal(threshold);
    }

    /**
     * HANDOVER_LOCK 期间仅熔断大额（>= 阈值）操作，小额放行。
     */
    public Optional<Long> lockedElectionForLargeAmount(Long tenantId, BigDecimal amount) {
        if (!isLargeAmount(amount)) {
            return Optional.empty();
        }
        return handoverCircuitBreaker.activeElectionSubjectId(tenantId);
    }

    public boolean isLargeAmount(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        return amount.compareTo(largeAmountThreshold) >= 0;
    }
}
