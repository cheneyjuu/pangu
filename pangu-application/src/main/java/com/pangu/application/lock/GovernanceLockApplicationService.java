package com.pangu.application.lock;

import com.pangu.application.lock.command.CommitteeUnlockCommand;
import com.pangu.application.lock.command.LockCommand;
import com.pangu.application.lock.command.StreetUnlockCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.lock.DistributedLockTemplate;
import com.pangu.domain.lock.DistributedLockTemplate.DistributedLockAcquisitionException;
import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.model.lock.LockEntityType;
import com.pangu.domain.repository.GovernanceLockRepository;
import com.pangu.domain.repository.GovernanceLockRepository.DuplicateLockException;
import com.pangu.domain.repository.GovernanceLockRepository.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/**
 * 通用治理锁编排服务。
 *
 * <p>编排四条 use case：
 * <ol>
 *   <li>{@link #lock} 锁定（NEW → LOCKED），写入唯一索引；</li>
 *   <li>{@link #committeeSign} 业委会主任解锁初签（LOCKED → COMMITTEE_SIGNED）；</li>
 *   <li>{@link #streetSign} 街道办解锁终签（COMMITTEE_SIGNED → FULLY_UNLOCKED + unlockAt）；</li>
 *   <li>{@link #verifyLocked} 校验当前锁仍处于「锁定 / 单签未解锁」状态，供 disclosure 修改前校验。</li>
 * </ol>
 *
 * <p>{@link #lock} 的并发与去重防御为三层（与 {@code WaiverApplicationService.submitDraft} 对齐）：
 * <pre>
 *   ① Redis 红线锁（{@code lock:gov:tenant:{T}:type:{X}:entity:{E}}）—— 单进程串行化；
 *   ② DB SELECT FOR UPDATE  —— 跨进程串行化、防止两个红线锁拿在不同实例；
 *   ③ 唯一索引 uidx_lock_entity —— 兜底，捕获 DuplicateKeyException 转友好错误码。
 * </pre>
 *
 * <p>本服务对外只抛 {@link GovernanceLockApplicationException}；状态机与同人双签校验由聚合根
 * {@link GovernanceLock} 完成，本服务捕获 {@link IllegalStateException} 转译。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GovernanceLockApplicationService {

    /** Redis 锁 TTL（远大于单次事务执行时间，但短于业务上限以防死锁）。 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final String COMMITTEE_UNLOCK_ROLE = "COMMITTEE_DIRECTOR";
    private static final String STREET_UNLOCK_ROLE = "GOV_SUPER_ADMIN";

    private final GovernanceLockRepository lockRepository;
    private final DistributedLockTemplate lockTemplate;
    private final TransactionTemplate transactionTemplate;
    private final UserContextHolder userContextHolder;

    /**
     * 锁定指定业务实体。先 Redis 锁串行化，再事务内行锁查询，最后落库由唯一索引兜底。
     */
    public GovernanceLock lock(LockCommand cmd) {
        String key = String.format("lock:gov:tenant:%d:type:%s:entity:%d",
                cmd.tenantId(), cmd.entityType().name(), cmd.entityId());
        try {
            return lockTemplate.executeWithLock(key, LOCK_TTL, () ->
                    transactionTemplate.execute(status -> doLockWithinTx(cmd)));
        } catch (DistributedLockAcquisitionException e) {
            throw new GovernanceLockApplicationException(
                    GovernanceLockApplicationException.Reason.LOCK_ALREADY_EXISTS,
                    "目标实体正在被另一线程锁定，请稍后再试", e);
        }
    }

    /**
     * 锁内事务体：行锁查询同 entity → 聚合根工厂 → 持久化（唯一索引兜底）。
     */
    private GovernanceLock doLockWithinTx(LockCommand cmd) {
        lockRepository.findByEntityForUpdate(cmd.tenantId(), cmd.entityType(), cmd.entityId())
                .ifPresent(existing -> {
                    throw new GovernanceLockApplicationException(
                            GovernanceLockApplicationException.Reason.LOCK_ALREADY_EXISTS,
                            "实体已存在锁定记录 lockId=" + existing.getLockId());
                });
        GovernanceLock lock = GovernanceLock.lock(
                cmd.tenantId(), cmd.entityType(), cmd.entityId(),
                cmd.lockedByUserId(), cmd.lockPayloadHash());
        try {
            return lockRepository.insert(lock);
        } catch (DuplicateLockException e) {
            throw new GovernanceLockApplicationException(
                    GovernanceLockApplicationException.Reason.LOCK_ALREADY_EXISTS,
                    "实体已存在锁定记录（唯一索引兜底）", e);
        }
    }

    /** 业委会主任解锁初签。 */
    @Transactional
    public GovernanceLock committeeSign(CommitteeUnlockCommand cmd) {
        requireRole(COMMITTEE_UNLOCK_ROLE, "仅业委会主任可执行治理锁初签");
        GovernanceLock lock = loadForUpdate(cmd.lockId());
        try {
            lock.signByCommittee(cmd.approverUserId(), cmd.signature());
        } catch (IllegalStateException e) {
            throw mapStateException(e);
        }
        try {
            lockRepository.update(lock);
        } catch (OptimisticLockException e) {
            throw new GovernanceLockApplicationException(
                    GovernanceLockApplicationException.Reason.LOCK_CONCURRENT_MODIFICATION,
                    "治理锁已被其他操作并发修改，请刷新后重试", e);
        }
        return lock;
    }

    /** 街道办解锁终签。 */
    @Transactional
    public GovernanceLock streetSign(StreetUnlockCommand cmd) {
        requireRole(STREET_UNLOCK_ROLE, "仅街道办超管可执行治理锁终签");
        GovernanceLock lock = loadForUpdate(cmd.lockId());
        try {
            lock.signByStreet(cmd.approverUserId(), cmd.signature());
        } catch (IllegalStateException e) {
            throw mapStateException(e);
        }
        try {
            lockRepository.update(lock);
        } catch (OptimisticLockException e) {
            throw new GovernanceLockApplicationException(
                    GovernanceLockApplicationException.Reason.LOCK_CONCURRENT_MODIFICATION,
                    "治理锁已被其他操作并发修改，请刷新后重试", e);
        }
        return lock;
    }

    /**
     * 校验目标 entity 当前仍持有「未完全解锁」的治理锁。供 M2-3 disclosure 修改前调用。
     *
     * @throws GovernanceLockApplicationException 锁不存在 / 已 FULLY_UNLOCKED 时抛 LOCK_NOT_HELD
     */
    public void verifyLocked(Long tenantId, LockEntityType entityType, Long entityId) {
        GovernanceLock lock = lockRepository
                .findByEntityForUpdate(tenantId, entityType, entityId)
                .orElseThrow(() -> new GovernanceLockApplicationException(
                        GovernanceLockApplicationException.Reason.LOCK_NOT_HELD,
                        "目标实体未被锁定 entityType=" + entityType + " entityId=" + entityId));
        if (lock.isUnlocked()) {
            throw new GovernanceLockApplicationException(
                    GovernanceLockApplicationException.Reason.LOCK_NOT_HELD,
                    "目标实体的治理锁已被双签解锁 lockId=" + lock.getLockId());
        }
    }

    private GovernanceLock loadForUpdate(Long lockId) {
        return lockRepository.findByIdForUpdate(lockId)
                .orElseThrow(() -> new GovernanceLockApplicationException(
                        GovernanceLockApplicationException.Reason.LOCK_NOT_FOUND,
                        "治理锁不存在 lockId=" + lockId));
    }

    private void requireRole(String expectedRole, String message) {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !expectedRole.equals(ctx.roleKey())) {
            throw new GovernanceLockApplicationException(
                    GovernanceLockApplicationException.Reason.LOCK_ROLE_FORBIDDEN,
                    message + "，当前角色=" + (ctx == null ? "ANONYMOUS" : ctx.roleKey()));
        }
    }

    private GovernanceLockApplicationException mapStateException(IllegalStateException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("终签与初签审批人不能为同一人")) {
            return new GovernanceLockApplicationException(
                    GovernanceLockApplicationException.Reason.LOCK_SIGNER_CONFLICT, msg, e);
        }
        return new GovernanceLockApplicationException(
                GovernanceLockApplicationException.Reason.LOCK_INVALID_TRANSITION, msg, e);
    }
}
