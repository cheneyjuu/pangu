package com.pangu.bootstrap.rbac;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1.2 trigger 1/2/3（sys_user_role 三道法理红线）反例覆盖。
 *
 * <p>对应设计文档 §8 trigger 1-3，含义：
 * <ul>
 *     <li><b>Trigger 1</b>：role.allowed_dept_category 必须 = user.dept.dept_category；
 *         OWNER_REPRESENTATIVE/VOLUNTEER/GRID_OPERATOR 对 dept_type 有硬性要求。</li>
 *     <li><b>Trigger 2</b>（DEFERRED）：上述三个角色必须有 ≥1 条 sys_user_building 生效记录，
 *         否则 COMMIT 阶段抛错。</li>
 *     <li><b>Trigger 3</b>：role.fixed_data_scope NOT NULL 时，sys_user_role.effective_data_scope
 *         必须等于 fixed_data_scope（无法降级 / 升级）。</li>
 * </ul>
 *
 * <p>使用 TEST_TENANT_ID=99004，避免与 V1.1 求是小区（10001）/ V2.x 集成测试（99001-99003）冲突。
 */
@SpringBootTest
public class SysUserRoleTriggerTest {

    private static final long TEST_TENANT_ID = 99004L;

    /** 求是 V1.1 街道办 dept_id=1（cat=G, tenant=NULL）— 测试 dept 子树挂这个根上。 */
    private static final long ROOT_STREET_DEPT_ID = 1L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager txManager;

    private long deptParty;          // dept_type=6 党组织 G
    private long deptCommittee;      // dept_type=2 居委会 G
    private long deptGrid;           // dept_type=5 网格 G
    private long deptOwnerCom;       // dept_type=4 业委会 B
    private long deptOwnerRep;       // dept_type=11 业主代表团 B
    private long deptVolunteer;      // dept_type=10 志愿队 B
    private long deptProperty;       // dept_type=3 物业 S

    private long userOnGrid;         // dept_type=5
    private long userOnCommittee;    // dept_type=2
    private long userOnOwnerCom;     // dept_type=4
    private long userOnOwnerRep;     // dept_type=11
    private long userOnProperty;     // dept_type=3
    private long accountId;

