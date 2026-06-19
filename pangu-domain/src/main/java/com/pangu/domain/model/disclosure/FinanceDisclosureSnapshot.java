package com.pangu.domain.model.disclosure;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 财务公示快照聚合根。
 *
 * <p>核心职责：
 * <ul>
 *   <li>承载一期公示的不可变载荷（{@link #dataPayload} + {@link #payloadHash}）；</li>
 *   <li>维护 DRAFT → LOCKED → PUBLISHED → REVISING → DRAFT 状态机；</li>
 *   <li>{@code markLocked(...)} 同步落 {@link #governanceLockId} + {@link #lockedAt} + status——
 *       <b>必须在同一次 UPDATE 内推进</b>，否则 DB trigger 9
 *       会因「LOCKED 状态 governance_lock_id 为空」抛出，详见 V2.7 注释；</li>
 *   <li>不直接持久化（由 application 层调用 repository 完成）；</li>
 *   <li>不引入 Spring/Lombok 之外的框架依赖（保持 domain 框架轻量，与 {@code GovernanceLock} 一致）。</li>
 * </ul>
 */
public class FinanceDisclosureSnapshot {

    /** 合法状态流转（来源 → 允许的目标集），与 trigger 9 一致。 */
    private static final Map<DisclosureStatus, Set<DisclosureStatus>> ALLOWED_TRANSITIONS;
    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(DisclosureStatus.class);
        ALLOWED_TRANSITIONS.put(DisclosureStatus.DRAFT,
                EnumSet.of(DisclosureStatus.LOCKED));
        ALLOWED_TRANSITIONS.put(DisclosureStatus.LOCKED,
                EnumSet.of(DisclosureStatus.PUBLISHED));
        ALLOWED_TRANSITIONS.put(DisclosureStatus.PUBLISHED,
                EnumSet.of(DisclosureStatus.REVISING));
        ALLOWED_TRANSITIONS.put(DisclosureStatus.REVISING,
                EnumSet.of(DisclosureStatus.DRAFT));
    }

    private Long snapshotId;
    private Long tenantId;
    private String period;
    private DisclosureType disclosureType;
    private DisclosureStatus status;

    private String dataPayload;       // canonical JSON
    private int statisticsVersion;
    private String payloadHash;       // 64-hex SHA-256

    private Long composedByUserId;
    private Instant composedAt;

    private Long governanceLockId;
    private Instant lockedAt;
    private Instant publishedAt;

    private long version;

    private FinanceDisclosureSnapshot() {
    }

    /**
     * 构造一条 DRAFT 状态的新快照。composedAt 由聚合根内取时间，
     * 与 {@code GovernanceLock.lock(...)} 风格一致。
     */
    public static FinanceDisclosureSnapshot compose(
            Long tenantId, String period, DisclosureType disclosureType,
            String dataPayload, String payloadHash,
            Long composedByUserId, int statisticsVersion) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period must not be blank");
        }
        if (disclosureType == null) {
            throw new IllegalArgumentException("disclosureType must not be null");
        }
        if (dataPayload == null || dataPayload.isBlank()) {
            throw new IllegalArgumentException("dataPayload must not be blank");
        }
        if (payloadHash == null || payloadHash.length() != 64) {
            throw new IllegalArgumentException("payloadHash must be 64-hex SHA256");
        }
        if (composedByUserId == null) {
            throw new IllegalArgumentException("composedByUserId must not be null");
        }
        if (statisticsVersion < 1) {
            throw new IllegalArgumentException("statisticsVersion must be >= 1");
        }
        FinanceDisclosureSnapshot s = new FinanceDisclosureSnapshot();
        s.tenantId = tenantId;
        s.period = period;
        s.disclosureType = disclosureType;
        s.status = DisclosureStatus.DRAFT;
        s.dataPayload = dataPayload;
        s.statisticsVersion = statisticsVersion;
        s.payloadHash = payloadHash;
        s.composedByUserId = composedByUserId;
        s.composedAt = Instant.now();
        return s;
    }

    /**
     * DRAFT → LOCKED：同步落 governanceLockId + lockedAt + status。
     *
     * <p><b>调用约束</b>：repository 执行 update 时必须把 status / governance_lock_id / locked_at
     * 三字段一并下推到同一条 UPDATE，否则 trigger 9 会因「LOCKED 状态必须挂治理锁」反弹。
     */
    public void markLocked(Long governanceLockId) {
        if (governanceLockId == null) {
            throw new IllegalArgumentException("governanceLockId must not be null");
        }
        transitionTo(DisclosureStatus.LOCKED);
        this.governanceLockId = governanceLockId;
        this.lockedAt = Instant.now();
    }

    /** LOCKED → PUBLISHED：填 publishedAt，状态推进。 */
    public void markPublished() {
        transitionTo(DisclosureStatus.PUBLISHED);
        this.publishedAt = Instant.now();
    }

    /** PUBLISHED → REVISING：进入修订态，等待新 statisticsVersion 的 DRAFT 起步。 */
    public void startRevise() {
        transitionTo(DisclosureStatus.REVISING);
    }

    /**
     * 不可逆状态机流转校验。
     *
     * @throws IllegalStateException 不允许的流转
     */
    public void transitionTo(DisclosureStatus target) {
        if (target == null) {
            throw new IllegalArgumentException("target status must not be null");
        }
        if (status == target) {
            throw new IllegalStateException("Cannot transition to the same status: " + status);
        }
        Set<DisclosureStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(status,
                EnumSet.noneOf(DisclosureStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException(
                    "Illegal status transition: " + status + " -> " + target);
        }
        this.status = target;
    }

    public boolean isReadableByOwner() {
        return status != null && status.isReadableByOwner();
    }

    // === 属性访问（与 GovernanceLock 风格一致） ===

    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public Long getTenantId() { return tenantId; }
    public String getPeriod() { return period; }
    public DisclosureType getDisclosureType() { return disclosureType; }
    public DisclosureStatus getStatus() { return status; }
    public String getDataPayload() { return dataPayload; }
    public int getStatisticsVersion() { return statisticsVersion; }
    public String getPayloadHash() { return payloadHash; }
    public Long getComposedByUserId() { return composedByUserId; }
    public Instant getComposedAt() { return composedAt; }
    public Long getGovernanceLockId() { return governanceLockId; }
    public Instant getLockedAt() { return lockedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    /**
     * Repository 重建用：仅供持久化层使用。
     */
    public static FinanceDisclosureSnapshot rehydrate(
            Long snapshotId, Long tenantId, String period, DisclosureType disclosureType,
            DisclosureStatus status, String dataPayload, int statisticsVersion, String payloadHash,
            Long composedByUserId, Instant composedAt,
            Long governanceLockId, Instant lockedAt, Instant publishedAt,
            long version) {
        FinanceDisclosureSnapshot s = new FinanceDisclosureSnapshot();
        s.snapshotId = snapshotId;
        s.tenantId = tenantId;
        s.period = period;
        s.disclosureType = disclosureType;
        s.status = status;
        s.dataPayload = dataPayload;
        s.statisticsVersion = statisticsVersion;
        s.payloadHash = payloadHash;
        s.composedByUserId = composedByUserId;
        s.composedAt = composedAt;
        s.governanceLockId = governanceLockId;
        s.lockedAt = lockedAt;
        s.publishedAt = publishedAt;
        s.version = version;
        return s;
    }
}
