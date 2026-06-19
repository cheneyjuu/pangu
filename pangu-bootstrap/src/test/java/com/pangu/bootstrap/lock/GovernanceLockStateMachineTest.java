package com.pangu.bootstrap.lock;

import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.model.lock.GovernanceLockStatus;
import com.pangu.domain.model.lock.LockEntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GovernanceLock} 聚合根的纯域行为测试。
 *
 * <p>验证：
 * <ul>
 *   <li>静态工厂 {@link GovernanceLock#lock} 校验（tenantId/entityType/entityId/userId 不可空，
 *       lockPayloadHash 必须为 64-hex SHA256）；</li>
 *   <li>正向链路 LOCKED → COMMITTEE_SIGNED → FULLY_UNLOCKED 全链路允许；</li>
 *   <li>跨级跳转、终止态再流转、相同状态之间的流转必须被拒；</li>
 *   <li>同一审批人不可既任初签又任终签；</li>
 *   <li>FULLY_UNLOCKED 同步置 {@code unlockAt}；isUnlocked 反映状态。</li>
 * </ul>
 */
public class GovernanceLockStateMachineTest {

    private static final Long TENANT = 9001L;
    private static final Long ENTITY = 5001L;
    private static final Long INITIATOR = 7001L;
    private static final Long COMMITTEE_APPROVER = 7002L;
    private static final Long STREET_APPROVER = 8001L;
    private static final String HASH64 = "a".repeat(64);

    private GovernanceLock newLock() {
        return GovernanceLock.lock(TENANT, LockEntityType.FINANCE_DISCLOSURE, ENTITY,
                INITIATOR, HASH64);
    }

    // ===== 工厂校验 =====

    @Test
    public void lock_rejectsNullArgs() {
        assertThrows(IllegalArgumentException.class, () -> GovernanceLock.lock(
                null, LockEntityType.FINANCE_DISCLOSURE, ENTITY, INITIATOR, HASH64));
        assertThrows(IllegalArgumentException.class, () -> GovernanceLock.lock(
                TENANT, null, ENTITY, INITIATOR, HASH64));
        assertThrows(IllegalArgumentException.class, () -> GovernanceLock.lock(
                TENANT, LockEntityType.FINANCE_DISCLOSURE, null, INITIATOR, HASH64));
        assertThrows(IllegalArgumentException.class, () -> GovernanceLock.lock(
                TENANT, LockEntityType.FINANCE_DISCLOSURE, ENTITY, null, HASH64));
    }

    @Test
    public void lock_rejectsInvalidHash() {
        assertThrows(IllegalArgumentException.class, () -> GovernanceLock.lock(
                TENANT, LockEntityType.FINANCE_DISCLOSURE, ENTITY, INITIATOR, null));
        assertThrows(IllegalArgumentException.class, () -> GovernanceLock.lock(
                TENANT, LockEntityType.FINANCE_DISCLOSURE, ENTITY, INITIATOR, "abc"));
        assertThrows(IllegalArgumentException.class, () -> GovernanceLock.lock(
                TENANT, LockEntityType.FINANCE_DISCLOSURE, ENTITY, INITIATOR, "x".repeat(63)));
    }

    @Test
    public void lock_initialStateIsLockedAndStampsLockedAt() {
        GovernanceLock l = newLock();
        assertEquals(GovernanceLockStatus.LOCKED, l.getStatus());
        assertNotNull(l.getLockedAt());
        assertNull(l.getUnlockAt());
        assertNull(l.getUnlockCommitteeUserId());
        assertNull(l.getUnlockStreetUserId());
        assertFalse(l.isUnlocked());
    }

    // ===== 正向链路 =====

    @Test
    public void forwardChain_allTransitionsAllowed_andUnlockAtAtomic() {
        GovernanceLock l = newLock();

        l.signByCommittee(COMMITTEE_APPROVER, "sig-c");
        assertEquals(GovernanceLockStatus.COMMITTEE_SIGNED, l.getStatus());
        assertEquals(COMMITTEE_APPROVER, l.getUnlockCommitteeUserId());
        assertNotNull(l.getUnlockCommitteeAt());
        assertEquals("sig-c", l.getUnlockCommitteeSignature());
        // 初签后仍未完全解锁
        assertNull(l.getUnlockAt());
        assertFalse(l.isUnlocked());

        l.signByStreet(STREET_APPROVER, "sig-s");
        assertEquals(GovernanceLockStatus.FULLY_UNLOCKED, l.getStatus());
        assertEquals(STREET_APPROVER, l.getUnlockStreetUserId());
        assertNotNull(l.getUnlockStreetAt());
        assertEquals("sig-s", l.getUnlockStreetSignature());
        // 终签同步填充 unlockAt
        assertNotNull(l.getUnlockAt());
        assertTrue(l.isUnlocked());
        assertTrue(l.getStatus().isTerminal());
    }

    // ===== 跨级跳转 =====

    @Test
    public void cannotSkipFromLockedToFullyUnlocked() {
        GovernanceLock l = newLock();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> l.transitionTo(GovernanceLockStatus.FULLY_UNLOCKED));
        assertTrue(ex.getMessage().contains("LOCKED -> FULLY_UNLOCKED"));
    }

    @Test
    public void cannotStreetSignBeforeCommitteeSign() {
        GovernanceLock l = newLock();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> l.signByStreet(STREET_APPROVER, "sig-s"));
        assertTrue(ex.getMessage().contains("Only COMMITTEE_SIGNED"));
    }

    @Test
    public void cannotCommitteeSignTwice() {
        GovernanceLock l = newLock();
        l.signByCommittee(COMMITTEE_APPROVER, "sig-c");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> l.signByCommittee(COMMITTEE_APPROVER, "sig-c2"));
        assertTrue(ex.getMessage().contains("Only LOCKED"));
    }

    // ===== 终止态不可流转 =====

    @Test
    public void cannotTransitionFromFullyUnlockedTerminal() {
        GovernanceLock l = newLock();
        l.signByCommittee(COMMITTEE_APPROVER, "sig-c");
        l.signByStreet(STREET_APPROVER, "sig-s");
        assertTrue(l.getStatus().isTerminal());
        assertThrows(IllegalStateException.class,
                () -> l.transitionTo(GovernanceLockStatus.LOCKED));
        assertThrows(IllegalStateException.class,
                () -> l.signByCommittee(COMMITTEE_APPROVER, "sig-c2"));
    }

    // ===== 双签强制（初/终签不可同人） =====

    @Test
    public void streetSignerCannotBeSameAsCommitteeSigner() {
        GovernanceLock l = newLock();
        l.signByCommittee(COMMITTEE_APPROVER, "sig-c");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> l.signByStreet(COMMITTEE_APPROVER, "sig-s"));
        assertTrue(ex.getMessage().contains("终签与初签审批人不能为同一人"));
    }

    @Test
    public void approverMustNotBeNull() {
        GovernanceLock l = newLock();
        assertThrows(IllegalArgumentException.class,
                () -> l.signByCommittee(null, "sig-c"));
        l.signByCommittee(COMMITTEE_APPROVER, "sig-c");
        assertThrows(IllegalArgumentException.class,
                () -> l.signByStreet(null, "sig-s"));
    }
}
