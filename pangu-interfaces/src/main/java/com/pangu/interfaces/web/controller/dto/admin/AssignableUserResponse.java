package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.AssignableUser;

/**
 * 可分配用户列表响应 DTO（楼栋责任田分配页左栏）。
 */
public record AssignableUserResponse(
        Long userId,
        String nickName,
        String roleKey,
        long buildingCount) {

    public static AssignableUserResponse from(AssignableUser u) {
        return new AssignableUserResponse(u.userId(), u.nickName(), u.roleKey(), u.buildingCount());
    }
}
