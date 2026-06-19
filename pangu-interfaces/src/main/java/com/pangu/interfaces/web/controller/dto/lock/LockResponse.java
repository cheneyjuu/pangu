package com.pangu.interfaces.web.controller.dto.lock;

import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.model.lock.GovernanceLockStatus;
import com.pangu.domain.model.lock.LockEntityType;

import java.time.Instant;

/**
 * 治理锁响应体（POST 返回 / 后续 GET 详情通用）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>暴露稳定的业务字段 + 双签审计字段，便于前端展示「谁锁的 / 谁初签 / 谁终签」；</li>
 *   <li>不暴露 createTime / updateTime（此为 DB 维度时间戳，业务以 lockedAt / unlockAt 为准）。</li>
 * </ul>
 */
public record LockResponse(
        Long lockId,
        Long tenantId,
        LockEntityType entityType,
        Long entityId,
        Long lockedByUserId,
        Instant lockedAt,
        String lockPayloadHash,
        GovernanceLockStatus status,
        Long unlockCommitteeUserId,
        Instant unlockCommitteeAt,
        String unlockCommitteeSignature,
        Long unlockStreetUserId,
        Instant unlockStreetAt,
        String unlockStreetSignature,
        Instant unlockAt,
        long version
) {
    public static LockResponse from(GovernanceLock lock) {
        return new LockResponse(
                lock.getLockId(),
                lock.getTenantId(),
                lock.getEntityType(),
                lock.getEntityId(),
                lock.getLockedByUserId(),
                lock.getLockedAt(),
                lock.getLockPayloadHash(),
                lock.getStatus(),
                lock.getUnlockCommitteeUserId(),
                lock.getUnlockCommitteeAt(),
                lock.getUnlockCommitteeSignature(),
                lock.getUnlockStreetUserId(),
                lock.getUnlockStreetAt(),
                lock.getUnlockStreetSignature(),
                lock.getUnlockAt(),
                lock.getVersion()
        );
    }
}
