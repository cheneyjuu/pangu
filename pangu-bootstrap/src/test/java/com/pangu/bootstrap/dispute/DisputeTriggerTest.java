package com.pangu.bootstrap.dispute;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2.8 trigger 10 / 11 + chk_dispute_kind_level1 + uk_decision_dispute_level 反例覆盖。
 *
 * <p>规则要点（详见 V2.8__owner_dispute_pipeline.sql）：
 * <ul>
 *   <li>(trigger 10-a) status 含 LEVEL_N 时 N 必须 = current_review_level；</li>
 *   <li>(trigger 10-b) UPDATE 路径 current_review_level 单调递增、不可跳级、不可逆；</li>
 *   <li>(trigger 10-c) closed_at 当且仅当 status ∈ {CLOSED_FINAL, WITHDRAWN}；</li>
 *   <li>(trigger 11-a) decision.review_level 必须 ≤ 主表 current_review_level；</li>
 *   <li>(trigger 11-b) 主表 status 必须为 DECIDED_LEVEL_N_<KIND> 时方可插入 LEVEL_N decision；</li>
 *   <li>(trigger 11-c) decision 引用的 dispute_id 必须存在；</li>
 *   <li>(chk_dispute_kind_level1) 仅 EXPENSE_VOUCHER_DISPUTE 适用 Level 1；</li>
 *   <li>(uk_decision_dispute_level) (dispute_id, review_level) 唯一。</li>
 * </ul>
 *
 * <p>所有反例期望抛 {@link DataAccessException}/{@link DuplicateKeyException} 且根因含
 * {@code [trigger 10]} / {@code [trigger 11]} / 约束名。
 */
@SpringBootTest
public class DisputeTriggerTest {

    /** 与其他集成用例隔离的独立 tenant_id。 */
    private static final long TEST_TENANT_ID = 99801L;
    private static final long OWNER_ID = 70002L;
    private static final long DECIDER_ID = 800003L;

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
                "DELETE FROM t_dispute_review_decision WHERE dispute_id IN "
                        + "(SELECT dispute_id FROM t_owner_dispute WHERE tenant_id = ?)", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_dispute_evidence WHERE dispute_id IN "
                        + "(SELECT dispute_id FROM t_owner_dispute WHERE tenant_id = ?)", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_owner_dispute WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    /** 插入合法 RAISED 行（EXPENSE_VOUCHER_DISPUTE，level=1），返回 disputeId。 */
    private long insertRaisedExpenseVoucher() {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO t_owner_dispute(tenant_id, raised_by_owner_id, dispute_kind, "
                        + "current_review_level, status, business_payload) "
                        + "VALUES(?, ?, 'EXPENSE_VOUCHER_DISPUTE', 1, 'RAISED', "
                        + "CAST('{}' AS JSONB)) RETURNING dispute_id",
                Long.class, TEST_TENANT_ID, OWNER_ID);
        assertNotNull(id);
        return id;
    }

    // ===== trigger 10-a：status LEVEL 与 current_review_level 不一致 =====

