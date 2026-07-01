package com.pangu.application.fund;

import com.pangu.application.fund.command.MaintenanceFundExpenseCommand;
import com.pangu.application.fund.command.PublicRevenueTransferCommand;
import com.pangu.application.fund.command.TrustFundDisbursementCommand;
import com.pangu.application.handover.TenantTermLockGuard;
import com.pangu.application.support.PayloadHasher;
import com.pangu.domain.model.lock.LockEntityType;
import com.pangu.domain.repository.GovernanceLockRepository;
import com.pangu.domain.repository.MaintenanceFundAccountRepository;
import com.pangu.domain.repository.MaintenanceFundAccountRepository.Account;
import com.pangu.domain.repository.MaintenanceFundAccountRepository.LedgerEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 维修资金动账应用服务。
 */
@Service
@RequiredArgsConstructor
public class MaintenanceFundApplicationService {

    /** t_fund_ledger_entry.business_type = 4: MAINTENANCE_PROJECT。 */
    private static final int BUSINESS_TYPE_MAINTENANCE_PROJECT = 4;

    /** t_fund_ledger_entry.business_type = 3: PUBLIC_INCOME_TRANSFER。 */
    private static final int BUSINESS_TYPE_PUBLIC_INCOME_TRANSFER = 3;

    /** t_fund_ledger_entry.business_type = 7: TRUST_FUND_DISBURSEMENT。 */
    private static final int BUSINESS_TYPE_TRUST_FUND_DISBURSEMENT = 7;

    /** t_fund_ledger_entry.direction = 1: DEBIT / 入账。 */
    private static final int DIRECTION_DEBIT = 1;

    /** t_fund_ledger_entry.direction = 2: CREDIT / 出账。 */
    private static final int DIRECTION_CREDIT = 2;

    private final MaintenanceFundAccountRepository repository;
    private final GovernanceLockRepository governanceLockRepository;
    private final TenantTermLockGuard tenantTermLockGuard;

    @Transactional
    public LedgerEntry recordMaintenanceExpense(MaintenanceFundExpenseCommand cmd) {
        validate(cmd);
        tenantTermLockGuard.lockedElectionForLargeAmount(cmd.tenantId(), cmd.amount()).ifPresent(electionId -> {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.HANDOVER_LOCKED_LARGE_AMOUNT,
                    "换届锁定中，大额维修资金支取已熔断 electionSubjectId=" + electionId
                            + " amount=" + cmd.amount());
        });

