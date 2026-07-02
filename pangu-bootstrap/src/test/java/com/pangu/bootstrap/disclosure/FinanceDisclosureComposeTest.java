package com.pangu.bootstrap.disclosure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.disclosure.FinanceDisclosureApplicationException;
import com.pangu.application.disclosure.FinanceDisclosureApplicationService;
import com.pangu.application.disclosure.command.ComposeCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.disclosure.DisclosureStatus;
import com.pangu.domain.model.disclosure.DisclosureType;
import com.pangu.domain.model.disclosure.FinanceDisclosureSnapshot;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FinanceDisclosureApplicationService#compose(ComposeCommand)} 集成测试。
 *
 * <p>通过 {@link JdbcTemplate} 写入 V2.2 假数据（账户树 + 流水），驱动 service compose，
 * 断言：
 * <ol>
 *   <li>payload JSON 含 accounts / entrySummaries 两段；</li>
 *   <li>statisticsVersion 在重复 compose 时自增；</li>
 *   <li>COMMON_FUND 抛 DISCLOSURE_TYPE_NOT_SUPPORTED；</li>
 *   <li>无任何账户与流水时抛 LEDGER_QUERY_EMPTY。</li>
 * </ol>
 */
@SpringBootTest
public class FinanceDisclosureComposeTest {

    private static final long TEST_TENANT_ID = 99702L;
    private static final long COMPOSE_USER_ID = 7001L;

    @Autowired
    private FinanceDisclosureApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserContextHolder userContextHolder;

    @BeforeEach
    public void setUp() {
        cleanUp();
        setRole("COMMITTEE_DIRECTOR", COMPOSE_USER_ID);
    }

