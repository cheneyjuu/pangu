package com.pangu.bootstrap.disclosure;

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
 * V2.7 trigger 9（{@code fn_disclosure_atomicity}）反例覆盖。
 *
 * <p>规则要点（详见 V2.7 SQL）：
 * <ul>
 *   <li>(a) status=2(LOCKED) 要求 {@code governance_lock_id} NOT NULL；</li>
 *   <li>(b) status=3(PUBLISHED) 要求 {@code published_at} NOT NULL；</li>
 *   <li>(d) UPDATE 路径 status 仅允许 1→1/1→2 / 2→2/2→3 / 3→3/3→4 / 4→4/4→1，
 *       任何跨级 / 逆向均拒；</li>
 *   <li>(e) BEFORE INSERT 仅校验 (a)(b)，DRAFT(1) 起步天然合法。</li>
 * </ul>
 *
 * <p>本测试通过 {@link JdbcTemplate} 直接绕过聚合根触达 DB，验证 trigger 兜底；
 * 全部反例期望抛 {@link DataAccessException} 且根因含 {@code [trigger 9]}。
 */
@SpringBootTest
public class DisclosureTriggerTest {

    /** 与其他集成用例隔离的独立 tenant_id。 */
    private static final long TEST_TENANT_ID = 99701L;
    private static final String HASH64 = "e".repeat(64);
    private static final String PAYLOAD_JSON = "{\"k\":\"v\"}";

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
                "DELETE FROM t_disclosure_audit_compare WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_finance_disclosure_snapshot WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    /** 插入一条合法 DRAFT，返回 snapshotId。 */
    private long insertDraft(String period) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO t_finance_disclosure_snapshot(tenant_id, period, disclosure_type, "
                        + "status, data_payload, statistics_version, payload_hash, "
                        + "composed_by_user_id) "
                        + "VALUES(?, ?, 'MAINTENANCE_FUND', 1, CAST(? AS JSONB), 1, ?, 7001) "
                        + "RETURNING snapshot_id",
                Long.class, TEST_TENANT_ID, period, PAYLOAD_JSON, HASH64);
        assertEquals(false, id == null);
        return id;
    }

    // ===== (a) LOCKED 必挂 governance_lock_id =====

    @Test
    public void trigger9_lockedWithoutGovernanceLockId_rejected() {
        long id = insertDraft("2026-01");
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "UPDATE t_finance_disclosure_snapshot SET status = 2 "
                                + "WHERE snapshot_id = ?", id));
        assertTrue(rootMessage(ex).contains("[trigger 9]"),
                "应抛 trigger 9，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("LOCKED"),
                "信息应提及 LOCKED 必挂 governance_lock_id，实际：" + rootMessage(ex));
    }

    // ===== (b) PUBLISHED 必有 published_at =====

    @Test
    public void trigger9_publishedWithoutPublishedAt_rejected() {
        // 先合法地推进到 LOCKED（governance_lock_id 用任意非空数字，保证 (a) 通过；
        // 真实生产由 GovernanceLockApplicationService 写入，这里仅为命中 trigger 9 (b)）
        long id = insertDraft("2026-02");
        // 临时关闭 FK 校验：governance_lock_id 引用 t_governance_lock；
        // 这里只为构造 status=3 + published_at IS NULL 的反例，所以走 DEFERRABLE
        // 不可行的话退化为先建一条 lock 行：实际项目里 t_governance_lock 已是真实表
        Long lockId = jdbcTemplate.queryForObject(
                "INSERT INTO t_governance_lock(tenant_id, entity_type, entity_id, "
                        + "locked_by_user_id, lock_payload_hash, status) "
                        + "VALUES(?, 'FINANCE_DISCLOSURE', ?, 7001, ?, 1) RETURNING lock_id",
                Long.class, TEST_TENANT_ID, id, HASH64);
        // 推进到 LOCKED
        jdbcTemplate.update(
                "UPDATE t_finance_disclosure_snapshot SET status = 2, "
                        + "governance_lock_id = ?, locked_at = CURRENT_TIMESTAMP "
                        + "WHERE snapshot_id = ?", lockId, id);

        // 试图直接 LOCKED → PUBLISHED 但不填 published_at
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "UPDATE t_finance_disclosure_snapshot SET status = 3 "
                                + "WHERE snapshot_id = ?", id));
        assertTrue(rootMessage(ex).contains("[trigger 9]"),
                "应抛 trigger 9，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("PUBLISHED"),
                "信息应提及 PUBLISHED 必有 published_at，实际：" + rootMessage(ex));
    }

    // ===== (d) DRAFT → PUBLISHED 跨级禁止 =====

    @Test
    public void trigger9_draftToPublishedSkip_rejected() {
        long id = insertDraft("2026-03");
        // 即便给 published_at 也不能跨级
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "UPDATE t_finance_disclosure_snapshot SET status = 3, "
                                + "published_at = CURRENT_TIMESTAMP "
                                + "WHERE snapshot_id = ?", id));
        assertTrue(rootMessage(ex).contains("[trigger 9]"),
                "应抛 trigger 9，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("非法流转"),
                "信息应提及状态非法流转，实际：" + rootMessage(ex));
    }

    // ===== (d) PUBLISHED → DRAFT 逆向禁止 =====

    @Test
    public void trigger9_publishedToDraftReverse_rejected() {
        long id = insertDraft("2026-04");
        // 走完整链路至 PUBLISHED
        Long lockId = jdbcTemplate.queryForObject(
                "INSERT INTO t_governance_lock(tenant_id, entity_type, entity_id, "
                        + "locked_by_user_id, lock_payload_hash, status) "
                        + "VALUES(?, 'FINANCE_DISCLOSURE', ?, 7001, ?, 1) RETURNING lock_id",
                Long.class, TEST_TENANT_ID, id, HASH64);
        jdbcTemplate.update(
                "UPDATE t_finance_disclosure_snapshot SET status = 2, "
                        + "governance_lock_id = ?, locked_at = CURRENT_TIMESTAMP "
                        + "WHERE snapshot_id = ?", lockId, id);
        jdbcTemplate.update(
                "UPDATE t_finance_disclosure_snapshot SET status = 3, "
                        + "published_at = CURRENT_TIMESTAMP "
                        + "WHERE snapshot_id = ?", id);

        // 试图回退到 DRAFT
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "UPDATE t_finance_disclosure_snapshot SET status = 1 "
                                + "WHERE snapshot_id = ?", id));
        assertTrue(rootMessage(ex).contains("[trigger 9]"),
                "应抛 trigger 9，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("非法流转"),
                "信息应提及状态非法流转，实际：" + rootMessage(ex));
    }

    // ===== chk_disc_period_format =====

    @Test
    public void periodFormat_rejectInvalidLiteral() {
        // 'YYYY' 缺月份段、'YYYY-13' 月份越界、'YYYYQ5' 季度越界
        for (String bad : new String[]{"2026", "2026-13", "2026Q5", "abcd"}) {
            DataAccessException ex = assertThrows(DataAccessException.class, () ->
                    jdbcTemplate.update(
                            "INSERT INTO t_finance_disclosure_snapshot(tenant_id, period, "
                                    + "disclosure_type, status, data_payload, statistics_version, "
                                    + "payload_hash, composed_by_user_id) "
                                    + "VALUES(?, ?, 'MAINTENANCE_FUND', 1, CAST(? AS JSONB), 1, ?, 7001)",
                            TEST_TENANT_ID, bad, PAYLOAD_JSON, HASH64));
            assertTrue(rootMessage(ex).toLowerCase().contains("chk_disc_period_format")
                            || rootMessage(ex).contains("period"),
                    "应被 chk_disc_period_format 拒绝（period=" + bad + "），实际：" + rootMessage(ex));
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
