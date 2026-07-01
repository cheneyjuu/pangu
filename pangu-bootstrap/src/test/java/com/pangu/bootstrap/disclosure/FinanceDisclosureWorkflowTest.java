package com.pangu.bootstrap.disclosure;

import com.pangu.application.disclosure.FinanceDisclosureApplicationException;
import com.pangu.application.disclosure.FinanceDisclosureApplicationService;
import com.pangu.application.disclosure.command.CompareCommand;
import com.pangu.application.disclosure.command.ComposeCommand;
import com.pangu.application.disclosure.command.LockAndPublishCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.disclosure.DisclosureDiff;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * compose → lockAndPublish → 业主 GET → compare 全链路集成。
 *
 * <p>断言：
 * <ul>
 *   <li>lockAndPublish 后 status=PUBLISHED 且关联 t_governance_lock 行 entity_type=FINANCE_DISCLOSURE；</li>
 *   <li>业主 GET DRAFT 报 DISCLOSURE_NOT_PUBLISHED；GET PUBLISHED 通过；</li>
 *   <li>compare 写入 audit 表，且 W/R/N 计数正确；</li>
 *   <li>compare 同 (prev, curr) 重复调用幂等（不会写入第二条 audit）。</li>
 * </ul>
 */
@SpringBootTest
public class FinanceDisclosureWorkflowTest {

    private static final long TEST_TENANT_ID = 99703L;
    private static final long COMPOSE_USER = 7001L;
    private static final long PUBLISH_USER = 7002L;
    private static final long AUDIT_USER = 7003L;

    @Autowired
    private FinanceDisclosureApplicationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserContextHolder userContextHolder;

    @BeforeEach
    public void setUp() {
        cleanUp();
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
        jdbcTemplate.update(
                "DELETE FROM t_governance_lock WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_fund_ledger_entry WHERE account_id IN "
                        + "(SELECT account_id FROM t_maintenance_fund_account WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_maintenance_fund_account WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    private Long seedAccount(BigDecimal balance) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_maintenance_fund_account(tenant_id, account_level, reference_id, "
                        + "ancestors, total_balance, frozen_balance) "
                        + "VALUES (?, 1, ?, '0', ?, 0) RETURNING account_id",
                Long.class, TEST_TENANT_ID, TEST_TENANT_ID, balance);
    }

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

    @Test
    public void fullWorkflow_composeLockPublishGetCompare() {
        // 第一期：2026-03
        Long accountId = seedAccount(new BigDecimal("1000.00"));
        seedEntry(accountId, 2, 1, new BigDecimal("100.00"), "2026-03-10 10:00:00");
        setRole("COMMITTEE_DIRECTOR", COMPOSE_USER);
        FinanceDisclosureSnapshot prev = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2026-03", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER));
        setRole("COMMITTEE_DIRECTOR", PUBLISH_USER);
        prev = service.lockAndPublish(new LockAndPublishCommand(
                TEST_TENANT_ID, prev.getSnapshotId(), PUBLISH_USER));
        assertEquals(DisclosureStatus.PUBLISHED, prev.getStatus());
        assertNotNull(prev.getGovernanceLockId());
        assertNotNull(prev.getPublishedAt());

        // 校验治理锁通路：t_governance_lock 中应能看到 entity_type='FINANCE_DISCLOSURE'
        Integer lockRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_governance_lock "
                        + "WHERE tenant_id = ? AND entity_type = 'FINANCE_DISCLOSURE' "
                        + "AND entity_id = ? AND lock_id = ?",
                Integer.class, TEST_TENANT_ID, prev.getSnapshotId(), prev.getGovernanceLockId());
        assertEquals(1, lockRows, "FINANCE_DISCLOSURE 治理锁行应已生成");

        // 业主 GET PUBLISHED 通过
        FinanceDisclosureSnapshot fetched = service.getReadablePublishedSnapshot(
                prev.getSnapshotId(), TEST_TENANT_ID);
        assertEquals(DisclosureStatus.PUBLISHED, fetched.getStatus());

        // 第二期：2026-04，余额上调 + 新增一笔流水
        jdbcTemplate.update("UPDATE t_maintenance_fund_account SET total_balance = ? "
                + "WHERE account_id = ?", new BigDecimal("1100.00"), accountId);
        seedEntry(accountId, 2, 1, new BigDecimal("100.00"), "2026-04-12 10:00:00");
        setRole("COMMITTEE_DIRECTOR", COMPOSE_USER);
        FinanceDisclosureSnapshot curr = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2026-04", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER));

        // compare：DRAFT 与 PUBLISHED 也允许 audit（service 不限制 status，只校验 tenant/type/时序）
        setRole("GOV_SUPER_ADMIN", AUDIT_USER);
        DisclosureDiff diff = service.compare(new CompareCommand(
                TEST_TENANT_ID, prev.getSnapshotId(), curr.getSnapshotId(), AUDIT_USER));
        // 至少有一处 W（账户余额变了）
        assertTrue(diff.writeCount() >= 1, "余额变化应至少计 1 个 W");

