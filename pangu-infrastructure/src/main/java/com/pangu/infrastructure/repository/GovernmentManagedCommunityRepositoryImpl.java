package com.pangu.infrastructure.repository;

import com.pangu.domain.model.community.GovernmentManagedCommunity;
import com.pangu.domain.repository.GovernmentManagedCommunityRepository;
import com.pangu.infrastructure.persistence.mapper.UserContextMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 关联业务：将政府组织树和小区管辖授权转换为管理端可切换的小区范围。
 */
@Repository
@RequiredArgsConstructor
public class GovernmentManagedCommunityRepositoryImpl implements GovernmentManagedCommunityRepository {

    private final UserContextMapper userContextMapper;

    @Override
    public List<GovernmentManagedCommunity> listManagedCommunities(Long governmentDeptId) {
        if (governmentDeptId == null) {
            return List.of();
        }
        return userContextMapper.selectManagedCommunitiesByGovernmentDept(governmentDeptId).stream()
                .map(row -> new GovernmentManagedCommunity(
                        row.getTenantId(),
                        row.getTenantName(),
                        row.getPlannedHouseholdCount(),
                        row.getTotalExclusiveArea(),
                        row.getGovernanceStatus()))
                .toList();
    }

    @Override
    public boolean canManageCommunity(Long governmentDeptId, Long tenantId) {
        return governmentDeptId != null
                && tenantId != null
                && userContextMapper.existsManagedCommunityByGovernmentDept(governmentDeptId, tenantId);
    }
}
