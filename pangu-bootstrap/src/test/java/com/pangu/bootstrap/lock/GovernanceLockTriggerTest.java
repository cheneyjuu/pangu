package com.pangu.bootstrap.lock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2.5 trigger 8（{@code fn_governance_lock_unlock_atomicity}）反例覆盖。
 *
 * <p>规则要点（详见 V2.5 SQL）：
 * <ul>
 *   <li>(a) committee_user_id 与 committee_at 必须同时填或同时空；</li>
 *   <li>(b) street_user_id 与 street_at 必须同时填或同时空；</li>
 *   <li>(c) unlock_at 当且仅当委员会 + 街道办双签齐备时方可填充；</li>
 *   <li>(d) status 与字段填充情况一致（LOCKED 不应有任何 unlock 字段；
 *       COMMITTEE_SIGNED 要求 committee 齐备且 street 必为空；
 *       FULLY_UNLOCKED 必有 unlock_at）；</li>
 *   <li>(e) UPDATE 路径上 status 不可逆。</li>
 * </ul>
 *
 * <p>本测试通过 {@link JdbcTemplate} 直接绕过聚合根触达 DB，验证 trigger 兜底；
 * 全部反例期望抛出 {@link DataAccessException} 且根因 message 含 {@code [trigger 8]}。
 */
@SpringBootTest
public class GovernanceLockTriggerTest {

    /** 与并发测试隔离，使用独立 tenant_id。 */
    private static final long TEST_TENANT_ID = 99201L;
    private static final String HASH64 = "b".repeat(64);

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
        jdbcTemplate.update("DELETE FROM t_governance_lock WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    // ===== (a) committee 字段半填 =====

    @Test
    public void trigger8_committeeUserIdWithoutCommitteeAt_rejected() {
        // status 维持 1 也不行：committee_user_id 单独填 → (a) 拦下
        // 但 status=1 + 任何 unlock 字段已被 (d) 拦——两者都报 [trigger 8]，断言总信息即可
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_governance_lock(tenant_id, entity_type, entity_id, "
                                + "locked_by_user_id, lock_payload_hash, status, "
                                + "unlock_committee_user_id) "
                                + "VALUES(?, 'FINANCE_DISCLOSURE', 1001, 7001, ?, 2, 7002)",
                        TEST_TENANT_ID, HASH64));
        assertTrue(rootMessage(ex).contains("[trigger 8]"),
                "应抛 trigger 8，实际：" + rootMessage(ex));
    }

    // ===== (b) street 字段半填 =====

    @Test
    public void trigger8_streetAtWithoutStreetUserId_rejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_governance_lock(tenant_id, entity_type, entity_id, "
                                + "locked_by_user_id, lock_payload_hash, status, "
                                + "unlock_committee_user_id, unlock_committee_at, "
                                + "unlock_committee_signature, unlock_street_at) "
                                + "VALUES(?, 'FINANCE_DISCLOSURE', 1002, 7001, ?, 2, "
                                + "7002, CURRENT_TIMESTAMP, 'sig-c', CURRENT_TIMESTAMP)",
                        TEST_TENANT_ID, HASH64));
        assertTrue(rootMessage(ex).contains("[trigger 8]"),
                "应抛 trigger 8，实际：" + rootMessage(ex));
    }

    // ===== (c) unlock_at 仅当双签齐备 =====

    @Test
    public void trigger8_unlockAtSetButOnlyCommitteeSigned_rejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_governance_lock(tenant_id, entity_type, entity_id, "
                                + "locked_by_user_id, lock_payload_hash, status, "
                                + "unlock_committee_user_id, unlock_committee_at, "
                                + "unlock_committee_signature, unlock_at) "
                                + "VALUES(?, 'FINANCE_DISCLOSURE', 1003, 7001, ?, 2, "
                                + "7002, CURRENT_TIMESTAMP, 'sig-c', CURRENT_TIMESTAMP)",
                        TEST_TENANT_ID, HASH64));
        assertTrue(rootMessage(ex).contains("[trigger 8]"),
                "应抛 trigger 8，实际：" + rootMessage(ex));
    }

    @Test
    public void trigger8_dualSignedButUnlockAtMissing_rejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_governance_lock(tenant_id, entity_type, entity_id, "
                                + "locked_by_user_id, lock_payload_hash, status, "
                                + "unlock_committee_user_id, unlock_committee_at, "
                                + "unlock_committee_signature, "
                                + "unlock_street_user_id, unlock_street_at, "
                                + "unlock_street_signature) "
                                + "VALUES(?, 'FINANCE_DISCLOSURE', 1004, 7001, ?, 3, "
                                + "7002, CURRENT_TIMESTAMP, 'sig-c', "
                                + "8001, CURRENT_TIMESTAMP, 'sig-s')",
                        TEST_TENANT_ID, HASH64));
        assertTrue(rootMessage(ex).contains("[trigger 8]"),
                "应抛 trigger 8，实际：" + rootMessage(ex));
    }

    // ===== (d) status=1 不应有 unlock 字段 =====

    @Test
    public void trigger8_lockedStateWithUnlockFields_rejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_governance_lock(tenant_id, entity_type, entity_id, "
                                + "locked_by_user_id, lock_payload_hash, status, "
                                + "unlock_committee_user_id, unlock_committee_at) "
                                + "VALUES(?, 'FINANCE_DISCLOSURE', 1005, 7001, ?, 1, "
                                + "7002, CURRENT_TIMESTAMP)",
                        TEST_TENANT_ID, HASH64));
        assertTrue(rootMessage(ex).contains("[trigger 8]"),
                "应抛 trigger 8，实际：" + rootMessage(ex));
    }

    // ===== (e) status 不可逆 =====

    @Test
    public void trigger8_statusReversal_rejected() {
        // 1) 先合法插入一行 LOCKED
        jdbcTemplate.update(
                "INSERT INTO t_governance_lock(tenant_id, entity_type, entity_id, "
                        + "locked_by_user_id, lock_payload_hash, status) "
                        + "VALUES(?, 'FUND_LEDGER_PUBLISH', 1006, 7001, ?, 1)",
                TEST_TENANT_ID, HASH64);
        // 2) 推进至 COMMITTEE_SIGNED（合法）
        int promoted = jdbcTemplate.update(
                "UPDATE t_governance_lock SET status = 2, "
                        + "unlock_committee_user_id = 7002, "
                        + "unlock_committee_at = CURRENT_TIMESTAMP, "
                        + "unlock_committee_signature = 'sig-c' "
                        + "WHERE tenant_id = ? AND entity_id = 1006",
                TEST_TENANT_ID);
        assertEquals(1, promoted);

        // 3) 试图回退到 LOCKED → trigger 8 (e) 拦下
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "UPDATE t_governance_lock SET status = 1, "
                                + "unlock_committee_user_id = NULL, "
                                + "unlock_committee_at = NULL, "
                                + "unlock_committee_signature = NULL "
                                + "WHERE tenant_id = ? AND entity_id = 1006",
                        TEST_TENANT_ID));
        assertTrue(rootMessage(ex).contains("[trigger 8]"),
                "应抛 trigger 8，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("不可逆"),
                "错误信息应提到状态不可逆，实际：" + rootMessage(ex));
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
