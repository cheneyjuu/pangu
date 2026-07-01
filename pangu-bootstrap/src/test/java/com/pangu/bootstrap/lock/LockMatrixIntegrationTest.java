package com.pangu.bootstrap.lock;

import com.pangu.application.lock.GovernanceLockApplicationException;
import com.pangu.application.lock.GovernanceLockApplicationService;
import com.pangu.application.lock.command.CommitteeUnlockCommand;
import com.pangu.application.lock.command.LockCommand;
import com.pangu.application.lock.command.StreetUnlockCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.model.lock.GovernanceLockStatus;
import com.pangu.domain.model.lock.LockEntityType;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全 entityType 治理锁端到端整合：lock → committeeSign → streetSign → verifyLocked。
 *
 * <p>覆盖四种 {@link LockEntityType}（FINANCE_DISCLOSURE / ELECTION_DISCLOSURE /
 * FUND_LEDGER_PUBLISH / TRUST_FUND_PAYMENT）；每种走完整链路，并断言：
 * <ul>
 *   <li>初签后 {@link GovernanceLockApplicationService#verifyLocked} 仍视为持锁；</li>
 *   <li>终签 FULLY_UNLOCKED 后 {@code verifyLocked} 抛 LOCK_NOT_HELD；</li>
 *   <li>不存在的 entity 调用 {@code verifyLocked} 同样抛 LOCK_NOT_HELD；</li>
 *   <li>不存在 lockId 的 committeeSign / streetSign 抛 LOCK_NOT_FOUND；</li>
 *   <li>双签同人在 application 层翻译为 LOCK_SIGNER_CONFLICT。</li>
 * </ul>
 */
@SpringBootTest
public class LockMatrixIntegrationTest {

    private static final long TEST_TENANT_ID = 99401L;
    private static final long INITIATOR = 7001L;
    private static final long COMMITTEE_USER = 7101L;
    private static final long STREET_USER = 8101L;
    private static final String HASH64 = "d".repeat(64);

    @Autowired
    private GovernanceLockApplicationService lockApplicationService;

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
        jdbcTemplate.update("DELETE FROM t_governance_lock WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    private void setRole(String roleKey, long userId) {
        userContextHolder.set(new UserContext(
                userId + 100000L,
                UserContext.IdentityType.SYS_USER,
                userId,
                TEST_TENANT_ID,
                9001L,
                "GOV_SUPER_ADMIN".equals(roleKey) ? UserContext.DeptCategory.G : UserContext.DeptCategory.B,
                "GOV_SUPER_ADMIN".equals(roleKey) ? 2 : 10,
                DataScopeType.ALL_COMMUNITY,
                AuthenticationLevel.L1,
                roleKey,
                Set.of(),
                Set.of()));
    }

    @Test
    public void financeDisclosure_fullChain_thenVerifyLocked() {
        runFullChain(LockEntityType.FINANCE_DISCLOSURE, 5001L);
    }

    @Test
    public void electionDisclosure_fullChain_thenVerifyLocked() {
        runFullChain(LockEntityType.ELECTION_DISCLOSURE, 5002L);
    }

    @Test
    public void fundLedgerPublish_fullChain_thenVerifyLocked() {
        runFullChain(LockEntityType.FUND_LEDGER_PUBLISH, 5003L);
    }

    @Test
    public void trustFundPayment_fullChain_thenVerifyLocked() {
        runFullChain(LockEntityType.TRUST_FUND_PAYMENT, 5005L);
    }

    /** 公共全链路：lock → verifyLocked OK → committeeSign → verifyLocked still OK → streetSign → verifyLocked NOT_HELD。 */
    private void runFullChain(LockEntityType type, long entityId) {
        // 1) lock
        GovernanceLock locked = lockApplicationService.lock(
                new LockCommand(TEST_TENANT_ID, type, entityId, INITIATOR, HASH64));
        assertNotNull(locked.getLockId());
        assertEquals(GovernanceLockStatus.LOCKED, locked.getStatus());

        // 2) verifyLocked: LOCKED 状态视为持锁
        assertDoesNotThrow(() -> lockApplicationService.verifyLocked(TEST_TENANT_ID, type, entityId));

        // 3) committeeSign
        setRole("COMMITTEE_DIRECTOR", COMMITTEE_USER);
        GovernanceLock afterCommittee = lockApplicationService.committeeSign(
                new CommitteeUnlockCommand(locked.getLockId(), COMMITTEE_USER, "sig-c"));
        assertEquals(GovernanceLockStatus.COMMITTEE_SIGNED, afterCommittee.getStatus());
        assertEquals(COMMITTEE_USER, afterCommittee.getUnlockCommitteeUserId());
        assertNull(afterCommittee.getUnlockAt(), "初签后 unlock_at 仍应为 null");

        // 4) verifyLocked: 单签仍视为持锁
        assertDoesNotThrow(() -> lockApplicationService.verifyLocked(TEST_TENANT_ID, type, entityId));

        // 5) streetSign
        setRole("GOV_SUPER_ADMIN", STREET_USER);
        GovernanceLock afterStreet = lockApplicationService.streetSign(
                new StreetUnlockCommand(locked.getLockId(), STREET_USER, "sig-s"));
        assertEquals(GovernanceLockStatus.FULLY_UNLOCKED, afterStreet.getStatus());
        assertEquals(STREET_USER, afterStreet.getUnlockStreetUserId());
        assertNotNull(afterStreet.getUnlockAt(), "终签同步填充 unlock_at");
        assertTrue(afterStreet.isUnlocked());

        // 6) verifyLocked: 终态抛 LOCK_NOT_HELD
        GovernanceLockApplicationException notHeld = assertThrows(
                GovernanceLockApplicationException.class,
                () -> lockApplicationService.verifyLocked(TEST_TENANT_ID, type, entityId));
        assertEquals(GovernanceLockApplicationException.Reason.LOCK_NOT_HELD, notHeld.getReason());
    }

    @Test
    public void verifyLocked_onMissingEntity_throwsNotHeld() {
        GovernanceLockApplicationException ex = assertThrows(
                GovernanceLockApplicationException.class,
                () -> lockApplicationService.verifyLocked(
                        TEST_TENANT_ID, LockEntityType.FINANCE_DISCLOSURE, 9999999L));
        assertEquals(GovernanceLockApplicationException.Reason.LOCK_NOT_HELD, ex.getReason());
    }

    @Test
    public void committeeSign_onMissingLockId_throwsNotFound() {
        setRole("COMMITTEE_DIRECTOR", COMMITTEE_USER);
        GovernanceLockApplicationException ex = assertThrows(
                GovernanceLockApplicationException.class,
                () -> lockApplicationService.committeeSign(
                        new CommitteeUnlockCommand(9999999L, COMMITTEE_USER, "sig-c")));
        assertEquals(GovernanceLockApplicationException.Reason.LOCK_NOT_FOUND, ex.getReason());
    }

    @Test
    public void sameApproverForCommitteeAndStreet_translatesToSignerConflict() {
        // 锁 + 初签
        GovernanceLock locked = lockApplicationService.lock(
                new LockCommand(TEST_TENANT_ID, LockEntityType.FINANCE_DISCLOSURE, 5004L,
                        INITIATOR, HASH64));
        setRole("COMMITTEE_DIRECTOR", COMMITTEE_USER);
        lockApplicationService.committeeSign(
                new CommitteeUnlockCommand(locked.getLockId(), COMMITTEE_USER, "sig-c"));

        // 终签使用与初签同人 → 聚合根 IllegalStateException → application 层翻 LOCK_SIGNER_CONFLICT
        setRole("GOV_SUPER_ADMIN", COMMITTEE_USER);
        GovernanceLockApplicationException ex = assertThrows(
                GovernanceLockApplicationException.class,
                () -> lockApplicationService.streetSign(
                        new StreetUnlockCommand(locked.getLockId(), COMMITTEE_USER, "sig-s")));
        assertEquals(GovernanceLockApplicationException.Reason.LOCK_SIGNER_CONFLICT, ex.getReason());
    }

    @Test
    public void communityAdminCannotStreetSignEvenIfPermissionMisconfigured() {
        GovernanceLock locked = lockApplicationService.lock(
                new LockCommand(TEST_TENANT_ID, LockEntityType.FINANCE_DISCLOSURE, 5006L,
                        INITIATOR, HASH64));
        setRole("COMMITTEE_DIRECTOR", COMMITTEE_USER);
        lockApplicationService.committeeSign(
                new CommitteeUnlockCommand(locked.getLockId(), COMMITTEE_USER, "sig-c"));

        setRole("COMMUNITY_ADMIN", STREET_USER);
        GovernanceLockApplicationException ex = assertThrows(
                GovernanceLockApplicationException.class,
                () -> lockApplicationService.streetSign(
                        new StreetUnlockCommand(locked.getLockId(), STREET_USER, "sig-s")));
        assertEquals(GovernanceLockApplicationException.Reason.LOCK_ROLE_FORBIDDEN, ex.getReason());
    }
}
