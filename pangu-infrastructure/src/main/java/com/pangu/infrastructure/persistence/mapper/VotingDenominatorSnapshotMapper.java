package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.DenominatorItemRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 投票分母快照 Mapper。
 *
 * <p>承担 {@code DefaultVotingDenominatorResolver} 的双重去重计算与快照写入：
 * <ul>
 *   <li>{@link #selectDenominatorItems}：基于 {@code c_owner_property} 的窗口函数双重去重；</li>
 *   <li>{@link #upsertSnapshot}：议题维度幂等 upsert（同议题重复 settle 仅刷新该行）；</li>
 *   <li>{@link #deleteItemsBySnapshotId} + {@link #insertItems}：先删后插同步行级明细。</li>
 * </ul>
 */
@Mapper
public interface VotingDenominatorSnapshotMapper {

    /**
     * 双重去重查询分母候选行：每个 room 仅返回一行（代表业主 + 楼栋 + 面积 + 资格标记）。
     *
     * @param tenantId         租户 ID
     * @param scope            分母范围 dbValue（1=COMMUNITY，2=BUILDING）；UNIT 不在此查询，
     *                         需调用方提前拒绝
     * @param scopeReferenceId BUILDING 时为 building_id；COMMUNITY 时可为 null
     */
    List<DenominatorItemRow> selectDenominatorItems(@Param("tenantId") Long tenantId,
                                                     @Param("scope") int scope,
                                                     @Param("scopeReferenceId") Long scopeReferenceId);

    /**
     * 议题维度幂等 upsert，返回 snapshot_id（PG INSERT ... RETURNING）。
     */
    Long upsertSnapshot(@Param("subjectId") Long subjectId,
                        @Param("scope") int scope,
                        @Param("scopeReferenceId") Long scopeReferenceId,
                        @Param("totalArea") BigDecimal totalArea,
                        @Param("totalOwnerCount") long totalOwnerCount,
                        @Param("itemCount") long itemCount,
                        @Param("aggregateHash") String aggregateHash);

    int deleteItemsBySnapshotId(@Param("snapshotId") Long snapshotId);

    int insertItems(@Param("snapshotId") Long snapshotId,
                    @Param("items") List<DenominatorItemRow> items);
}
