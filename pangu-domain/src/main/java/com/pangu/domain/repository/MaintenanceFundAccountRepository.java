package com.pangu.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * 维修资金账户写仓储端口。
 */
public interface MaintenanceFundAccountRepository {

    Optional<Account> findByIdForUpdate(Long accountId);

    int debit(Long accountId, BigDecimal amount, long expectedVersion);

    int credit(Long accountId, BigDecimal amount, long expectedVersion);

    void insertLedgerEntry(LedgerEntry entry);

    boolean existsConfirmedLedgerEntry(Long accountId, int businessType, Long businessRefId);

    record Account(
            Long accountId,
            Long tenantId,
            BigDecimal totalBalance,
            BigDecimal frozenBalance,
            long version) {

        public BigDecimal availableBalance() {
            return totalBalance.subtract(frozenBalance);
        }
    }

    record LedgerEntry(
            Long accountId,
            int businessType,
            int direction,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Instant occurredAt,
            Long businessRefId,
            Long operatorId,
            String auditHash) {
    }
}
