package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 更新网格组织节点的楼栋范围。
 */
public record UpdateGridBuildingScopeRequest(
        List<Long> buildingIds,
        List<@Valid BuildingScopeItem> buildingScopes) {

    public List<WorkIdentityBuildingScope> toDomainScopes(Long fallbackTenantId) {
        if (buildingScopes != null && !buildingScopes.isEmpty()) {
            return buildingScopes.stream()
                    .map(item -> new WorkIdentityBuildingScope(item.tenantId(), item.buildingId()))
                    .toList();
        }
        if (buildingIds == null) {
            return List.of();
        }
        return buildingIds.stream()
                .map(buildingId -> new WorkIdentityBuildingScope(fallbackTenantId, buildingId))
                .toList();
    }

    public record BuildingScopeItem(
            @NotNull Long tenantId,
            @NotNull Long buildingId) {
    }
}
