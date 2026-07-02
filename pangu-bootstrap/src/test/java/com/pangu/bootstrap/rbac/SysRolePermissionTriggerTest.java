package com.pangu.bootstrap.rbac;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1.4 trigger 6/7 反例覆盖（sys_role_permission 一致性 + sys_role 系统角色不可删）。
 *
 * <ul>
 *     <li><b>Trigger 6</b>：role.allowed_dept_category 必须出现在 permission.allowed_dept_categories；
 *         is_legal_redline=1 时 role.fixed_data_scope 必须 NOT NULL。</li>
 *     <li><b>Trigger 7</b>：is_system=1 的预置角色禁止 DELETE。</li>
 * </ul>
 *
 * <p>测试不直接修改 V1.4 的预置 13 个角色 / 17 个 permission，
 * 只通过新建临时角色（is_system=0）+ 已存在的红线 / 非红线 permission 来验证 trigger 6 反例；
 * trigger 7 用 role_id=4 GRID_MEMBER（is_system=1）做 DELETE 反例。
 */
@SpringBootTest
public class SysRolePermissionTriggerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long tempRoleId;

    @BeforeEach
    public void setUp() {
        cleanUp();
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        // 删除测试新建的临时角色（role_key 前缀 'TEST_'）
        jdbcTemplate.update(
                "DELETE FROM sys_role_permission WHERE role_id IN ("
                        + "SELECT role_id FROM sys_role WHERE role_key LIKE 'TEST_%')");
        // is_system=0 的临时角色不会被 trigger 7 拦
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_key LIKE 'TEST_%'");
        tempRoleId = null;
    }

    /** 创建一个临时（is_system=0）的自建角色。 */
    private long insertTempRole(String key, String allowedCat, String fixedScope, String defaultScope) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO sys_role(role_name, role_key, allowed_dept_category, fixed_data_scope, "
                        + "default_data_scope, is_system) VALUES(?, ?, ?, ?, ?, 0) RETURNING role_id",
                Long.class, key + "_name", key, allowedCat, fixedScope, defaultScope);
    }

    // ===================================================================
    // Trigger 6：role 的端归属必须在 permission.allowed_dept_categories 字符串中
    //   waiver:approve:committee allowed='G'，非 G 端角色不可挂
    // ===================================================================
    @Test
    public void trigger6_roleCategoryNotInPermissionAllowed_rejected() {
        // 新建一个 B 端临时角色 + fixed_data_scope NOT NULL（满足 redline 前置）
        tempRoleId = insertTempRole("TEST_B_FIXED", "B", "ALL_COMMUNITY", "ALL_COMMUNITY");

        // waiver:approve:committee allowed='G'，B 端不能挂
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO sys_role_permission(role_id, permission_key) "
                                + "VALUES(?, 'waiver:approve:committee')",
                        tempRoleId));
        assertTrue(rootMessage(ex).contains("[trigger 6]"),
                "应抛 trigger 6，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("端归属不一致")
                        || rootMessage(ex).contains("不一致"),
                "错误信息应提到端归属不一致，实际：" + rootMessage(ex));
    }

    @Test
    public void trigger6_redlinePermissionRequiresFixedScope_rejected() {
        // 新建一个 G 端但 fixed_data_scope=NULL 的临时角色
        tempRoleId = insertTempRole("TEST_G_NOFIXED", "G", null, "ALL_COMMUNITY");

        // waiver:approve:committee 是 redline=1 → fixed_data_scope NULL 应被拒
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO sys_role_permission(role_id, permission_key) "
                                + "VALUES(?, 'waiver:approve:committee')",
                        tempRoleId));
        assertTrue(rootMessage(ex).contains("[trigger 6]"),
                "应抛 trigger 6，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("法理红线") || rootMessage(ex).contains("redline")
                        || rootMessage(ex).contains("fixed_data_scope"),
                "错误信息应提到法理红线 / fixed_data_scope，实际：" + rootMessage(ex));
    }

    @Test
    public void trigger6_nonRedlinePermissionOnFixedNullRole_allowed() {
        // 反向验证：非红线 permission（waiver:read allowed=GBS, redline=0）允许 fixed=NULL 的 G 端角色挂
        tempRoleId = insertTempRole("TEST_G_NOFIXED_NONRED", "G", null, "ALL_COMMUNITY");
        // 不应抛 → assertDoesNotThrow 隐式断言
        int updated = jdbcTemplate.update(
                "INSERT INTO sys_role_permission(role_id, permission_key) "
                        + "VALUES(?, 'waiver:read')",
                tempRoleId);
        assertTrue(updated == 1, "非红线 permission 挂 fixed=NULL 角色应允许");
    }

    // ===================================================================
    // Trigger 7：is_system=1 的预置角色禁止 DELETE
    // ===================================================================
    @Test
    public void trigger7_systemRoleCannotBeDeleted_rejected() {
        // 直接尝试删除 role_id=4 GRID_MEMBER (is_system=1)
        // 注意：role 是被 sys_user_role 引用的，外键 ON DELETE CASCADE 但 trigger 7 BEFORE DELETE 先抛
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("DELETE FROM sys_role WHERE role_id = 4"));
        assertTrue(rootMessage(ex).contains("[trigger 7]"),
                "应抛 trigger 7 系统角色禁删，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("GRID_MEMBER")
                        || rootMessage(ex).contains("系统角色")
                        || rootMessage(ex).contains("不可删除"),
                "错误信息应提到 GRID_MEMBER 或系统角色不可删除，实际：" + rootMessage(ex));
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
