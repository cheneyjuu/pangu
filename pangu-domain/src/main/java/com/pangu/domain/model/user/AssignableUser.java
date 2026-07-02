package com.pangu.domain.model.user;

import java.util.List;

/**
 * 可分配用户列表/搜索结果行。
 *
 * <p>某可分配角色（GRID_MEMBER / VOLUNTEER / OWNER_REPRESENTATIVE）下的用户，
 * 附当前已生效楼栋数 {@link #buildingCount} 与合规快照 {@link #complianceIssues}
 * 供前端 badge 与合规检查 Dialog 直接驱动。
 *
 * <p>{@code complianceIssues} 为空表示全部合规；元素为 application 层定义的
 * 不合规原因（如 {@code "NOT_VERIFIED"} / {@code "BUILDING_LIMIT_REACHED"}），
 * 前端按原因展示对应红 ✗。
 *
 * @param userId           sys_user.user_id
 * @param nickName         展示名
 * @param roleKey          角色键（如 GRID_MEMBER）
 * @param phone            t_account.phone（前端展示时按需脱敏）
 * @param realNameVerified t_account.real_name_verified（0/1）
 * @param buildingCount    已生效楼栋数
 * @param complianceIssues 不合规原因列表；空表示合规
 */
public record AssignableUser(
        Long userId,
        String nickName,
        String roleKey,
        String phone,
        int realNameVerified,
        long buildingCount,
        List<String> complianceIssues) {

    public AssignableUser {
        complianceIssues = complianceIssues == null ? List.of() : List.copyOf(complianceIssues);
    }
}
