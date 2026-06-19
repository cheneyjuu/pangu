package com.pangu.domain.repository;

import com.pangu.domain.model.disclosure.DisclosureType;
import com.pangu.domain.model.disclosure.FinanceDisclosureSnapshot;

import java.util.Optional;

/**
 * 财务公示快照仓储端口（Hexagonal Port）。
 *
 * <p>application 层通过本接口操作 {@link FinanceDisclosureSnapshot} 聚合根，
 * 不直接 import MyBatis Mapper / 实体行类型。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/FinanceDisclosureRepositoryImpl}。
 */
public interface FinanceDisclosureRepository {

    /** 仅读，不加锁；按主键查询。 */
    Optional<FinanceDisclosureSnapshot> findById(Long snapshotId);

    /** 按主键加 SELECT FOR UPDATE 行锁，供 lockAndPublish 内部使用。 */
    Optional<FinanceDisclosureSnapshot> findByIdForUpdate(Long snapshotId);

    /**
     * 查询同 (tenant, type, period) 下当前最大 statisticsVersion 的快照（不论状态）。
     * compose 用于决定下一个 statisticsVersion 编号。
     *
     * @return 若不存在则返回空
     */
    Optional<FinanceDisclosureSnapshot> findLatestByPeriod(
            Long tenantId, DisclosureType disclosureType, String period);

    /**
     * 新增快照。返回带数据库主键的聚合。
     *
     * @throws DuplicateSnapshotException 触发 {@code uk_disc_period} / {@code uidx_disc_latest} 冲突
     */
    FinanceDisclosureSnapshot insert(FinanceDisclosureSnapshot snapshot);

    /**
     * 更新已存在的快照；带 version 乐观锁，零行影响时抛 {@link OptimisticLockException}。
     *
     * <p>调用方必须保证：调用 {@code markLocked(...)} 后的 update 把 status / governance_lock_id /
     * locked_at 一并推到同一条 UPDATE，否则 trigger 9 会反弹。
     */
    void update(FinanceDisclosureSnapshot snapshot);

    /**
     * 唯一索引冲突信号（同 tenant + type + period + statisticsVersion，或 LOCKED/PUBLISHED 唯一）。
     * 由 application 层映射为业务错误码 {@code SNAPSHOT_DUPLICATE}。
     */
    class DuplicateSnapshotException extends RuntimeException {
        public DuplicateSnapshotException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 乐观锁失败信号。 */
    class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}
