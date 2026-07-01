package com.pangu.domain.repository;

import com.pangu.domain.model.handover.TenantTermState;

import java.util.Optional;

/**
 * 租户任期状态仓储端口。
 */
public interface TenantTermStateRepository {

    Optional<TenantTermState> findByTenantId(Long tenantId);

    void engageHandoverLock(Long tenantId, Long subjectId);

    void releaseHandoverLock(Long tenantId, Long confirmedByUserId);
}
