package com.pangu.bootstrap.handover;

import com.pangu.application.fund.MaintenanceFundApplicationException;
import com.pangu.application.fund.MaintenanceFundApplicationService;
import com.pangu.application.fund.command.MaintenanceFundExpenseCommand;
import com.pangu.application.fund.command.PublicRevenueTransferCommand;
import com.pangu.application.fund.command.TrustFundDisbursementCommand;
import com.pangu.application.lock.GovernanceLockApplicationService;
import com.pangu.application.lock.command.CommitteeUnlockCommand;
import com.pangu.application.lock.command.LockCommand;
import com.pangu.application.lock.command.StreetUnlockCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.model.lock.LockEntityType;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.repository.MaintenanceFundAccountRepository.LedgerEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 梯度 C：HANDOVER_LOCK 下维修资金支取的金额阈值熔断。
 */
@SpringBootTest
public class MaintenanceFundHandoverGuardTest {

    private static final long TEST_TENANT_ID = 99621L;
    private static final long OPERATOR_ID = 800101L;
    private static final long COMMITTEE_USER_ID = 800201L;
    private static final long STREET_USER_ID = 800301L;
    private static final String HASH64 = "f".repeat(64);
    private static final String TITLE_PREFIX = "HANDOVER-FUND-";

    @Autowired
    private MaintenanceFundApplicationService service;

    @Autowired
    private GovernanceLockApplicationService lockApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserContextHolder userContextHolder;

    @BeforeEach
    public void setUp() {
        cleanUp();
    }

