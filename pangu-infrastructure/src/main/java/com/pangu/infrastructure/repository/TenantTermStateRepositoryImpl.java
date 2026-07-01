package com.pangu.infrastructure.repository;

import com.pangu.domain.model.handover.TenantTermState;
import com.pangu.domain.model.handover.TenantTermStatus;
import com.pangu.domain.repository.TenantTermStateRepository;
import com.pangu.infrastructure.persistence.entity.TenantTermStateRow;
import com.pangu.infrastructure.persistence.mapper.TenantTermStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TenantTermStateRepositoryImpl implements TenantTermStateRepository {

    private final TenantTermStateMapper mapper;

    @Override
    public Optional<TenantTermState> findByTenantId(Long tenantId) {
        return Optional.ofNullable(mapper.selectByTenantId(tenantId)).map(this::toDomain);
    }

    @Override
    public void engageHandoverLock(Long tenantId, Long subjectId) {
        mapper.upsertHandoverLock(tenantId, subjectId);
    }

    @Override
    public void releaseHandoverLock(Long tenantId, Long confirmedByUserId) {
        mapper.releaseHandoverLock(tenantId, confirmedByUserId);
    }

    private TenantTermState toDomain(TenantTermStateRow row) {
        return new TenantTermState(
                row.getTenantId(),
                TenantTermStatus.fromDbValue(row.getTermStatus()),
                row.getTermLockedBySubjectId(),
                row.getTermLockedAt(),
                row.getTermUnlockedAt(),
                row.getTermUnlockedByUserId());
    }
}
