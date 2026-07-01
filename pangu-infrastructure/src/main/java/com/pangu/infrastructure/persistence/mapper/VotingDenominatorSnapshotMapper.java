package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.DenominatorItemRow;
import com.pangu.infrastructure.persistence.entity.DenominatorSnapshotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 投票分母快照 Mapper。
 *
 * <p>承担 {@code DefaultVotingDenominatorResolver} 的双重去重计算与冻结快照写入：
 * <ul>
 *   <li>{@link #selectDenominatorItems}：基于 {@code c_owner_property} 的窗口函数双重去重；</li>
 *   <li>{@link #insertSnapshotIfAbsent}：议题维度首次插入，同议题复用既有冻结快照；</li>
 *   <li>{@link #deleteItemsBySnapshotId} + {@link #insertItems}：仅首次冻结时写入行级明细。</li>
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

    DenominatorSnapshotRow selectSnapshotBySubjectId(@Param("subjectId") Long subjectId);

    /**
     * 议题维度首次插入，返回 snapshot_id；已存在时返回 null，由调用方读取既有冻结快照。
     */
    Long insertSnapshotIfAbsent(@Param("subjectId") Long subjectId,
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
