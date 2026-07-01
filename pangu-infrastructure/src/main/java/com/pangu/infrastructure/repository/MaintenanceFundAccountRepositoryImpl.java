package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.MaintenanceFundAccountRepository;
import com.pangu.infrastructure.persistence.entity.MaintenanceFundAccountRow;
import com.pangu.infrastructure.persistence.mapper.MaintenanceFundAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MaintenanceFundAccountRepositoryImpl implements MaintenanceFundAccountRepository {

    private final MaintenanceFundAccountMapper mapper;

    @Override
    public Optional<Account> findByIdForUpdate(Long accountId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(accountId)).map(this::toDomain);
    }

    @Override
    public int debit(Long accountId, java.math.BigDecimal amount, long expectedVersion) {
        return mapper.debit(accountId, amount, expectedVersion);
    }

    @Override
    public int credit(Long accountId, java.math.BigDecimal amount, long expectedVersion) {
        return mapper.credit(accountId, amount, expectedVersion);
    }

    @Override
    public void insertLedgerEntry(LedgerEntry entry) {
        mapper.insertLedgerEntry(
                entry.accountId(),
                entry.businessType(),
                entry.direction(),
                entry.amount(),
                entry.balanceAfter(),
                entry.occurredAt(),
                entry.businessRefId(),
                entry.operatorId(),
                entry.auditHash());
    }

    @Override
    public boolean existsConfirmedLedgerEntry(Long accountId, int businessType, Long businessRefId) {
        return mapper.countConfirmedLedgerEntry(accountId, businessType, businessRefId) > 0;
    }

    private Account toDomain(MaintenanceFundAccountRow row) {
        return new Account(
                row.getAccountId(),
                row.getTenantId(),
                row.getTotalBalance(),
                row.getFrozenBalance(),
                row.getVersion());
    }
}
