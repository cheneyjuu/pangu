package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.GovernanceLockRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * t_governance_lock 全字段 Mapper（{@code GovernanceLockRepositoryImpl} 用）。
 *
 * <p>三层并发防御中的「DB 行锁 + 唯一索引」：
 * <ul>
 *   <li>{@link #selectByEntityForUpdate} 加 {@code FOR UPDATE}，避免同一 (tenant, entity_type, entity_id)
 *       上的双线程乐观锁竞争；</li>
 *   <li>{@link #insert} 命中 {@code uidx_lock_entity} 唯一索引时由 Spring 抛
 *       {@link org.springframework.dao.DuplicateKeyException}，
 *       由 Repository 适配器翻译为领域端口异常；</li>
 *   <li>{@link #update} 带 version 乐观锁；返回 0 行表示版本失配。</li>
 * </ul>
 */
@Mapper
public interface GovernanceLockMapper {

    /** 按主键读（不加锁）。 */
    GovernanceLockRow selectById(@Param("lockId") Long lockId);

    /** 按主键加 {@code FOR UPDATE} 行锁。 */
    GovernanceLockRow selectByIdForUpdate(@Param("lockId") Long lockId);

    /**
     * 按 (tenant_id, entity_type, entity_id) 查询当前锁记录并加 {@code FOR UPDATE} 行锁。
     * 返回 null 表示该 entity 当前没有锁。
     */
    GovernanceLockRow selectByEntityForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("entityType") String entityType,
                                              @Param("entityId") Long entityId);

    /** 新增；MyBatis {@code useGeneratedKeys} 回填 lock_id；唯一索引冲突由调用方翻译。 */
    int insert(GovernanceLockRow row);

    /**
     * 更新整行；带 version 乐观锁 —— 仅当 version 与传入相同才更新。
     * 返回受影响行数；0 表示乐观锁失败。
     */
    int update(GovernanceLockRow row);
}