        Account account = repository.findByIdForUpdate(cmd.accountId())
                .orElseThrow(() -> new MaintenanceFundApplicationException(
                        MaintenanceFundApplicationException.Reason.ACCOUNT_NOT_FOUND,
                        "维修资金账户不存在 accountId=" + cmd.accountId()));
        if (!cmd.tenantId().equals(account.tenantId())) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.TENANT_MISMATCH,
                    "维修资金账户与当前租户不匹配 accountId=" + cmd.accountId());
        }
        if (account.availableBalance().compareTo(cmd.amount()) < 0) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.INSUFFICIENT_AVAILABLE_BALANCE,
                    "维修资金可用余额不足 accountId=" + cmd.accountId());
        }

        BigDecimal balanceAfter = account.totalBalance().subtract(cmd.amount());
        int updated = repository.debit(account.accountId(), cmd.amount(), account.version());
        if (updated != 1) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.CONCURRENT_MODIFICATION,
                    "维修资金账户已被并发修改 accountId=" + cmd.accountId());
        }
        LedgerEntry entry = new LedgerEntry(
                account.accountId(),
                BUSINESS_TYPE_MAINTENANCE_PROJECT,
                DIRECTION_CREDIT,
                cmd.amount(),
                balanceAfter,
                Instant.now(),
                cmd.businessRefId(),
                cmd.operatorId(),
                auditHash(cmd, account, balanceAfter));
        repository.insertLedgerEntry(entry);
        return entry;
    }

    @Transactional
    public LedgerEntry recordPublicIncomeTransfer(PublicRevenueTransferCommand cmd) {
        validate(cmd);
        tenantTermLockGuard.lockedElectionForLargeAmount(cmd.tenantId(), cmd.amount()).ifPresent(electionId -> {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.HANDOVER_LOCKED_LARGE_AMOUNT,
                    "换届锁定中，大额公共收益划拨已熔断 electionSubjectId=" + electionId
                            + " amount=" + cmd.amount());
        });

        Account account = repository.findByIdForUpdate(cmd.accountId())
                .orElseThrow(() -> new MaintenanceFundApplicationException(
                        MaintenanceFundApplicationException.Reason.ACCOUNT_NOT_FOUND,
                        "维修资金账户不存在 accountId=" + cmd.accountId()));
        if (!cmd.tenantId().equals(account.tenantId())) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.TENANT_MISMATCH,
                    "维修资金账户与当前租户不匹配 accountId=" + cmd.accountId());
        }

        BigDecimal balanceAfter = account.totalBalance().add(cmd.amount());
        int updated = repository.credit(account.accountId(), cmd.amount(), account.version());
        if (updated != 1) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.CONCURRENT_MODIFICATION,
                    "维修资金账户已被并发修改 accountId=" + cmd.accountId());
        }
        LedgerEntry entry = new LedgerEntry(
                account.accountId(),
                BUSINESS_TYPE_PUBLIC_INCOME_TRANSFER,
                DIRECTION_DEBIT,
                cmd.amount(),
                balanceAfter,
                Instant.now(),
                cmd.businessRefId(),
                cmd.operatorId(),
                auditHash(cmd.tenantId(), cmd.accountId(), cmd.amount(), balanceAfter,
                        cmd.businessRefId(), cmd.operatorId(), account.version()));
        repository.insertLedgerEntry(entry);
        return entry;
    }

    @Transactional
    public LedgerEntry recordTrustFundDisbursement(TrustFundDisbursementCommand cmd) {
        validate(cmd);
        tenantTermLockGuard.lockedElectionForLargeAmount(cmd.tenantId(), cmd.amount()).ifPresent(electionId -> {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.HANDOVER_LOCKED_LARGE_AMOUNT,
                    "换届锁定中，大额信托制动账已熔断 electionSubjectId=" + electionId
                            + " amount=" + cmd.amount());
        });
        governanceLockRepository.findByEntityForUpdate(
                        cmd.tenantId(), LockEntityType.TRUST_FUND_PAYMENT, cmd.trustPaymentId())
                .filter(lock -> lock.isUnlocked())
                .orElseThrow(() -> new MaintenanceFundApplicationException(
                        MaintenanceFundApplicationException.Reason.TRUST_PAYMENT_NOT_FULLY_UNLOCKED,
                        "信托制付款指令未完成业委会主任与街道办双签 trustPaymentId=" + cmd.trustPaymentId()));
        ensurePreviousInstallmentConfirmed(cmd);

        Account account = repository.findByIdForUpdate(cmd.accountId())
                .orElseThrow(() -> new MaintenanceFundApplicationException(
                        MaintenanceFundApplicationException.Reason.ACCOUNT_NOT_FOUND,
                        "维修资金账户不存在 accountId=" + cmd.accountId()));
        if (!cmd.tenantId().equals(account.tenantId())) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.TENANT_MISMATCH,
                    "维修资金账户与当前租户不匹配 accountId=" + cmd.accountId());
        }
        if (account.availableBalance().compareTo(cmd.amount()) < 0) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.INSUFFICIENT_AVAILABLE_BALANCE,
                    "信托制动账可用余额不足 accountId=" + cmd.accountId());
        }

        BigDecimal balanceAfter = account.totalBalance().subtract(cmd.amount());
        int updated = repository.debit(account.accountId(), cmd.amount(), account.version());
        if (updated != 1) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.CONCURRENT_MODIFICATION,
                    "维修资金账户已被并发修改 accountId=" + cmd.accountId());
        }
        LedgerEntry entry = new LedgerEntry(
                account.accountId(),
                BUSINESS_TYPE_TRUST_FUND_DISBURSEMENT,
                DIRECTION_CREDIT,
                cmd.amount(),
                balanceAfter,
                Instant.now(),
                cmd.trustPaymentId(),
                cmd.operatorId(),
                auditHash(cmd.tenantId(), cmd.accountId(), cmd.amount(), balanceAfter,
                        cmd.trustPaymentId(), cmd.operatorId(), account.version()));
        repository.insertLedgerEntry(entry);
        return entry;
    }

    private void validate(MaintenanceFundExpenseCommand cmd) {
        if (cmd == null || cmd.tenantId() == null || cmd.accountId() == null || cmd.operatorId() == null) {
            throw new IllegalArgumentException("MaintenanceFundExpenseCommand 字段不完整");
        }
        if (cmd.amount() == null || cmd.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.AMOUNT_INVALID,
                    "支取金额必须大于 0");
        }
    }

    private void validate(PublicRevenueTransferCommand cmd) {
        if (cmd == null || cmd.tenantId() == null || cmd.accountId() == null || cmd.operatorId() == null) {
            throw new IllegalArgumentException("PublicRevenueTransferCommand 字段不完整");
        }
        if (cmd.amount() == null || cmd.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.AMOUNT_INVALID,
                    "划拨金额必须大于 0");
        }
    }

    private void validate(TrustFundDisbursementCommand cmd) {
        if (cmd == null || cmd.tenantId() == null || cmd.accountId() == null
                || cmd.trustPaymentId() == null || cmd.operatorId() == null) {
            throw new IllegalArgumentException("TrustFundDisbursementCommand 字段不完整");
        }
        if (cmd.amount() == null || cmd.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.AMOUNT_INVALID,
                    "信托制动账金额必须大于 0");
        }
        if (cmd.installmentNo() == null || cmd.installmentNo() < 1) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.AMOUNT_INVALID,
                    "信托制分期序号必须大于等于 1");
        }
        if (cmd.installmentNo() > 1 && cmd.previousTrustPaymentId() == null) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.TRUST_PREVIOUS_INSTALLMENT_NOT_CONFIRMED,
                    "信托制第 " + cmd.installmentNo() + " 期动账必须指定前一期付款指令");
        }
    }

    private void ensurePreviousInstallmentConfirmed(TrustFundDisbursementCommand cmd) {
        if (cmd.installmentNo() <= 1) {
            return;
        }
        boolean previousLockUnlocked = governanceLockRepository.findByEntityForUpdate(
                        cmd.tenantId(), LockEntityType.TRUST_FUND_PAYMENT, cmd.previousTrustPaymentId())
                .filter(lock -> lock.isUnlocked())
                .isPresent();
        boolean previousLedgerConfirmed = repository.existsConfirmedLedgerEntry(
                cmd.accountId(), BUSINESS_TYPE_TRUST_FUND_DISBURSEMENT, cmd.previousTrustPaymentId());
        if (!previousLockUnlocked || !previousLedgerConfirmed) {
            throw new MaintenanceFundApplicationException(
                    MaintenanceFundApplicationException.Reason.TRUST_PREVIOUS_INSTALLMENT_NOT_CONFIRMED,
                    "信托制前一期付款尚未完成链上确认 previousTrustPaymentId=" + cmd.previousTrustPaymentId());
        }
    }

    private String auditHash(MaintenanceFundExpenseCommand cmd, Account account, BigDecimal balanceAfter) {
        return auditHash(cmd.tenantId(), account.accountId(), cmd.amount(), balanceAfter,
                cmd.businessRefId(), cmd.operatorId(), account.version());
    }

    private String auditHash(Long tenantId,
                             Long accountId,
                             BigDecimal amount,
                             BigDecimal balanceAfter,
                             Long businessRefId,
                             Long operatorId,
                             long version) {
        return PayloadHasher.sha256Hex(accountId + "|"
                + tenantId + "|"
                + amount.toPlainString() + "|"
                + balanceAfter.toPlainString() + "|"
                + businessRefId + "|"
                + operatorId + "|"
                + version);
    }
}
