package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.VotingMobilizationPermission;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.repository.VotingMobilizationPermissionRepository;
import com.pangu.infrastructure.persistence.entity.VotingMobilizationPermissionRow;
import com.pangu.infrastructure.persistence.mapper.VotingMobilizationPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VotingMobilizationPermissionRepositoryImpl implements VotingMobilizationPermissionRepository {

    private final VotingMobilizationPermissionMapper mapper;

    @Override
    public int activateForSubject(Long subjectId,
                                  Long tenantId,
                                  VotingScope scope,
                                  Long scopeReferenceId,
                                  Instant activatedAt,
                                  Instant expiresAt) {
        Integer scopeDb = scope == null ? null : scope.getDbValue();
        return mapper.activateForSubject(subjectId, tenantId, scopeDb, scopeReferenceId, activatedAt, expiresAt);
    }

    @Override
    public int deactivateForSubject(Long subjectId, Instant deactivatedAt) {
        return mapper.deactivateForSubject(subjectId, deactivatedAt);
    }

    @Override
    public List<VotingMobilizationPermission> findActiveBySubjectAndUser(Long subjectId,
                                                                         Long tenantId,
                                                                         Long userId,
                                                                         Instant now) {
        return mapper.selectActiveBySubjectAndUser(subjectId, tenantId, userId, now)
                .stream().map(this::toDomain).toList();
    }

    private VotingMobilizationPermission toDomain(VotingMobilizationPermissionRow row) {
        return VotingMobilizationPermission.builder()
                .permissionId(row.getPermissionId())
                .subjectId(row.getSubjectId())
                .tenantId(row.getTenantId())
                .buildingId(row.getBuildingId())
                .userId(row.getUserId())
                .roleKey(row.getRoleKey())
                .canRemind(Boolean.TRUE.equals(row.getCanRemind()))
                .canOfflineProxy(Boolean.TRUE.equals(row.getCanOfflineProxy()))
                .activatedAt(row.getActivatedAt())
                .expiresAt(row.getExpiresAt())
                .deactivatedAt(row.getDeactivatedAt())
                .status(row.getStatus() == null ? 0 : row.getStatus())
                .build();
    }
}
