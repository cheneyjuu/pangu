package com.pangu.bootstrap.voting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V3.0 trigger 12（CANCELLED ↔ cancel_* 一致性）+ chk_subject_status 反例覆盖。
 *
 * <p>规则要点（详见 V3.0__voting_lifecycle.sql）：
 * <ul>
 *   <li>(trigger 12-a) status=6(CANCELLED) 必须同时落 cancelled_at + cancelled_by_user_id + cancel_reason；</li>
 *   <li>(trigger 12-b) status≠6 不应携带任何 cancel_* 审计字段；</li>
 *   <li>(chk_subject_status) status 仅允许 1..6；</li>
 *   <li>正向：status=6 三件套齐全可写入。</li>
 * </ul>
 *
 * <p>所有反例期望抛 {@link DataAccessException} 且根因含 {@code [trigger 12]} / 约束名。
 */
@SpringBootTest
public class VotingLifecycleTriggerTest {

    /** 与其他集成用例隔离的独立 tenant_id。 */
    private static final long TEST_TENANT_ID = 99802L;
    private static final long PROPOSER_ID = 800101L;

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
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    // ===== trigger 12-a：CANCELLED 缺 cancel_* 字段 =====

    @Test
    public void trigger12_cancelledWithoutAuditFields_rejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_voting_subject(tenant_id, title, subject_type, status, "
                                + "vote_start_at, vote_end_at) "
                                + "VALUES(?, '已撤回但缺字段', 3, 6, "
                                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')",
                        TEST_TENANT_ID));
        assertTrue(rootMessage(ex).contains("[trigger 12]"),
                "应抛 trigger 12，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("必须同时落"),
                "信息应提及 CANCELLED 必须同时落 cancel_* 三件套：" + rootMessage(ex));
    }

    // ===== trigger 12-b：非 CANCELLED 携带 cancel_* 字段 =====

    @Test
    public void trigger12_nonCancelledCarryingAuditFields_rejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_voting_subject(tenant_id, title, subject_type, status, "
                                + "cancelled_at, cancelled_by_user_id, cancel_reason, "
                                + "vote_start_at, vote_end_at) "
                                + "VALUES(?, '草稿却带撤回字段', 3, 1, "
                                + "CURRENT_TIMESTAMP, ?, '不该有的原因', "
                                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')",
                        TEST_TENANT_ID, PROPOSER_ID));
        assertTrue(rootMessage(ex).contains("[trigger 12]"),
                "应抛 trigger 12，实际：" + rootMessage(ex));
        assertTrue(rootMessage(ex).contains("不应携带"),
                "信息应提及 非 CANCELLED 不应携带 cancel_* 字段：" + rootMessage(ex));
    }

    // ===== chk_subject_status：status 越界（7） =====

    @Test
    public void chkSubjectStatus_outOfRangeRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_voting_subject(tenant_id, title, subject_type, status, "
                                + "vote_start_at, vote_end_at) "
                                + "VALUES(?, '非法状态 7', 3, 7, "
                                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')",
                        TEST_TENANT_ID));
        String msg = rootMessage(ex).toLowerCase();
        assertTrue(msg.contains("chk_subject_status") || msg.contains("check"),
                "应被 chk_subject_status 拒绝：" + rootMessage(ex));
    }

    // ===== 正向：CANCELLED 三件套齐全可写入 =====

    @Test
    public void cancelledWithFullAuditFields_inserted() {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, status, "
                        + "cancelled_at, cancelled_by_user_id, cancel_reason, "
                        + "vote_start_at, vote_end_at) "
                        + "VALUES(?, '合法撤回议题', 3, 6, "
                        + "CURRENT_TIMESTAMP, ?, '街道办强撤：内容违规', "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day') "
                        + "RETURNING subject_id",
                Long.class, TEST_TENANT_ID, PROPOSER_ID);
        assertNotNull(id);
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_voting_subject WHERE subject_id = ?", Integer.class, id);
        assertEquals(6, status);
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
