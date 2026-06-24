package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

/**
 * 楼栋占用者行（{@code sys_user_building} JOIN {@code sys_user_role} + {@code sys_user}）。
 */
@Data
public class BuildingOccupantRow {
    private Long userId;
    private String nickName;
    private String roleKey;
}
