package com.pangu.bootstrap.disclosure;

import com.pangu.domain.model.disclosure.DisclosureStatus;
import com.pangu.domain.model.disclosure.DisclosureType;
import com.pangu.domain.model.disclosure.FinanceDisclosureSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FinanceDisclosureSnapshot} 聚合根的纯域行为测试（与 {@code GovernanceLockStateMachineTest} 同风格）。
 *
 * <p>验证：
 * <ul>
 *   <li>静态工厂 {@code compose} 字段校验（tenant/period/type/payload/hash/userId/version 全非空且合法）；</li>
 *   <li>正向链路 DRAFT → LOCKED → PUBLISHED → REVISING → DRAFT 全允许；</li>
 *   <li>跨级跳转、相同状态再流转、终止以外状态强迁均被拒；</li>
 *   <li>{@code markLocked} 同步落 status / governanceLockId / lockedAt（trigger 9 单 UPDATE 必备）；</li>
 *   <li>{@code rehydrate} 完整恢复字段。</li>
 * </ul>
 */
public class DisclosureStateMachineTest {

    private static final Long TENANT = 9001L;
    private static final String PERIOD = "2026-06";
    private static final String PAYLOAD = "{\"x\":1}";
    private static final String HASH = "a".repeat(64);
    private static final Long COMPOSER = 7001L;
    private static final Long LOCK_ID = 5500L;

    private FinanceDisclosureSnapshot newDraft() {
        return FinanceDisclosureSnapshot.compose(
                TENANT, PERIOD, DisclosureType.MAINTENANCE_FUND,
                PAYLOAD, HASH, COMPOSER, 1);
    }

    // ===== 工厂校验 =====

    @Test
    public void compose_rejectsNullArgs() {
        assertThrows(IllegalArgumentException.class, () -> FinanceDisclosureSnapshot.compose(
                null, PERIOD, DisclosureType.MAINTENANCE_FUND, PAYLOAD, HASH, COMPOSER, 1));
        assertThrows(IllegalArgumentException.class, () -> FinanceDisclosureSnapshot.compose(
                TENANT, null, DisclosureType.MAINTENANCE_FUND, PAYLOAD, HASH, COMPOSER, 1));
        assertThrows(IllegalArgumentException.class, () -> FinanceDisclosureSnapshot.compose(
                TENANT, " ", DisclosureType.MAINTENANCE_FUND, PAYLOAD, HASH, COMPOSER, 1));
        assertThrows(IllegalArgumentException.class, () -> FinanceDisclosureSnapshot.compose(
                TENANT, PERIOD, null, PAYLOAD, HASH, COMPOSER, 1));
        assertThrows(IllegalArgumentException.class, () -> FinanceDisclosureSnapshot.compose(
                TENANT, PERIOD, DisclosureType.MAINTENANCE_FUND, null, HASH, COMPOSER, 1));
        assertThrows(IllegalArgumentException.class, () -> FinanceDisclosureSnapshot.compose(
                TENANT, PERIOD, DisclosureType.MAINTENANCE_FUND, PAYLOAD, "abc", COMPOSER, 1));
        assertThrows(IllegalArgumentException.class, () -> FinanceDisclosureSnapshot.compose(
                TENANT, PERIOD, DisclosureType.MAINTENANCE_FUND, PAYLOAD, HASH, null, 1));
        assertThrows(IllegalArgumentException.class, () -> FinanceDisclosureSnapshot.compose(
                TENANT, PERIOD, DisclosureType.MAINTENANCE_FUND, PAYLOAD, HASH, COMPOSER, 0));
    }

    @Test
    public void compose_initialStateIsDraft() {
        FinanceDisclosureSnapshot s = newDraft();
        assertEquals(DisclosureStatus.DRAFT, s.getStatus());
        assertEquals(1, s.getStatisticsVersion());
        assertEquals(PAYLOAD, s.getDataPayload());
        assertEquals(HASH, s.getPayloadHash());
        assertNotNull(s.getComposedAt());
        assertNull(s.getGovernanceLockId());
        assertNull(s.getLockedAt());
        assertNull(s.getPublishedAt());
        assertFalse(s.isReadableByOwner());
    }

    // ===== 正向链路 =====

    @Test
    public void forwardChain_draftToReviseAndBack_allTransitionsAllowed() {
        FinanceDisclosureSnapshot s = newDraft();

        s.markLocked(LOCK_ID);
        assertEquals(DisclosureStatus.LOCKED, s.getStatus());
        assertEquals(LOCK_ID, s.getGovernanceLockId());
        assertNotNull(s.getLockedAt());
        // 必须三字段同步落地，trigger 9 才允许
        assertNull(s.getPublishedAt());

        s.markPublished();
        assertEquals(DisclosureStatus.PUBLISHED, s.getStatus());
        assertNotNull(s.getPublishedAt());
        assertTrue(s.isReadableByOwner());

        s.startRevise();
        assertEquals(DisclosureStatus.REVISING, s.getStatus());
        assertFalse(s.isReadableByOwner());

        // REVISING → DRAFT（开启新 statisticsVersion 周期）
        s.transitionTo(DisclosureStatus.DRAFT);
        assertEquals(DisclosureStatus.DRAFT, s.getStatus());
    }

    // ===== 跨级跳转 =====

    @Test
    public void cannotSkipFromDraftToPublished() {
        FinanceDisclosureSnapshot s = newDraft();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> s.transitionTo(DisclosureStatus.PUBLISHED));
        assertTrue(ex.getMessage().contains("DRAFT -> PUBLISHED"));
    }

    @Test
    public void cannotPublishWithoutLocking() {
        FinanceDisclosureSnapshot s = newDraft();
        // markPublished 内部走状态机：DRAFT→PUBLISHED 不在白名单
        assertThrows(IllegalStateException.class, s::markPublished);
    }

    @Test
    public void cannotMarkLockedWithoutLockId() {
        FinanceDisclosureSnapshot s = newDraft();
        assertThrows(IllegalArgumentException.class, () -> s.markLocked(null));
    }

    @Test
    public void cannotTransitionToSameStatus() {
        FinanceDisclosureSnapshot s = newDraft();
        assertThrows(IllegalStateException.class,
                () -> s.transitionTo(DisclosureStatus.DRAFT));
    }

    // ===== rehydrate =====

    @Test
    public void rehydrate_restoresAllFields() {
        java.time.Instant t = java.time.Instant.now();
        FinanceDisclosureSnapshot s = FinanceDisclosureSnapshot.rehydrate(
                42L, TENANT, PERIOD, DisclosureType.MAINTENANCE_FUND,
                DisclosureStatus.PUBLISHED, PAYLOAD, 3, HASH,
                COMPOSER, t.minusSeconds(60),
                LOCK_ID, t.minusSeconds(30), t,
                7L);
        assertEquals(42L, s.getSnapshotId());
        assertEquals(TENANT, s.getTenantId());
        assertEquals(PERIOD, s.getPeriod());
        assertEquals(DisclosureStatus.PUBLISHED, s.getStatus());
        assertEquals(3, s.getStatisticsVersion());
        assertEquals(LOCK_ID, s.getGovernanceLockId());
        assertNotNull(s.getPublishedAt());
        assertEquals(7L, s.getVersion());
        assertTrue(s.isReadableByOwner());
    }
}
