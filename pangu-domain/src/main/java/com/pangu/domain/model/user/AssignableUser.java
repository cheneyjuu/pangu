package com.pangu.domain.model.user;

/**
 * 可分配用户列表行（楼栋责任田分配页左栏）。
 *
 * <p>某可分配角色（GRID_OPERATOR / VOLUNTEER / OWNER_REPRESENTATIVE）下的用户，
 * 附当前已生效楼栋数 {@link #buildingCount} 供列表 badge 展示。
 *
 * @param userId        sys_user.user_id
 * @param nickName      展示名
 * @param roleKey       角色键（如 GRID_OPERATOR）
 * @param buildingCount 已生效楼栋数
 */
public record AssignableUser(
        Long userId,
        String nickName,
        String roleKey,
        long buildingCount) {
}
