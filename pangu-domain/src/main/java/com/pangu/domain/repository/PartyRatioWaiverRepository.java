package com.pangu.domain.repository;

import com.pangu.domain.model.waiver.PartyRatioWaiver;

import java.util.Optional;

/**
 * 党员比例放宽申请仓储端口（Hexagonal Port）。
 *
 * <p>application 层通过本接口操作 {@link PartyRatioWaiver} 聚合根，
 * 不直接 import MyBatis Mapper / 实体行类型。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/PartyRatioWaiverRepositoryImpl}。
 */
public interface PartyRatioWaiverRepository {

    /**
     * 查询同议题当前的活跃 waiver（非终止态）并加 SELECT FOR UPDATE 行锁。
     * 用于「先 Redis 锁 → 再 DB 行锁 → 再唯一索引」的三层并发防御。
     *
     * <p>活跃状态：DRAFT / PENDING_COMMITTEE / PENDING_STREET / APPROVED；
     * 终止状态（REJECTED / REVOKED / REVOKED_BY_SYSTEM / APPLIED）不返回。
     *
     * @param subjectId 议题 ID
     * @return 活跃 waiver 聚合（如有）
     */
    Optional<PartyRatioWaiver> findActiveBySubjectIdForUpdate(Long subjectId);

    /**
     * 按主键查询并加行锁；用于审批 / 撤销路径。
     */
    Optional<PartyRatioWaiver> findByIdForUpdate(Long waiverId);

    /**
     * 仅读，不加锁（GET 详情用）。
     */
    Optional<PartyRatioWaiver> findById(Long waiverId);

    /**
     * 新增草稿/已提交申请。返回带数据库主键的聚合。
     *
     * @throws DuplicateActiveWaiverException 触发 {@code uidx_waiver_active_per_subject} 唯一索引冲突
     */
    PartyRatioWaiver insert(PartyRatioWaiver waiver);

    /**
     * 更新已存在的 waiver；带 version 乐观锁，零行影响时抛 {@link OptimisticLockException}。
     */
    void update(PartyRatioWaiver waiver);

    /**
     * 唯一索引冲突信号（部分唯一索引：同议题至多一条活跃 waiver）。
     * 由 application 层映射为业务错误码（{@code WAIVER_ALREADY_PENDING}）。
     */
    class DuplicateActiveWaiverException extends RuntimeException {
        public DuplicateActiveWaiverException(String message, Throwable cause) {
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
