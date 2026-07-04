package com.pangu.domain.model.user;

/**
 * 网格或工作身份可选择的物理楼栋范围。
 *
 * <p>{@code buildingId} 不是全系统唯一，跨小区网格必须同时携带 {@code tenantId}
 * 才能区分 A 小区 1 号楼与 B 小区 1 号楼。
 */
public record WorkIdentityBuildingScope(
        Long tenantId,
        Long buildingId) {

    public Long getTenantId() {
        return tenantId;
    }

    public Long getBuildingId() {
        return buildingId;
    }
}
