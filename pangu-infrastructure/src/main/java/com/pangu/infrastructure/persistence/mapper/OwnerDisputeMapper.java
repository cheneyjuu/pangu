package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.OwnerDisputeRow;
import com.pangu.domain.model.user.WorkIdentityBuildingScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * t_owner_dispute Mapper（{@code DisputeRepositoryImpl} 用）。
 *
 * <ul>
 *   <li>{@link #selectByIdForUpdate} 加 {@code FOR UPDATE} 行锁，配合乐观锁兜底；</li>
 *   <li>{@link #insert} 由 V2.8 trigger 10 在 BEFORE INSERT 校验状态/层级一致性；</li>
 *   <li>{@link #update} 带 version 乐观锁，零行影响表示版本失配；同时 V2.8 trigger 10
 *       在 BEFORE UPDATE 校验单调递增/不可跳级/closed_at 一致性。</li>
 * </ul>
 */
@Mapper
public interface OwnerDisputeMapper {

    OwnerDisputeRow selectById(@Param("disputeId") Long disputeId);

    OwnerDisputeRow selectByIdForUpdate(@Param("disputeId") Long disputeId);

    /** 业主"我的异议"列表（按 raised_at DESC）。 */
    List<OwnerDisputeRow> selectByOwner(@Param("tenantId") Long tenantId,
                                        @Param("ownerId") Long ownerId,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    /** 仲裁工作台：level / status 任一为 null 时不过滤该字段。 */
    List<OwnerDisputeRow> selectForJurisdiction(@Param("tenantId") Long tenantId,
                                                @Param("level") Integer level,
                                                @Param("status") String status,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    List<OwnerDisputeRow> selectForJurisdictionByBuildingScopes(
            @Param("buildingScopes") Set<WorkIdentityBuildingScope> buildingScopes,
            @Param("level") Integer level,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    int insert(OwnerDisputeRow row);

    /** 更新；带 version 乐观锁；返回 0 行表示版本失配。 */
    int update(OwnerDisputeRow row);
}