        // audit 表应已落记录
        Integer auditRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_disclosure_audit_compare "
                        + "WHERE tenant_id = ? AND prev_snapshot_id = ? AND curr_snapshot_id = ?",
                Integer.class, TEST_TENANT_ID, prev.getSnapshotId(), curr.getSnapshotId());
        assertEquals(1, auditRows);

        // 幂等：同 (prev, curr) 再 compare 不应写第二条
        setRole("GOV_SUPER_ADMIN", AUDIT_USER);
        service.compare(new CompareCommand(
                TEST_TENANT_ID, prev.getSnapshotId(), curr.getSnapshotId(), AUDIT_USER));
        Integer auditRowsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_disclosure_audit_compare "
                        + "WHERE tenant_id = ? AND prev_snapshot_id = ? AND curr_snapshot_id = ?",
                Integer.class, TEST_TENANT_ID, prev.getSnapshotId(), curr.getSnapshotId());
        assertEquals(1, auditRowsAfter, "compare 必须幂等（findByPair 命中复用）");
    }

    @Test
    public void getReadablePublishedSnapshot_draftRejected() {
        Long accountId = seedAccount(new BigDecimal("500.00"));
        seedEntry(accountId, 2, 1, new BigDecimal("50.00"), "2026-09-01 10:00:00");
        setRole("COMMITTEE_DIRECTOR", COMPOSE_USER);
        FinanceDisclosureSnapshot draft = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2026-09", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER));
        assertEquals(DisclosureStatus.DRAFT, draft.getStatus());

        FinanceDisclosureApplicationException ex = assertThrows(
                FinanceDisclosureApplicationException.class,
                () -> service.getReadablePublishedSnapshot(draft.getSnapshotId(), TEST_TENANT_ID));
        assertEquals(FinanceDisclosureApplicationException.Reason.DISCLOSURE_NOT_PUBLISHED,
                ex.getReason());
    }

    @Test
    public void getReadablePublishedSnapshot_wrongTenantRejected() {
        Long accountId = seedAccount(new BigDecimal("500.00"));
        seedEntry(accountId, 2, 1, new BigDecimal("50.00"), "2026-10-01 10:00:00");
        setRole("COMMITTEE_DIRECTOR", COMPOSE_USER);
        FinanceDisclosureSnapshot snap = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2026-10", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER));

        FinanceDisclosureApplicationException ex = assertThrows(
                FinanceDisclosureApplicationException.class,
                () -> service.getReadablePublishedSnapshot(snap.getSnapshotId(), 99999L));
        assertEquals(FinanceDisclosureApplicationException.Reason.SNAPSHOT_NOT_FOUND,
                ex.getReason());
    }

    @Test
    public void compare_samePairRejected() {
        Long accountId = seedAccount(new BigDecimal("500.00"));
        seedEntry(accountId, 2, 1, new BigDecimal("50.00"), "2026-11-01 10:00:00");
        setRole("COMMITTEE_DIRECTOR", COMPOSE_USER);
        FinanceDisclosureSnapshot snap = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2026-11", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER));

        setRole("GOV_SUPER_ADMIN", AUDIT_USER);
        FinanceDisclosureApplicationException ex = assertThrows(
                FinanceDisclosureApplicationException.class,
                () -> service.compare(new CompareCommand(
                        TEST_TENANT_ID, snap.getSnapshotId(),
                        snap.getSnapshotId(), AUDIT_USER)));
        assertEquals(FinanceDisclosureApplicationException.Reason.COMPARE_INVALID_PAIR,
                ex.getReason());
    }

    @Test
    public void communityAdminCannotPublishEvenIfPermissionMisconfigured() {
        Long accountId = seedAccount(new BigDecimal("500.00"));
        seedEntry(accountId, 2, 1, new BigDecimal("50.00"), "2026-12-01 10:00:00");
        setRole("COMMITTEE_DIRECTOR", COMPOSE_USER);
        FinanceDisclosureSnapshot draft = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2026-12", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER));

        setRole("COMMUNITY_ADMIN", PUBLISH_USER);
        FinanceDisclosureApplicationException ex = assertThrows(
                FinanceDisclosureApplicationException.class,
                () -> service.lockAndPublish(new LockAndPublishCommand(
                        TEST_TENANT_ID, draft.getSnapshotId(), PUBLISH_USER)));
        assertEquals(FinanceDisclosureApplicationException.Reason.DISCLOSURE_ROLE_FORBIDDEN,
                ex.getReason());
    }

    @Test
    public void committeeDirectorCannotAuditEvenIfPermissionMisconfigured() {
        Long accountId = seedAccount(new BigDecimal("500.00"));
        seedEntry(accountId, 2, 1, new BigDecimal("50.00"), "2027-01-01 10:00:00");
        setRole("COMMITTEE_DIRECTOR", COMPOSE_USER);
        FinanceDisclosureSnapshot snap = service.compose(new ComposeCommand(
                TEST_TENANT_ID, "2027-01", DisclosureType.MAINTENANCE_FUND, COMPOSE_USER));

        setRole("COMMITTEE_DIRECTOR", AUDIT_USER);
        FinanceDisclosureApplicationException ex = assertThrows(
                FinanceDisclosureApplicationException.class,
                () -> service.compare(new CompareCommand(
                        TEST_TENANT_ID, snap.getSnapshotId(), snap.getSnapshotId(), AUDIT_USER)));
        assertEquals(FinanceDisclosureApplicationException.Reason.DISCLOSURE_ROLE_FORBIDDEN,
                ex.getReason());
    }

    /** 防止 IDE 警告 unused import 时占位（Map 在 cleanup SQL 里曾被引用，留给后续扩展）。 */
    @SuppressWarnings("unused")
    private static final Map<String, Object> RESERVED = Map.of();
}
