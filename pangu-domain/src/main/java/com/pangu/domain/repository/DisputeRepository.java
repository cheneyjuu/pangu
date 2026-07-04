package com.pangu.domain.repository;

import com.pangu.domain.model.dispute.Dispute;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 业主异议主表仓储端口（Hexagonal Port）。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/DisputeRepositoryImpl}。
 */
public interface DisputeRepository {

    Optional<Dispute> findById(Long disputeId);

    /** 按主键加 SELECT FOR UPDATE 行锁，供 mutating 路径内部使用。 */
    Optional<Dispute> findByIdForUpdate(Long disputeId);

    /** 业主"我的异议"列表（按 raised_at DESC，limit/offset 简单分页）。 */
    List<Dispute> findByOwner(Long tenantId, Long ownerId, int limit, int offset);

    /** 仲裁工作台：按层级 + 状态过滤；level/status 任一为 null 时不过滤该字段。 */
    List<Dispute> findForJurisdiction(Long tenantId, Integer reviewLevel, String status,
                                      int limit, int offset);

    /** 网格员调解工作台：按关联房产所在 tenant/building 范围过滤。 */
    List<Dispute> findForJurisdictionByBuildingScopes(Set<WorkIdentityBuildingScope> buildingScopes,
                                                      Integer reviewLevel,
                                                      String status,
                                                      int limit,
                                                      int offset);

    /**
     * 新增异议。返回带数据库主键的聚合。
     */
    Dispute insert(Dispute dispute);

    /**
     * 乐观锁更新；零行影响时抛 {@link OptimisticLockException}。
     *
     * <p>调用方必须保证 update 与 decision insert 顺序：先 update 主表 status 转 DECIDED_LEVEL_N_<KIND>，
     * 再 insert decision，否则 V2.8 trigger 11 会反弹。
     */
    void update(Dispute dispute);

    /** 乐观锁失败信号。 */
    class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}
