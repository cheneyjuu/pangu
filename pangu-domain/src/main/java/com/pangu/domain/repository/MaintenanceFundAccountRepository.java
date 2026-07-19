// 关联业务：按共有范围定位专项维修资金账户，并在资金支取或维修方案锁定时读取可信账簿快照。
package com.pangu.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * 维修资金账户写仓储端口。
 */
public interface MaintenanceFundAccountRepository {

    Optional<Account> findByIdForUpdate(Long accountId);

    /**
     * 维修方案只能从与决定范围严格对应的专项维修资金账户取得账簿事实；调用方持有行锁直到本次快照完成。
     */
    Optional<Account> findByScopeForUpdate(Long tenantId, AccountScope scope, Long referenceId);

    int debit(Long accountId, BigDecimal amount, long expectedVersion);

    int credit(Long accountId, BigDecimal amount, long expectedVersion);

    void insertLedgerEntry(LedgerEntry entry);

    boolean existsConfirmedLedgerEntry(Long accountId, int businessType, Long businessRefId);

    /**
     * 当前账户树中已具备稳定业务标识的范围层级。单元层缺少可核验 unitId 时不得以名称猜测绑定。
     */
    enum AccountScope {
        COMMUNITY(1),
        BUILDING(2);

        private final int accountLevel;

        AccountScope(int accountLevel) {
            this.accountLevel = accountLevel;
        }

        public int accountLevel() {
            return accountLevel;
        }
    }

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
