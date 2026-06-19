package com.pangu.domain.model.lock;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 通用治理锁聚合根。
 *
 * <p>核心职责：
 * <ul>
 *   <li>承载锁定时刻的快照 hash + 双签解锁的全部审计字段（committee/street）；</li>
 *   <li>维护严格的不可逆状态机，禁止跨级跳转 / 终止态再流转 / 同一审批人初签与终签；</li>
 *   <li>不直接持久化（由 application 层调用 repository 完成）；</li>
 *   <li>不引入 Spring/Lombok 之外的框架依赖（保持 domain 框架轻量，与 {@code PartyRatioWaiver} 一致）。</li>
 * </ul>
 *
 * <p>本聚合根与 {@code PartyRatioWaiver} 在本期保持 disjoint：M1 党员比例放宽申请的双签字段
 * 仍写在自己表里，本聚合只承载新增的 3 类 entityType（FINANCE_DISCLOSURE /
 * ELECTION_DISCLOSURE / FUND_LEDGER_PUBLISH）。
 */
public class GovernanceLock {

    /** 合法状态流转（来源 → 允许的目标集）。 */
    private static final Map<GovernanceLockStatus, Set<GovernanceLockStatus>> ALLOWED_TRANSITIONS;
    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(GovernanceLockStatus.class);
        ALLOWED_TRANSITIONS.put(GovernanceLockStatus.LOCKED,
                EnumSet.of(GovernanceLockStatus.COMMITTEE_SIGNED));
        ALLOWED_TRANSITIONS.put(GovernanceLockStatus.COMMITTEE_SIGNED,
                EnumSet.of(GovernanceLockStatus.FULLY_UNLOCKED));
        // 终止态不再允许任何流转
        ALLOWED_TRANSITIONS.put(GovernanceLockStatus.FULLY_UNLOCKED,
                EnumSet.noneOf(GovernanceLockStatus.class));
    }

    private Long lockId;
    private Long tenantId;
    private LockEntityType entityType;
    private Long entityId;
    private Long lockedByUserId;
    private Instant lockedAt;
    private String lockPayloadHash;

    private GovernanceLockStatus status;

    private Long unlockCommitteeUserId;
    private Instant unlockCommitteeAt;
    private String unlockCommitteeSignature;

    private Long unlockStreetUserId;
    private Instant unlockStreetAt;
    private String unlockStreetSignature;

    private Instant unlockAt;

    private long version;

    private GovernanceLock() {
    }

    /**
     * 构造一条 LOCKED 状态的新锁。锁定瞬间立即生成 lockedAt（聚合根内取时间，
     * 与 {@code PartyRatioWaiver.lockLocalPayloadHash} 风格一致）。
     */
    public static GovernanceLock lock(Long tenantId, LockEntityType entityType, Long entityId,
                                       Long lockedByUserId, String lockPayloadHash) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (entityType == null) {
            throw new IllegalArgumentException("entityType must not be null");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        if (lockedByUserId == null) {
            throw new IllegalArgumentException("lockedByUserId must not be null");
        }
        if (lockPayloadHash == null || lockPayloadHash.length() != 64) {
            throw new IllegalArgumentException("lockPayloadHash must be 64-hex SHA256");
        }
        GovernanceLock lock = new GovernanceLock();
        lock.tenantId = tenantId;
        lock.entityType = entityType;
        lock.entityId = entityId;
        lock.lockedByUserId = lockedByUserId;
        lock.lockPayloadHash = lockPayloadHash;
        lock.lockedAt = Instant.now();
        lock.status = GovernanceLockStatus.LOCKED;
        return lock;
    }

    /**
     * 不可逆状态机流转校验。
     *
     * @throws IllegalStateException 不允许的流转
     */
    public void transitionTo(GovernanceLockStatus target) {
        if (target == null) {
            throw new IllegalArgumentException("target status must not be null");
        }
        if (status == target) {
            throw new IllegalStateException("Cannot transition to the same status: " + status);
        }
        Set<GovernanceLockStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(status,
                EnumSet.noneOf(GovernanceLockStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException(
                    "Illegal status transition: " + status + " -> " + target);
        }
        this.status = target;
    }

    /**
     * 业委会主任解锁初签：流转到 COMMITTEE_SIGNED。
     *
     * <p>权限由 controller 层 {@code @PreAuthorize("hasAuthority('lock:unlock:committee')")} 校验；
     * 本聚合仅校验状态机 + 审批人非空。
     */
    public void signByCommittee(Long approverUserId, String signature) {
        requireApprover(approverUserId, "业委会主任解锁初签人");
        if (status != GovernanceLockStatus.LOCKED) {
            throw new IllegalStateException(
                    "Only LOCKED can be signed by committee, current=" + status);
        }
        this.unlockCommitteeUserId = approverUserId;
        this.unlockCommitteeAt = Instant.now();
        this.unlockCommitteeSignature = signature;
        transitionTo(GovernanceLockStatus.COMMITTEE_SIGNED);
    }

    /**
     * 街道办解锁终签：流转到 FULLY_UNLOCKED，并同步置 {@code unlockAt}。
     *
     * <p>权限由 controller 层 {@code @PreAuthorize("hasAuthority('lock:unlock:street')")} 校验；
     * 本聚合校验状态机 + 终签人不可与初签人重合（trigger 8 + chk_unlock_signers_diff 兜底）。
     */
    public void signByStreet(Long approverUserId, String signature) {
        requireApprover(approverUserId, "街道办解锁终签人");
        if (status != GovernanceLockStatus.COMMITTEE_SIGNED) {
            throw new IllegalStateException(
                    "Only COMMITTEE_SIGNED can be signed by street, current=" + status);
        }
        if (unlockCommitteeUserId != null && unlockCommitteeUserId.equals(approverUserId)) {
            throw new IllegalStateException("终签与初签审批人不能为同一人");
        }
        this.unlockStreetUserId = approverUserId;
        this.unlockStreetAt = Instant.now();
        this.unlockStreetSignature = signature;
        this.unlockAt = Instant.now();
        transitionTo(GovernanceLockStatus.FULLY_UNLOCKED);
    }

    /**
     * @return 该锁是否已被双签解锁。供 application 层 verifyLocked 使用。
     */
    public boolean isUnlocked() {
        return status == GovernanceLockStatus.FULLY_UNLOCKED;
    }

    private void requireApprover(Long approverUserId, String label) {
        if (approverUserId == null) {
            throw new IllegalArgumentException(label + " 必须提供");
        }
    }

    // === 属性访问（保持框架轻量；application 层 builder 模式手写） ===

    public Long getLockId() { return lockId; }
    public void setLockId(Long lockId) { this.lockId = lockId; }
    public Long getTenantId() { return tenantId; }
    public LockEntityType getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public Long getLockedByUserId() { return lockedByUserId; }
    public Instant getLockedAt() { return lockedAt; }
    public String getLockPayloadHash() { return lockPayloadHash; }
    public GovernanceLockStatus getStatus() { return status; }
    public Long getUnlockCommitteeUserId() { return unlockCommitteeUserId; }
    public Instant getUnlockCommitteeAt() { return unlockCommitteeAt; }
    public String getUnlockCommitteeSignature() { return unlockCommitteeSignature; }
    public Long getUnlockStreetUserId() { return unlockStreetUserId; }
    public Instant getUnlockStreetAt() { return unlockStreetAt; }
    public String getUnlockStreetSignature() { return unlockStreetSignature; }
    public Instant getUnlockAt() { return unlockAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    /**
     * Repository 重建用：仅供持久化层使用。
     */
    public static GovernanceLock rehydrate(
            Long lockId, Long tenantId, LockEntityType entityType, Long entityId,
            Long lockedByUserId, Instant lockedAt, String lockPayloadHash,
            GovernanceLockStatus status,
            Long unlockCommitteeUserId, Instant unlockCommitteeAt, String unlockCommitteeSignature,
            Long unlockStreetUserId, Instant unlockStreetAt, String unlockStreetSignature,
            Instant unlockAt, long version) {
        GovernanceLock lock = new GovernanceLock();
        lock.lockId = lockId;
        lock.tenantId = tenantId;
        lock.entityType = entityType;
        lock.entityId = entityId;
        lock.lockedByUserId = lockedByUserId;
        lock.lockedAt = lockedAt;
        lock.lockPayloadHash = lockPayloadHash;
        lock.status = status;
        lock.unlockCommitteeUserId = unlockCommitteeUserId;
        lock.unlockCommitteeAt = unlockCommitteeAt;
        lock.unlockCommitteeSignature = unlockCommitteeSignature;
        lock.unlockStreetUserId = unlockStreetUserId;
        lock.unlockStreetAt = unlockStreetAt;
        lock.unlockStreetSignature = unlockStreetSignature;
        lock.unlockAt = unlockAt;
        lock.version = version;
        return lock;
    }
}
