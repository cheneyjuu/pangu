package com.pangu.infrastructure.repository;

import com.pangu.domain.model.user.AssignableUser;
import com.pangu.domain.model.user.AssignedBuildingSummary;
import com.pangu.domain.model.user.BuildingAssignment;
import com.pangu.domain.model.user.BuildingOccupant;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import com.pangu.domain.repository.BuildingAssignmentRepository;
import com.pangu.infrastructure.persistence.entity.AssignableUserRow;
import com.pangu.infrastructure.persistence.entity.AssignedBuildingSummaryRow;
import com.pangu.infrastructure.persistence.entity.BuildingAssignmentRow;
import com.pangu.infrastructure.persistence.entity.BuildingOccupantRow;
import com.pangu.infrastructure.persistence.mapper.BuildingAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link BuildingAssignmentRepository} 默认实现。
 *
 * <p>{@link #assign} 幂等编排：先 {@link BuildingAssignmentMapper#selectExisting} ——
 * <ul>
 *   <li>无记录 → insert（status=1）；</li>
 *   <li>已生效（status=1）→ noop 返回 1；</li>
 *   <li>已撤销（status=2）→ reactivate（status 2→1，刷新分配人/时间）。</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class BuildingAssignmentRepositoryImpl implements BuildingAssignmentRepository {

    private final BuildingAssignmentMapper mapper;

    @Override
    public List<AssignableUser> listUsersByRole(String roleKey, Long tenantId) {
        return mapper.selectUsersByRole(roleKey, tenantId).stream()
                .map(this::toAssignableUser).toList();
    }

    @Override
    public List<AssignableUser> searchAssignableUsers(String keyword, Long tenantId, int limit) {
        return mapper.searchUsers(keyword, tenantId, limit).stream()
                .map(this::toAssignableUser).toList();
    }

    @Override
    public List<BuildingOccupant> listOccupants(Long buildingId, Long tenantId) {
        return mapper.selectOccupantsByBuilding(buildingId, tenantId).stream()
                .map(this::toOccupant).toList();
    }

    @Override
    public List<Long> listBuildings(Long tenantId) {
        return mapper.selectDistinctBuildings(tenantId);
    }

    @Override
    public List<BuildingAssignment> listAssignmentsByUser(Long userId) {
        return mapper.selectAssignmentsByUser(userId).stream()
                .map(this::toAssignment).toList();
    }

    @Override
    public List<AssignedBuildingSummary> listBuildingSummaries(Set<WorkIdentityBuildingScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        return mapper.selectBuildingSummariesByScopes(new ArrayList<>(scopes)).stream()
                .map(this::toAssignedBuildingSummary).toList();
    }

    @Override
    public int assign(Long userId, Long buildingId, Long tenantId, Long assignedBy) {
        BuildingAssignmentRow existing = mapper.selectExisting(userId, buildingId);
        if (existing == null) {
            mapper.insertAssignment(userId, buildingId, tenantId, assignedBy);
            return 1;
        }
        if (existing.getStatus() != null && existing.getStatus() == 1) {
            // 已生效 → 幂等 noop
            return 1;
        }
        // 已撤销 → 复活
        mapper.reactivateAssignment(existing.getAssignmentId(), assignedBy);
        return 1;
    }

    @Override
    public int revoke(Long userId, Long buildingId) {
        return mapper.revokeAssignment(userId, buildingId, null);
    }

    @Override
    public boolean buildingExistsInTenant(Long buildingId, Long tenantId) {
        if (tenantId == null) {
            // 街道超管跨租户俯瞰 → 跳过租户归属校验
            return true;
        }
        return mapper.existsBuildingInTenant(buildingId, tenantId);
    }

    @Override
    public boolean userHasAssignableRole(Long userId, String roleKey, Long tenantId) {
        return mapper.userHasRoleInTenant(userId, roleKey, tenantId);
    }

    private AssignableUser toAssignableUser(AssignableUserRow row) {
        // complianceIssues 计算放在 application 层（service 拿到合规阈值常量），
        // 这里只透传原始字段（phone/realNameVerified/buildingCount）。
        return new AssignableUser(
                row.getUserId(),
                row.getNickName(),
                row.getRoleKey(),
                row.getPhone(),
                row.getRealNameVerified() == null ? 0 : row.getRealNameVerified(),
                row.getBuildingCount() == null ? 0L : row.getBuildingCount(),
                List.of());
    }

    private BuildingOccupant toOccupant(BuildingOccupantRow row) {
        return new BuildingOccupant(row.getUserId(), row.getNickName(), row.getRoleKey());
    }

    private BuildingAssignment toAssignment(BuildingAssignmentRow row) {
        return new BuildingAssignment(
                row.getAssignmentId(),
                row.getUserId(),
                row.getBuildingId(),
                row.getTenantId(),
                row.getAssignedBy(),
                row.getAssignedAt(),
                row.getStatus() == null ? 0 : row.getStatus());
    }

    private AssignedBuildingSummary toAssignedBuildingSummary(AssignedBuildingSummaryRow row) {
        return new AssignedBuildingSummary(
                row.getBuildingId(),
                row.getUnitCount() == null ? 0 : row.getUnitCount(),
                row.getReminderCompletionRate());
    }
}