    @AfterEach
    public void tearDown() {
        userContextHolder.clear();
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_tenant_term_state WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM t_governance_lock WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_fund_ledger_entry WHERE account_id IN "
                        + "(SELECT account_id FROM t_maintenance_fund_account WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM t_maintenance_fund_account WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?",
                TEST_TENANT_ID, TITLE_PREFIX + "%");
    }

    private long seedAccount(BigDecimal totalBalance, BigDecimal frozenBalance) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_maintenance_fund_account(tenant_id, account_level, reference_id, "
                        + "ancestors, total_balance, frozen_balance) "
                        + "VALUES (?, 1, ?, '0', ?, ?) RETURNING account_id",
                Long.class, TEST_TENANT_ID, TEST_TENANT_ID, totalBalance, frozenBalance);
    }

    private long seedSettledElectionLock() {
        Long subjectId = jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, max_winners, settled_at) "
                        + "VALUES(?, ?, 1, 1, 5, 2, CURRENT_TIMESTAMP) RETURNING subject_id",
                Long.class, TEST_TENANT_ID, TITLE_PREFIX + "已结算换届选举");
        jdbcTemplate.update(
                "INSERT INTO t_tenant_term_state(tenant_id, term_status, term_locked_at, term_locked_by_subject_id) "
                        + "VALUES(?, 2, CURRENT_TIMESTAMP, ?)",
                TEST_TENANT_ID, subjectId);
        return subjectId;
    }

    private MaintenanceFundExpenseCommand command(long accountId, String amount) {
        return new MaintenanceFundExpenseCommand(
                TEST_TENANT_ID,
                accountId,
                new BigDecimal(amount),
                88001L,
                OPERATOR_ID);
    }

    private PublicRevenueTransferCommand revenueCommand(long accountId, String amount) {
        return new PublicRevenueTransferCommand(
                TEST_TENANT_ID,
                accountId,
                new BigDecimal(amount),
                99001L,
                OPERATOR_ID);
    }

    private TrustFundDisbursementCommand trustCommand(long accountId, long trustPaymentId, String amount) {
        return new TrustFundDisbursementCommand(
                TEST_TENANT_ID,
                accountId,
                trustPaymentId,
                new BigDecimal(amount),
                OPERATOR_ID);
    }

    private TrustFundDisbursementCommand trustInstallmentCommand(
            long accountId,
            long trustPaymentId,
            int installmentNo,
            Long previousTrustPaymentId,
            String amount) {
        return new TrustFundDisbursementCommand(
                TEST_TENANT_ID,
                accountId,
                trustPaymentId,
                installmentNo,
                previousTrustPaymentId,
                new BigDecimal(amount),
                OPERATOR_ID);
    }

    private GovernanceLock lockTrustPayment(long trustPaymentId) {
        return lockApplicationService.lock(
                new LockCommand(TEST_TENANT_ID, LockEntityType.TRUST_FUND_PAYMENT,
                        trustPaymentId, OPERATOR_ID, HASH64));
    }

    private void setRole(String roleKey, long userId) {
        userContextHolder.set(new UserContext(
                userId + 100000L,
                UserContext.IdentityType.SYS_USER,
                userId,
                TEST_TENANT_ID,
                9001L,
                "GOV_SUPER_ADMIN".equals(roleKey) ? UserContext.DeptCategory.G : UserContext.DeptCategory.B,
                "GOV_SUPER_ADMIN".equals(roleKey) ? 2 : 10,
                DataScopeType.ALL_COMMUNITY,
                AuthenticationLevel.L1,
                roleKey,
                Set.of(),
                Set.of()));
    }

    private void fullyUnlockTrustPayment(long trustPaymentId) {
        GovernanceLock lock = lockTrustPayment(trustPaymentId);
        setRole("COMMITTEE_DIRECTOR", COMMITTEE_USER_ID);
        lockApplicationService.committeeSign(
                new CommitteeUnlockCommand(lock.getLockId(), COMMITTEE_USER_ID, "sig-committee"));
        setRole("GOV_SUPER_ADMIN", STREET_USER_ID);
        lockApplicationService.streetSign(
                new StreetUnlockCommand(lock.getLockId(), STREET_USER_ID, "sig-street"));
    }

    @Test
    public void handoverLock_largeExpenseRejectedAndNoLedgerWritten() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        seedSettledElectionLock();

        MaintenanceFundApplicationException ex = assertThrows(
                MaintenanceFundApplicationException.class,
                () -> service.recordMaintenanceExpense(command(accountId, "10000.00")));
        assertEquals(MaintenanceFundApplicationException.Reason.HANDOVER_LOCKED_LARGE_AMOUNT,
                ex.getReason());

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT total_balance FROM t_maintenance_fund_account WHERE account_id = ?",
                BigDecimal.class, accountId);
        assertEquals(0, new BigDecimal("50000.00").compareTo(balance));
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry WHERE account_id = ?",
                Integer.class, accountId);
        assertEquals(0, ledgerRows);
    }

    @Test
    public void handoverLock_smallExpenseAllowed() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        seedSettledElectionLock();

        LedgerEntry entry = service.recordMaintenanceExpense(command(accountId, "9999.99"));

        assertEquals(0, new BigDecimal("40000.01").compareTo(entry.balanceAfter()));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT total_balance, version FROM t_maintenance_fund_account WHERE account_id = ?",
                accountId);
        assertEquals(0, new BigDecimal("40000.01").compareTo((BigDecimal) row.get("total_balance")));
        assertEquals(1L, ((Number) row.get("version")).longValue());
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry "
                        + "WHERE account_id = ? AND business_type = 4 AND direction = 2 AND amount = ?",
                Integer.class, accountId, new BigDecimal("9999.99"));
        assertEquals(1, ledgerRows);
    }

    @Test
    public void noHandover_largeExpenseAllowed() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);

        LedgerEntry entry = service.recordMaintenanceExpense(command(accountId, "10000.00"));

        assertEquals(0, new BigDecimal("40000.00").compareTo(entry.balanceAfter()));
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry WHERE account_id = ?",
                Integer.class, accountId);
        assertEquals(1, ledgerRows);
    }

    @Test
    public void handoverLock_largePublicRevenueTransferRejectedAndNoLedgerWritten() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        seedSettledElectionLock();

        MaintenanceFundApplicationException ex = assertThrows(
                MaintenanceFundApplicationException.class,
                () -> service.recordPublicIncomeTransfer(revenueCommand(accountId, "10000.00")));
        assertEquals(MaintenanceFundApplicationException.Reason.HANDOVER_LOCKED_LARGE_AMOUNT,
                ex.getReason());

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT total_balance FROM t_maintenance_fund_account WHERE account_id = ?",
                BigDecimal.class, accountId);
        assertEquals(0, new BigDecimal("50000.00").compareTo(balance));
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry WHERE account_id = ?",
                Integer.class, accountId);
        assertEquals(0, ledgerRows);
    }

    @Test
    public void handoverLock_smallPublicRevenueTransferAllowed() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        seedSettledElectionLock();

        LedgerEntry entry = service.recordPublicIncomeTransfer(revenueCommand(accountId, "9999.99"));

        assertEquals(0, new BigDecimal("59999.99").compareTo(entry.balanceAfter()));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT total_balance, version FROM t_maintenance_fund_account WHERE account_id = ?",
                accountId);
        assertEquals(0, new BigDecimal("59999.99").compareTo((BigDecimal) row.get("total_balance")));
        assertEquals(1L, ((Number) row.get("version")).longValue());
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry "
                        + "WHERE account_id = ? AND business_type = 3 AND direction = 1 AND amount = ?",
                Integer.class, accountId, new BigDecimal("9999.99"));
        assertEquals(1, ledgerRows);
    }

    @Test
    public void noHandover_trustDisbursementWithoutFullUnlockRejectedAndNoLedgerWritten() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        long trustPaymentId = 77001L;
        lockTrustPayment(trustPaymentId);

        MaintenanceFundApplicationException ex = assertThrows(
                MaintenanceFundApplicationException.class,
                () -> service.recordTrustFundDisbursement(
                        trustCommand(accountId, trustPaymentId, "9999.99")));
        assertEquals(MaintenanceFundApplicationException.Reason.TRUST_PAYMENT_NOT_FULLY_UNLOCKED,
                ex.getReason());

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT total_balance FROM t_maintenance_fund_account WHERE account_id = ?",
                BigDecimal.class, accountId);
        assertEquals(0, new BigDecimal("50000.00").compareTo(balance));
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry WHERE account_id = ?",
                Integer.class, accountId);
        assertEquals(0, ledgerRows);
    }

    @Test
    public void noHandover_fullyUnlockedTrustDisbursementAllowed() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        long trustPaymentId = 77002L;
        fullyUnlockTrustPayment(trustPaymentId);

        LedgerEntry entry = service.recordTrustFundDisbursement(
                trustCommand(accountId, trustPaymentId, "9999.99"));

        assertEquals(0, new BigDecimal("40000.01").compareTo(entry.balanceAfter()));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT total_balance, version FROM t_maintenance_fund_account WHERE account_id = ?",
                accountId);
        assertEquals(0, new BigDecimal("40000.01").compareTo((BigDecimal) row.get("total_balance")));
        assertEquals(1L, ((Number) row.get("version")).longValue());
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry "
                        + "WHERE account_id = ? AND business_type = 7 AND direction = 2 AND amount = ?",
                Integer.class, accountId, new BigDecimal("9999.99"));
        assertEquals(1, ledgerRows);
    }

    @Test
    public void handoverLock_largeTrustDisbursementRejectedEvenAfterFullUnlock() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        long trustPaymentId = 77003L;
        fullyUnlockTrustPayment(trustPaymentId);
        seedSettledElectionLock();

        MaintenanceFundApplicationException ex = assertThrows(
                MaintenanceFundApplicationException.class,
                () -> service.recordTrustFundDisbursement(
                        trustCommand(accountId, trustPaymentId, "10000.00")));
        assertEquals(MaintenanceFundApplicationException.Reason.HANDOVER_LOCKED_LARGE_AMOUNT,
                ex.getReason());

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT total_balance FROM t_maintenance_fund_account WHERE account_id = ?",
                BigDecimal.class, accountId);
        assertEquals(0, new BigDecimal("50000.00").compareTo(balance));
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry WHERE account_id = ?",
                Integer.class, accountId);
        assertEquals(0, ledgerRows);
    }

    @Test
    public void trustSecondInstallmentRejectedUntilPreviousLedgerConfirmed() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        long previousTrustPaymentId = 77004L;
        long currentTrustPaymentId = 77005L;
        fullyUnlockTrustPayment(previousTrustPaymentId);
        fullyUnlockTrustPayment(currentTrustPaymentId);

        MaintenanceFundApplicationException ex = assertThrows(
                MaintenanceFundApplicationException.class,
                () -> service.recordTrustFundDisbursement(
                        trustInstallmentCommand(
                                accountId, currentTrustPaymentId, 2, previousTrustPaymentId, "1000.00")));
        assertEquals(MaintenanceFundApplicationException.Reason.TRUST_PREVIOUS_INSTALLMENT_NOT_CONFIRMED,
                ex.getReason());

        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry WHERE account_id = ?",
                Integer.class, accountId);
        assertEquals(0, ledgerRows);
    }

    @Test
    public void trustSecondInstallmentRejectedWhenPreviousLedgerNotChainConfirmed() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        long previousTrustPaymentId = 77006L;
        long currentTrustPaymentId = 77007L;
        fullyUnlockTrustPayment(previousTrustPaymentId);
        fullyUnlockTrustPayment(currentTrustPaymentId);

        service.recordTrustFundDisbursement(
                trustInstallmentCommand(
                        accountId, previousTrustPaymentId, 1, null, "1000.00"));

        MaintenanceFundApplicationException ex = assertThrows(
                MaintenanceFundApplicationException.class,
                () -> service.recordTrustFundDisbursement(
                        trustInstallmentCommand(
                                accountId, currentTrustPaymentId, 2, previousTrustPaymentId, "1500.00")));

        assertEquals(MaintenanceFundApplicationException.Reason.TRUST_PREVIOUS_INSTALLMENT_NOT_CONFIRMED,
                ex.getReason());
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry "
                        + "WHERE account_id = ? AND business_type = 7 AND direction = 2",
                Integer.class, accountId);
        assertEquals(1, ledgerRows);
    }

    @Test
    public void trustSecondInstallmentAllowedAfterPreviousLedgerChainConfirmed() {
        long accountId = seedAccount(new BigDecimal("50000.00"), BigDecimal.ZERO);
        long previousTrustPaymentId = 77008L;
        long currentTrustPaymentId = 77009L;
        fullyUnlockTrustPayment(previousTrustPaymentId);
        fullyUnlockTrustPayment(currentTrustPaymentId);

        service.recordTrustFundDisbursement(
                trustInstallmentCommand(
                        accountId, previousTrustPaymentId, 1, null, "1000.00"));
        markTrustLedgerConfirmed(accountId, previousTrustPaymentId, "chain-tx-77008");
        LedgerEntry entry = service.recordTrustFundDisbursement(
                trustInstallmentCommand(
                        accountId, currentTrustPaymentId, 2, previousTrustPaymentId, "1500.00"));

        assertEquals(0, new BigDecimal("47500.00").compareTo(entry.balanceAfter()));
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_fund_ledger_entry "
                        + "WHERE account_id = ? AND business_type = 7 AND direction = 2",
                Integer.class, accountId);
        assertEquals(2, ledgerRows);
    }

    private void markTrustLedgerConfirmed(long accountId, long trustPaymentId, String txHash) {
        int updated = jdbcTemplate.update("""
                UPDATE t_fund_ledger_entry
                SET blockchain_tx_hash = ?,
                    chain_attest_status = 3,
                    chain_confirmed_at = CURRENT_TIMESTAMP
                WHERE account_id = ?
                  AND business_type = 7
                  AND business_ref_id = ?
                """, txHash, accountId, trustPaymentId);
        assertEquals(1, updated);
    }
}