    @Test
    public void trigger10_statusLevelMismatchOnInsert_rejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_owner_dispute(tenant_id, raised_by_owner_id, dispute_kind, "
                                + "current_review_level, status, business_payload) "
                                + "VALUES(?, ?, 'PROPOSAL_QUALITY_DISPUTE', 2, "
                                + "'UNDER_REVIEW_LEVEL_3', CAST('{}' AS JSONB))",
                        TEST_TENANT_ID, OWNER_ID));
        assertTrue(rootMessage(ex).contains("[trigger 10]"),
                "应抛 trigger 10，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("不一致"),
                "信息应提及 status × current_review_level 不一致：" + rootMessage(ex));
    }

    // ===== trigger 10-b：current_review_level 跳级 =====

    @Test
    public void trigger10_currentReviewLevelSkipped_rejected() {
        long id = insertRaisedExpenseVoucher();
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "UPDATE t_owner_dispute SET current_review_level = 3, "
                                + "status = 'UNDER_REVIEW_LEVEL_3' WHERE dispute_id = ?", id));
        assertTrue(rootMessage(ex).contains("[trigger 10]"),
                "应抛 trigger 10，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("不可跳级"),
                "信息应提及 不可跳级：" + rootMessage(ex));
    }

    // ===== trigger 10-b：current_review_level 逆向 =====

    @Test
    public void trigger10_currentReviewLevelReversed_rejected() {
        // 插入 level=2 的行（PROPOSAL_QUALITY_DISPUTE 起步 Level 2）
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO t_owner_dispute(tenant_id, raised_by_owner_id, dispute_kind, "
                        + "current_review_level, status, business_payload) "
                        + "VALUES(?, ?, 'PROPOSAL_QUALITY_DISPUTE', 2, 'RAISED', "
                        + "CAST('{}' AS JSONB)) RETURNING dispute_id",
                Long.class, TEST_TENANT_ID, OWNER_ID);
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "UPDATE t_owner_dispute SET current_review_level = 1, "
                                + "status = 'UNDER_REVIEW_LEVEL_1' WHERE dispute_id = ?", id));
        assertTrue(rootMessage(ex).contains("[trigger 10]"),
                "应抛 trigger 10，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("不可逆"),
                "信息应提及 不可逆：" + rootMessage(ex));
    }

    // ===== trigger 10-c：终态必有 closed_at =====

    @Test
    public void trigger10_closedFinalWithoutClosedAt_rejected() {
        long id = insertRaisedExpenseVoucher();
        // RAISED → UNDER_REVIEW_LEVEL_1（合法过渡）
        jdbcTemplate.update(
                "UPDATE t_owner_dispute SET status = 'UNDER_REVIEW_LEVEL_1' "
                        + "WHERE dispute_id = ?", id);
        // 直接强迁 CLOSED_FINAL 但不填 closed_at
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "UPDATE t_owner_dispute SET status = 'CLOSED_FINAL' "
                                + "WHERE dispute_id = ?", id));
        assertTrue(rootMessage(ex).contains("[trigger 10]"),
                "应抛 trigger 10，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("closed_at"),
                "信息应提及 closed_at 必填：" + rootMessage(ex));
    }

    // ===== trigger 10-c：非终态不应有 closed_at =====

    @Test
    public void trigger10_nonTerminalWithClosedAt_rejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_owner_dispute(tenant_id, raised_by_owner_id, dispute_kind, "
                                + "current_review_level, status, closed_at, business_payload) "
                                + "VALUES(?, ?, 'EXPENSE_VOUCHER_DISPUTE', 1, 'RAISED', "
                                + "CURRENT_TIMESTAMP, CAST('{}' AS JSONB))",
                        TEST_TENANT_ID, OWNER_ID));
        assertTrue(rootMessage(ex).contains("[trigger 10]"),
                "应抛 trigger 10，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("非终态"),
                "信息应提及 非终态不应有 closed_at：" + rootMessage(ex));
    }

    // ===== trigger 11-a：decision.review_level > 主表 level =====

    @Test
    public void trigger11_decisionLevelExceedsMain_rejected() {
        long id = insertRaisedExpenseVoucher();
        // 推进到 DECIDED_LEVEL_1_UPHELD
        jdbcTemplate.update(
                "UPDATE t_owner_dispute SET status = 'UNDER_REVIEW_LEVEL_1' "
                        + "WHERE dispute_id = ?", id);
        jdbcTemplate.update(
                "UPDATE t_owner_dispute SET status = 'DECIDED_LEVEL_1_UPHELD' "
                        + "WHERE dispute_id = ?", id);

        // 试图插入 review_level=2 的 decision（主表仅到 level=1）
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_dispute_review_decision(dispute_id, review_level, "
                                + "decided_by_user_id, decision_kind, decision_content) "
                                + "VALUES(?, 2, ?, 'UPHELD', '违法越级')", id, DECIDER_ID));
        assertTrue(rootMessage(ex).contains("[trigger 11]"),
                "应抛 trigger 11，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("超过主表"),
                "信息应提及 review_level 超过主表 current_review_level：" + rootMessage(ex));
    }

    // ===== trigger 11-b：主表 status 未到 DECIDED_LEVEL_N_<KIND> =====

    @Test
    public void trigger11_mainStatusNotDecided_rejected() {
        long id = insertRaisedExpenseVoucher();
        // 仅推进到 UNDER_REVIEW_LEVEL_1（未 DECIDED）
        jdbcTemplate.update(
                "UPDATE t_owner_dispute SET status = 'UNDER_REVIEW_LEVEL_1' "
                        + "WHERE dispute_id = ?", id);

        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_dispute_review_decision(dispute_id, review_level, "
                                + "decided_by_user_id, decision_kind, decision_content) "
                                + "VALUES(?, 1, ?, 'UPHELD', '主表未 DECIDED')", id, DECIDER_ID));
        assertTrue(rootMessage(ex).contains("[trigger 11]"),
                "应抛 trigger 11，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("不允许插入"),
                "信息应提及 主表 status 不允许插入 decision：" + rootMessage(ex));
    }

    // ===== trigger 11-c：dispute_id 不存在 =====

    @Test
    public void trigger11_disputeIdNotFound_rejected() {
        // FK 在 t_dispute_review_decision.dispute_id 上已存在 REFERENCES t_owner_dispute
        // 故触发 FK 违例（PG 在 trigger 函数读 dispute 之前已被 FK 拒绝）—— 异常根因含 FK 约束。
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_dispute_review_decision(dispute_id, review_level, "
                                + "decided_by_user_id, decision_kind, decision_content) "
                                + "VALUES(99999999, 1, ?, 'UPHELD', '不存在的 dispute_id')",
                        DECIDER_ID));
        String msg = rootMessage(ex).toLowerCase();
        assertTrue(msg.contains("foreign key") || msg.contains("dispute_id")
                        || msg.contains("[trigger 11]"),
                "应被 FK 或 trigger 11 拒绝：" + rootMessage(ex));
    }

    // ===== chk_dispute_kind_level1：非 EXPENSE_VOUCHER_DISPUTE 不允许 Level 1 =====

    @Test
    public void chkDisputeKindLevel1_nonExpenseVoucherCannotStartAtLevel1() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_owner_dispute(tenant_id, raised_by_owner_id, dispute_kind, "
                                + "current_review_level, status, business_payload) "
                                + "VALUES(?, ?, 'PROPOSAL_QUALITY_DISPUTE', 1, 'RAISED', "
                                + "CAST('{}' AS JSONB))",
                        TEST_TENANT_ID, OWNER_ID));
        String msg = rootMessage(ex).toLowerCase();
        assertTrue(msg.contains("chk_dispute_kind_level1") || msg.contains("check"),
                "应被 chk_dispute_kind_level1 拒绝：" + rootMessage(ex));
    }

    // ===== uk_decision_dispute_level：(dispute_id, level) 唯一 =====

    @Test
    public void ukDecisionDisputeLevel_duplicateRejected() {
        long id = insertRaisedExpenseVoucher();
        // 推到 DECIDED_LEVEL_1_UPHELD
        jdbcTemplate.update(
                "UPDATE t_owner_dispute SET status = 'UNDER_REVIEW_LEVEL_1' "
                        + "WHERE dispute_id = ?", id);
        jdbcTemplate.update(
                "UPDATE t_owner_dispute SET status = 'DECIDED_LEVEL_1_UPHELD' "
                        + "WHERE dispute_id = ?", id);

        // 第一条合法插入
        jdbcTemplate.update(
                "INSERT INTO t_dispute_review_decision(dispute_id, review_level, "
                        + "decided_by_user_id, decision_kind, decision_content) "
                        + "VALUES(?, 1, ?, 'UPHELD', '首次决议')", id, DECIDER_ID);

        // 同 (dispute_id, level=1) 重复插入应被 UK 拒
        DuplicateKeyException ex = assertThrows(DuplicateKeyException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_dispute_review_decision(dispute_id, review_level, "
                                + "decided_by_user_id, decision_kind, decision_content) "
                                + "VALUES(?, 1, ?, 'REJECTED', '重复决议')", id, DECIDER_ID));
        assertTrue(rootMessage(ex).toLowerCase().contains("uk_decision_dispute_level")
                        || rootMessage(ex).contains("dispute_id"),
                "应被 uk_decision_dispute_level 拒绝：" + rootMessage(ex));

        // 验证只有 1 条 decision
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_dispute_review_decision WHERE dispute_id = ?",
                Integer.class, id);
        assertEquals(1, count);
    }

    // ===== chk_dispute_litigation_outcome：异常 outcome 字面量 =====

    @Test
    public void chkLitigationOutcome_invalidLiteralRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_owner_dispute(tenant_id, raised_by_owner_id, dispute_kind, "
                                + "current_review_level, status, business_payload, "
                                + "litigation_outcome) "
                                + "VALUES(?, ?, 'EXPENSE_VOUCHER_DISPUTE', 1, 'RAISED', "
                                + "CAST('{}' AS JSONB), 'BOGUS')",
                        TEST_TENANT_ID, OWNER_ID));
        String msg = rootMessage(ex).toLowerCase();
        assertTrue(msg.contains("chk_dispute_litigation_outcome") || msg.contains("check"),
                "应被 chk_dispute_litigation_outcome 拒绝：" + rootMessage(ex));
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
