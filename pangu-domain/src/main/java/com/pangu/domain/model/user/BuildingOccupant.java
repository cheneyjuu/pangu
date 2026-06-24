package com.pangu.domain.model.user;

/**
 * 单个楼栋占用者（{@link BuildingOccupancy} 的元素）。
 *
 * @param userId   sys_user.user_id
 * @param nickName 展示名
 * @param roleKey  角色键
 */
public record BuildingOccupant(
        Long userId,
        String nickName,
        String roleKey) {
}