    @AfterEach
    public void tearDown() {
        userContextHolder.clear();
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM t_disclosure_audit_compare WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_finance_disclosure_snapshot WHERE tenant_id = ?", TEST_TENANT_ID);
        // 清账户与流水（注意 FK 顺序：先清流水再清账户）
        jdbcTemplate.update(
                "DELETE FROM t_fund_ledger_entry WHERE account_id IN "
                        + "(SELECT account_id FROM t_maintenance_fund_account WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_maintenance_fund_account WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    /** 写入一个 COMMUNITY 账户，返回 account_id。 */
    private Long seedAccount(BigDecimal balance) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_maintenance_fund_account(tenant_id, account_level, reference_id, "
                        + "ancestors, total_balance, frozen_balance) "
                        + "VALUES (?, 1, ?, '0', ?, 0) RETURNING account_id",
                Long.class, TEST_TENANT_ID, TEST_TENANT_ID, balance);
    }

    /** 写入一笔本期内的流水（occurred_at 落在 period 月内）。 */
    private void seedEntry(Long accountId, int businessType, int direction,
                            BigDecimal amount, String occurredAt) {
        jdbcTemplate.update(
                "INSERT INTO t_fund_ledger_entry(account_id, business_type, direction, amount, "
                        + "balance_after, occurred_at, audit_hash) "
                        + "VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMP), ?)",
                accountId, businessType, direction, amount, amount, occurredAt,
                "h".repeat(64));
    }

    private void setRole(String roleKey, long userId) {
        userContextHolder.set(new UserContext(
                userId + 100000L,
                UserContext.IdentityType.SYS_USER,
                userId,
                TEST_TENANT_ID,
                9001L,
                "GOV_SUPER_ADMIN".equals(roleKey) || "COMMUNITY_ADMIN".equals(roleKey)
                        ? UserContext.DeptCategory.G : UserContext.DeptCategory.B,
                "GOV_SUPER_ADMIN".equals(roleKey) || "COMMUNITY_ADMIN".equals(roleKey) ? 2 : 10,
                DataScopeType.ALL_COMMUNITY,
                AuthenticationLevel.L1,
                roleKey,
                Set.of(),
                Set.of()));
    }

    // ===== compose 正向 =====

    @Test
    public void compose_persistsSnapshotWithAccountsAndEntries() throws Exception {
        Long accountId = seedAccount(new BigDecimal("12345.67"));
        seedEntry(accountId, 2, 1, new BigDecimal("100.00"), "2026-05-10 10:00:00");
        seedEntry(accountId, 2, 1, new BigDecimal("200.00"), "2026-05-15 11:00:00");
        seedEntry(accountId, 4, 2, new BigDecimal("50.00"),  "2026-05-20 12:00:00");

        FinanceDisclosureSnapshot snapshot = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2026-05", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER_ID));

        assertNotNull(snapshot.getSnapshotId());
        assertEquals(DisclosureStatus.DRAFT, snapshot.getStatus());
        assertEquals(1, snapshot.getStatisticsVersion());
        assertEquals(64, snapshot.getPayloadHash().length());

        JsonNode payload = objectMapper.readTree(snapshot.getDataPayload());
        assertTrue(payload.has("accounts"), "payload 必须含 accounts 段");
        assertTrue(payload.has("entrySummaries"), "payload 必须含 entrySummaries 段");
        assertEquals(1, payload.get("accounts").size());
        assertTrue(payload.get("entrySummaries").size() >= 1, "至少应汇总到 1 条记录");
    }

    @Test
    public void compose_secondTimeIncrementsStatisticsVersion() {
        Long accountId = seedAccount(new BigDecimal("100.00"));
        seedEntry(accountId, 2, 1, new BigDecimal("10.00"), "2026-06-05 10:00:00");

        FinanceDisclosureSnapshot v1 = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2026-06", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER_ID));
        assertEquals(1, v1.getStatisticsVersion());

        FinanceDisclosureSnapshot v2 = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2026-06", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER_ID));
        assertEquals(2, v2.getStatisticsVersion(),
                "同 (tenant, type, period) 第二次 compose 应升到 v2");
    }

    // ===== compose 反向 =====

    @Test
    public void compose_commonFund_rejected() {
        Long accountId = seedAccount(new BigDecimal("100.00"));
        seedEntry(accountId, 2, 1, new BigDecimal("10.00"), "2026-07-05 10:00:00");

        FinanceDisclosureApplicationException ex = assertThrows(
                FinanceDisclosureApplicationException.class,
                () -> service.compose(new ComposeCommand(
                        TEST_TENANT_ID, "2026-07",
                        DisclosureType.COMMON_FUND, COMPOSE_USER_ID)));
        assertEquals(FinanceDisclosureApplicationException.Reason.DISCLOSURE_TYPE_NOT_SUPPORTED,
                ex.getReason());
    }

    @Test
    public void compose_emptyLedger_rejected() {
        // 不种任何 account/entry → gateway 返回空 → 抛 LEDGER_QUERY_EMPTY
        FinanceDisclosureApplicationException ex = assertThrows(
                FinanceDisclosureApplicationException.class,
                () -> service.compose(new ComposeCommand(
                        TEST_TENANT_ID, "2026-08",
                        DisclosureType.MAINTENANCE_FUND, COMPOSE_USER_ID)));
        assertEquals(FinanceDisclosureApplicationException.Reason.LEDGER_QUERY_EMPTY,
                ex.getReason());
    }

    @Test
    public void gridOperatorCannotComposeEvenIfPermissionMisconfigured() {
        setRole("GRID_MEMBER", COMPOSE_USER_ID);
        Long accountId = seedAccount(new BigDecimal("100.00"));
        seedEntry(accountId, 2, 1, new BigDecimal("10.00"), "2026-12-05 10:00:00");

        FinanceDisclosureApplicationException ex = assertThrows(
                FinanceDisclosureApplicationException.class,
                () -> service.compose(new ComposeCommand(
                        TEST_TENANT_ID, "2026-12",
                        DisclosureType.MAINTENANCE_FUND, COMPOSE_USER_ID)));
        assertEquals(FinanceDisclosureApplicationException.Reason.DISCLOSURE_ROLE_FORBIDDEN,
                ex.getReason());
    }
}
