package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

/**
 * 可分配用户列表/搜索结果行（{@code sys_user} JOIN {@code sys_user_role} JOIN
 * {@code t_account} 聚合）。
 *
 * <p>{@code buildingCount} 为子查询聚合的已生效楼栋数，可能为 null。
 * {@code phone} / {@code realNameVerified} 来自 t_account，供合规检查与展示用。
 */
@Data
public class AssignableUserRow {
    private Long userId;
    private String nickName;
    private String roleKey;
    private String phone;
    private Integer realNameVerified;
    private Long buildingCount;
}
