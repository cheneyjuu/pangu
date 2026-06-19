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
 * V1.2 trigger 4/5 反例覆盖（sys_dept + sys_user_building 多租户一致性）。
 *
 * <ul>
 *     <li><b>Trigger 4</b>：sys_dept.parent_id 的 tenant_id 必须为 NULL（跨租户根）或与自身相等。</li>
 *     <li><b>Trigger 5</b>：sys_user_building.tenant_id 必须 = user.dept.tenant_id；
 *         街道办用户（dept.tenant_id=NULL）禁止任命楼栋。</li>
 * </ul>
 *
 * <p>使用 TEST_TENANT_ID=99005 / 99006，避免与其它测试冲突。
 */
@SpringBootTest
public class SysDeptTriggerTest {

    private static final long TENANT_A = 99005L;
    private static final long TENANT_B = 99006L;
    private static final long ROOT_STREET_DEPT_ID = 1L;  // V1.1 街道办（tenant_id=NULL）

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() {
        cleanUp();
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM sys_user_building WHERE tenant_id IN (?, ?)",
                TENANT_A, TENANT_B);
        jdbcTemplate.update(
                "DELETE FROM sys_user WHERE account_id IN ("
                        + "SELECT account_id FROM t_account WHERE phone LIKE '99005%' OR phone LIKE '99006%')");
        jdbcTemplate.update(
                "DELETE FROM t_account WHERE phone LIKE '99005%' OR phone LIKE '99006%'");
        // 顺序：5/10/11/4/3 → 2 → 6
        jdbcTemplate.update(
                "DELETE FROM sys_dept WHERE tenant_id IN (?, ?) AND dept_type IN (3, 4, 5, 10, 11)",
                TENANT_A, TENANT_B);
        jdbcTemplate.update(
                "DELETE FROM sys_dept WHERE tenant_id IN (?, ?) AND dept_type = 2",
                TENANT_A, TENANT_B);
        jdbcTemplate.update(
                "DELETE FROM sys_dept WHERE tenant_id IN (?, ?) AND dept_type = 6",
                TENANT_A, TENANT_B);
    }

    private long insertDept(Long parentId, String name, int deptType, String cat, Long tenantId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO sys_dept(parent_id, ancestors, dept_name, dept_type, dept_category, tenant_id) "
                        + "VALUES(?, '', ?, ?, ?, ?) RETURNING dept_id",
                Long.class, parentId, name, deptType, cat, tenantId);
    }

    private long insertAccount(String phone) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_account(phone, real_name, real_name_verified, status) "
                        + "VALUES(?, '测试用户', 1, 1) RETURNING account_id",
                Long.class, phone);
    }

    private long insertSysUser(long accountId, long deptId, String userName) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO sys_user(account_id, dept_id, user_name, status) "
                        + "VALUES(?, ?, ?, '0') RETURNING user_id",
                Long.class, accountId, deptId, userName);
    }

    // ===================================================================
    // Trigger 4：parent.tenant_id 与 child.tenant_id 必须一致（除非 parent 跨租户根）
    // ===================================================================
    @Test
    public void trigger4_childTenantMismatchParent_rejected() {
        // 先建 TENANT_A 党组织（cat=G, tenant=A）做父
        long partyA = insertDept(ROOT_STREET_DEPT_ID, "TENANT_A 党组织", 6, "G", TENANT_A);
        // 在 partyA 下塞一个 tenant_id=B 的居委会 → trigger 4 应拒绝
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                insertDept(partyA, "TENANT_B 居委会-错配", 2, "G", TENANT_B));
        assertTrue(rootMessage(ex).contains("[trigger 4]"),
                "应抛 trigger 4 tenant 不一致，实际：" + rootMessage(ex));
    }

    @Test
    public void trigger4_parentIsCrossTenantRoot_anyChildTenantAllowed() {
        // parent=街道办 (tenant_id IS NULL, 跨租户根) → 任意 tenant 子节点允许
        // 这里只 assertDoesNotThrow（直接执行不抛即可）
        long partyA = insertDept(ROOT_STREET_DEPT_ID, "TENANT_A 党组织(P=root)", 6, "G", TENANT_A);
        long partyB = insertDept(ROOT_STREET_DEPT_ID, "TENANT_B 党组织(P=root)", 6, "G", TENANT_B);
        assertTrue(partyA > 0 && partyB > 0, "跨租户根下任意 tenant 子节点应允许");
    }

    // ===================================================================
    // Trigger 5：sys_user_building.tenant_id 必须 = user.dept.tenant_id
    //   街道办用户（dept.tenant_id IS NULL）禁止任命楼栋
    // ===================================================================
    @Test
    public void trigger5_buildingTenantMismatchUserDeptTenant_rejected() {
        // 用 TENANT_A 党组织 → 居委会 → 网格，挂一个网格员；
        // 试图给该网格员任命一个 tenant_id=B 的楼栋 → 拒绝
        long partyA = insertDept(ROOT_STREET_DEPT_ID, "A 党组织", 6, "G", TENANT_A);
        long committeeA = insertDept(partyA, "A 居委会", 2, "G", TENANT_A);
        long gridA = insertDept(committeeA, "A 网格", 5, "G", TENANT_A);
        long account = insertAccount("99005000001");
        long userId = insertSysUser(account, gridA, "trigger5_user_a");

        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO sys_user_building(user_id, building_id, tenant_id, assigned_by, status) "
                                + "VALUES(?, ?, ?, NULL, 1)",
                        userId, 999991L, TENANT_B));
        assertTrue(rootMessage(ex).contains("[trigger 5]"),
                "应抛 trigger 5 tenant 不一致，实际：" + rootMessage(ex));
    }

    @Test
    public void trigger5_streetUserCannotBeAssignedBuilding_rejected() {
        // 街道办用户：account_id → user 直接挂在 dept_id=1（街道办，tenant_id=NULL）
        long account = insertAccount("99006000001");
        long userId = insertSysUser(account, ROOT_STREET_DEPT_ID, "trigger5_street_user");

        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO sys_user_building(user_id, building_id, tenant_id, assigned_by, status) "
                                + "VALUES(?, ?, ?, NULL, 1)",
                        userId, 999992L, TENANT_A));
        assertTrue(rootMessage(ex).contains("[trigger 5]"),
                "应抛 trigger 5 街道办禁任命楼栋，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("街道办") || rootMessage(ex).contains("NULL"),
                "错误信息应提到街道办或 NULL，实际：" + rootMessage(ex));
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
