package com.pangu.domain.model.user;

import java.time.Instant;

/**
 * 楼栋责任田分配值对象（domain 层 record）。
 *
 * <p>对应 {@code sys_user_building} 一行——网格员 / 业主代表 / 志愿者（OWNER_GROUP
 * 数据范围）的管辖楼栋权威来源。{@link #status} 1=生效 / 2=已撤销；撤销后可复活。
 *
 * @param assignmentId 主键，新建时为 {@code null}
 * @param userId       sys_user.user_id（被分配人）
 * @param buildingId   c_owner_property.building_id（无独立楼栋表，裸数字 id）
 * @param tenantId     租户
 * @param assignedBy   分配人 sys_user.user_id
 * @param assignedAt   分配时间
 * @param status       1=生效, 2=已撤销
 */
public record BuildingAssignment(
        Long assignmentId,
        Long userId,
        Long buildingId,
        Long tenantId,
        Long assignedBy,
        Instant assignedAt,
        int status) {
}
