package com.pangu.domain.model.role;

import java.time.Instant;

/**
 * 角色列表行（读侧视图，framework-light record）。
 *
 * <p>区别于 {@link SysRole}（写侧 CRUD 用的 6 字段值对象），本 record 服务于
 * 管理台角色分页列表：在 {@code SysRole} 字段基础上补 {@code status / createTime}
 *（写侧转 aggregate 时丢弃，列表页需要展示），并附带 {@code permissionCount}
 *（由 {@code sys_role_permission} 子查询 COUNT 聚合，避免 N+1）。
 *
 * @param roleId             主键
 * @param roleKey            业务键
 * @param roleName           展示名
 * @param allowedDeptCategory 端归属 'G'/'B'/'S'
 * @param fixedDataScope     法理红线锁死的 data scope；可空
 * @param defaultDataScope   默认 data scope
 * @param isSystem           1=预置 / 0=自定义
 * @param status             '0' 正常 / '1' 停用
 * @param permissionCount    已授 permission 数
 * @param createTime         创建时间
 */
public record RoleListItem(
        Long roleId,
        String roleKey,
        String roleName,
        String allowedDeptCategory,
        String fixedDataScope,
        String defaultDataScope,
        int isSystem,
        String status,
        long permissionCount,
        Instant createTime) {
}