    @BeforeEach
    public void setUp() {
        cleanUp();

        deptParty = insertDept(ROOT_STREET_DEPT_ID, "求是党组织-99004", 6, "G", TEST_TENANT_ID);
        deptCommittee = insertDept(deptParty, "求是居委会-99004", 2, "G", TEST_TENANT_ID);
        deptGrid = insertDept(deptCommittee, "求是网格-99004", 5, "G", TEST_TENANT_ID);
        deptOwnerCom = insertDept(deptParty, "求是业委会-99004", 4, "B", TEST_TENANT_ID);
        deptOwnerRep = insertDept(deptParty, "求是业主代表团-99004", 11, "B", TEST_TENANT_ID);
        deptVolunteer = insertDept(deptOwnerCom, "求是志愿队-99004", 10, "B", TEST_TENANT_ID);
        deptProperty = insertDept(deptParty, "求是物业-99004", 3, "S", TEST_TENANT_ID);

        accountId = insertAccount("99004000001");
        userOnGrid = insertSysUser(accountId, deptGrid, "test_grid_99004");
        userOnCommittee = insertSysUser(accountId, deptCommittee, "test_committee_99004");
        userOnOwnerCom = insertSysUser(accountId, deptOwnerCom, "test_ownercom_99004");
        userOnOwnerRep = insertSysUser(accountId, deptOwnerRep, "test_ownerrep_99004");
        userOnProperty = insertSysUser(accountId, deptProperty, "test_property_99004");
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM sys_user_role WHERE user_id IN ("
                        + "SELECT user_id FROM sys_user WHERE account_id IN ("
                        + "SELECT account_id FROM t_account WHERE phone LIKE '99004%'))");
        jdbcTemplate.update(
                "DELETE FROM sys_user_building WHERE user_id IN ("
                        + "SELECT user_id FROM sys_user WHERE account_id IN ("
                        + "SELECT account_id FROM t_account WHERE phone LIKE '99004%'))");
        jdbcTemplate.update(
                "DELETE FROM sys_user WHERE account_id IN ("
                        + "SELECT account_id FROM t_account WHERE phone LIKE '99004%')");
        jdbcTemplate.update("DELETE FROM t_account WHERE phone LIKE '99004%'");
        // sys_dept 父子约束：先删叶子（5/10/11/4/3），再删中间（2），最后删根（6）
        jdbcTemplate.update(
                "DELETE FROM sys_dept WHERE tenant_id = ? AND dept_type IN (3, 4, 5, 10, 11)",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM sys_dept WHERE tenant_id = ? AND dept_type = 2",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM sys_dept WHERE tenant_id = ? AND dept_type = 6",
                TEST_TENANT_ID);
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
    // Trigger 1 反例：role 的 allowed_dept_category 与 user.dept.dept_category 不一致
    // ===================================================================
    @Test
    public void trigger1_roleCategoryMismatch_gOnBDept_rejected() {
        // role_id=4 GRID_OPERATOR (cat=G) 不能挂在 cat=B 的业委会用户身上
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO sys_user_role(user_id, role_id, effective_data_scope) "
                                + "VALUES(?, 4, 'OWNER_GROUP')",
                        userOnOwnerCom));
        assertTrue(rootMessage(ex).contains("[trigger 1]"),
                "应抛 trigger 1 端归属不一致错误，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("GRID_OPERATOR"),
                "错误信息应提到 GRID_OPERATOR，实际：" + rootMessage(ex));
    }

    @Test
    public void trigger1_gridOperatorOnNonGridDept_rejected() {
        // role_id=4 GRID_OPERATOR 必须挂在 dept_type=5；这里挂到 dept_type=2 居委会上 → 拒绝
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO sys_user_role(user_id, role_id, effective_data_scope) "
                                + "VALUES(?, 4, 'OWNER_GROUP')",
                        userOnCommittee));
        assertTrue(rootMessage(ex).contains("[trigger 1]"),
                "应抛 trigger 1，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("dept_type=5"),
                "错误信息应提示 GRID_OPERATOR 必须挂 dept_type=5，实际：" + rootMessage(ex));
    }

    @Test
    public void trigger1_ownerRepresentativeOnNonRepDept_rejected() {
        // role_id=8 OWNER_REPRESENTATIVE 必须挂在 dept_type=11；挂到 dept_type=4 业委会上 → 拒绝
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO sys_user_role(user_id, role_id, effective_data_scope) "
                                + "VALUES(?, 8, 'OWNER_GROUP')",
                        userOnOwnerCom));
        assertTrue(rootMessage(ex).contains("[trigger 1]"),
                "应抛 trigger 1，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("dept_type=11"),
                "错误信息应提示 OWNER_REPRESENTATIVE 必须挂 dept_type=11，实际：" + rootMessage(ex));
    }

    // ===================================================================
    // Trigger 3 反例：fixed_data_scope NOT NULL 时锁死 effective_data_scope
    // ===================================================================
    @Test
    public void trigger3_gridOperatorEffectiveScopeViolatesFixed_rejected() {
        // role_id=4 GRID_OPERATOR fixed=OWNER_GROUP；尝试挂上去用 ALL_COMMUNITY → 拒绝
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO sys_user_role(user_id, role_id, effective_data_scope) "
                                + "VALUES(?, 4, 'ALL_COMMUNITY')",
                        userOnGrid));
        assertTrue(rootMessage(ex).contains("[trigger 3]"),
                "应抛 trigger 3 法理红线锁死，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("OWNER_GROUP"),
                "错误信息应包含 fixed_data_scope=OWNER_GROUP，实际：" + rootMessage(ex));
    }

    // ===================================================================
    // Trigger 2 反例（DEFERRED INITIALLY DEFERRED）：
    //   特殊角色（OWNER_REPRESENTATIVE/GRID_OPERATOR/VOLUNTEER）必须有 ≥1 sys_user_building，
    //   否则在 COMMIT 阶段抛错。Spring 通常包装成 TransactionSystemException。
    // ===================================================================
    @Test
    public void trigger2_ownerRepresentativeWithoutBuilding_throwsAtCommit() {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        // 在事务内 INSERT sys_user_role（不插 sys_user_building）→ commit 时 trigger 2 抛错
        Throwable ex = assertThrows(Throwable.class, () -> tt.executeWithoutResult(status ->
                jdbcTemplate.update(
                        "INSERT INTO sys_user_role(user_id, role_id, effective_data_scope) "
                                + "VALUES(?, 8, 'OWNER_GROUP')",
                        userOnOwnerRep)));
        // commit 阶段失败：可能是 TransactionSystemException 或下层 PSQL 异常包装
        boolean isExpectedKind = ex instanceof TransactionSystemException
                || ex instanceof DataAccessException;
        assertTrue(isExpectedKind, "期望 TransactionSystemException / DataAccessException，实际：" + ex.getClass());
        assertTrue(rootMessage(ex).contains("[trigger 2]"),
                "异常 root 消息应包含 [trigger 2]，实际：" + rootMessage(ex));
    }

    /** 提取异常链 root 的 message（PG SQLException 通常嵌在最深层）。 */
    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
