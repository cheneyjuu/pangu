package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.FinanceDisclosureRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * t_finance_disclosure_snapshot Mapper（{@code FinanceDisclosureRepositoryImpl} 用）。
 *
 * <p>关键约定：
 * <ul>
 *   <li>{@link #insert} JSONB 字段走 {@code CAST(#{dataPayload} AS JSONB)}；
 *       命中 {@code uk_disc_period} / {@code uidx_disc_latest} 唯一索引时 Spring 抛
 *       {@link org.springframework.dao.DuplicateKeyException}；</li>
 *   <li>{@link #update} 带 version 乐观锁 + 一次 UPDATE 同时下推
 *       status / governance_lock_id / locked_at / published_at，
 *       否则 trigger 9 会因「LOCKED 状态 governance_lock_id 为空」反弹；</li>
 *   <li>{@link #selectLatestByPeriod} 取同 (tenant, type, period) 下 statisticsVersion 最大者，
 *       compose 用于决定下一个版本号。</li>
 * </ul>
 */
@Mapper
public interface FinanceDisclosureMapper {

    FinanceDisclosureRow selectById(@Param("snapshotId") Long snapshotId);

    FinanceDisclosureRow selectByIdForUpdate(@Param("snapshotId") Long snapshotId);

    /** 同 (tenant, type, period) 下 statisticsVersion 最大者。 */
    FinanceDisclosureRow selectLatestByPeriod(@Param("tenantId") Long tenantId,
                                              @Param("disclosureType") String disclosureType,
                                              @Param("period") String period);

    /** 当前租户某类已公示快照中最新的一期。 */
    FinanceDisclosureRow selectLatestPublished(@Param("tenantId") Long tenantId,
                                                @Param("disclosureType") String disclosureType,
                                                @Param("publishedStatus") Integer publishedStatus);

    int insert(FinanceDisclosureRow row);

    int update(FinanceDisclosureRow row);
}
