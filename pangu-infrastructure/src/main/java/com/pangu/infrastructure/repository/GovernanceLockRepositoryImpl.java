package com.pangu.infrastructure.repository;

import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.model.lock.GovernanceLockStatus;
import com.pangu.domain.model.lock.LockEntityType;
import com.pangu.domain.repository.GovernanceLockRepository;
import com.pangu.infrastructure.persistence.entity.GovernanceLockRow;
import com.pangu.infrastructure.persistence.mapper.GovernanceLockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link GovernanceLockRepository} 默认实现：MyBatis row ↔ 聚合根双向翻译。
 *
 * <p>关键约定：
 * <ul>
 *   <li>{@link DuplicateKeyException}（{@code uidx_lock_entity} 唯一索引触发）
 *       转译为领域端口的 {@link GovernanceLockRepository.DuplicateLockException}；</li>
 *   <li>乐观锁失败（{@code update} 影响行数为 0）转译为
 *       {@link GovernanceLockRepository.OptimisticLockException}；</li>
 *   <li>聚合根 ↔ row 的 status 序列化使用 {@link GovernanceLockStatus#getDbValue()} /
 *       {@link GovernanceLockStatus#fromDbValue(int)}；entityType 直接保存枚举名。</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class GovernanceLockRepositoryImpl implements GovernanceLockRepository {

    private final GovernanceLockMapper mapper;

    @Override
    public Optional<GovernanceLock> findByEntityForUpdate(Long tenantId, LockEntityType entityType, Long entityId) {
        GovernanceLockRow row = mapper.selectByEntityForUpdate(tenantId, entityType.name(), entityId);
        return Optional.ofNullable(row).map(this::toAggregate);
    }

    @Override
    public Optional<GovernanceLock> findById(Long lockId) {
        return Optional.ofNullable(mapper.selectById(lockId)).map(this::toAggregate);
    }

    @Override
    public Optional<GovernanceLock> findByIdForUpdate(Long lockId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(lockId)).map(this::toAggregate);
    }

    @Override
    public GovernanceLock insert(GovernanceLock lock) {
        GovernanceLockRow row = toRow(lock);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new DuplicateLockException(
                    "uidx_lock_entity violated for tenantId=" + lock.getTenantId()
                            + " entityType=" + lock.getEntityType()
                            + " entityId=" + lock.getEntityId(), e);
        }
        lock.setLockId(row.getLockId());
        return lock;
    }

    @Override
    public void update(GovernanceLock lock) {
        GovernanceLockRow row = toRow(lock);
        int affected = mapper.update(row);
        if (affected == 0) {
            throw new OptimisticLockException(
                    "Optimistic lock failed for lockId=" + lock.getLockId()
                            + " expectedVersion=" + lock.getVersion());
        }
        lock.setVersion(lock.getVersion() + 1);
    }

    // ===== row ↔ aggregate translators =====

    private GovernanceLockRow toRow(GovernanceLock l) {
        GovernanceLockRow r = new GovernanceLockRow();
        r.setLockId(l.getLockId());
        r.setTenantId(l.getTenantId());
        r.setEntityType(l.getEntityType().name());
        r.setEntityId(l.getEntityId());
        r.setLockedByUserId(l.getLockedByUserId());
        r.setLockedAt(l.getLockedAt());
        r.setLockPayloadHash(l.getLockPayloadHash());
        r.setStatus(l.getStatus().getDbValue());
        r.setUnlockCommitteeUserId(l.getUnlockCommitteeUserId());
        r.setUnlockCommitteeAt(l.getUnlockCommitteeAt());
        r.setUnlockCommitteeSignature(l.getUnlockCommitteeSignature());
        r.setUnlockStreetUserId(l.getUnlockStreetUserId());
        r.setUnlockStreetAt(l.getUnlockStreetAt());
        r.setUnlockStreetSignature(l.getUnlockStreetSignature());
        r.setUnlockAt(l.getUnlockAt());
        r.setVersion(l.getVersion());
        return r;
    }

    private GovernanceLock toAggregate(GovernanceLockRow r) {
        return GovernanceLock.rehydrate(
                r.getLockId(), r.getTenantId(),
                LockEntityType.fromDbValue(r.getEntityType()), r.getEntityId(),
                r.getLockedByUserId(), r.getLockedAt(), r.getLockPayloadHash(),
                GovernanceLockStatus.fromDbValue(r.getStatus()),
                r.getUnlockCommitteeUserId(), r.getUnlockCommitteeAt(), r.getUnlockCommitteeSignature(),
                r.getUnlockStreetUserId(), r.getUnlockStreetAt(), r.getUnlockStreetSignature(),
                r.getUnlockAt(), r.getVersion());
    }
}
