package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

/**
 * 可分配用户列表行（{@code sys_user} JOIN {@code sys_user_role} 聚合）。
 *
 * <p>{@code buildingCount} 为该用户已生效楼栋数（子查询聚合），可能为 null。
 */
@Data
public class AssignableUserRow {
    private Long userId;
    private String nickName;
    private String roleKey;
    private Long buildingCount;
}
