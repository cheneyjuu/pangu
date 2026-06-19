package com.pangu.domain.repository;

import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.model.lock.LockEntityType;

import java.util.Optional;

/**
 * 通用治理锁仓储端口（Hexagonal Port）。
 *
 * <p>application 层通过本接口操作 {@link GovernanceLock} 聚合根，
 * 不直接 import MyBatis Mapper / 实体行类型。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/GovernanceLockRepositoryImpl}。
 */
public interface GovernanceLockRepository {

    /**
     * 按 (tenant_id, entity_type, entity_id) 查询当前锁记录并加 SELECT FOR UPDATE 行锁。
     * 用于「先 Redis 锁 → 再 DB 行锁 → 再唯一索引」的三层并发防御。
     */
    Optional<GovernanceLock> findByEntityForUpdate(Long tenantId, LockEntityType entityType, Long entityId);

    /**
     * 仅读，不加锁；按主键查询。
     */
    Optional<GovernanceLock> findById(Long lockId);

    /**
     * 按主键加 SELECT FOR UPDATE 行锁。供解锁双签 use case 内部调用。
     */
    Optional<GovernanceLock> findByIdForUpdate(Long lockId);

    /**
     * 新增锁。返回带数据库主键的聚合。
     *
     * @throws DuplicateLockException 触发 {@code uidx_lock_entity} 唯一索引冲突
     */
    GovernanceLock insert(GovernanceLock lock);

    /**
     * 更新已存在的锁；带 version 乐观锁，零行影响时抛 {@link OptimisticLockException}。
     */
    void update(GovernanceLock lock);

    /**
     * 唯一索引冲突信号（uidx_lock_entity：同 tenant + entity_type + entity_id 至多一条记录）。
     * 由 application 层映射为业务错误码（{@code LOCK_ALREADY_EXISTS}）。
     */
    class DuplicateLockException extends RuntimeException {
        public DuplicateLockException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 乐观锁失败信号。
     */
    class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}
