package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.AssignableUser;

import java.util.List;

/**
 * 可分配用户列表/搜索结果响应 DTO。
 *
 * <p>{@code phone} 由后端原样返回，前端按需脱敏展示。{@code complianceIssues}
 * 为空时表示合规，元素为 {@code "NOT_VERIFIED"} / {@code "BUILDING_LIMIT_REACHED"}。
 */
public record AssignableUserResponse(
        Long userId,
        String nickName,
        String roleKey,
        String phone,
        int realNameVerified,
        long buildingCount,
        List<String> complianceIssues) {

    public static AssignableUserResponse from(AssignableUser u) {
        return new AssignableUserResponse(
                u.userId(),
                u.nickName(),
                u.roleKey(),
                u.phone(),
                u.realNameVerified(),
                u.buildingCount(),
                u.complianceIssues());
    }
}
