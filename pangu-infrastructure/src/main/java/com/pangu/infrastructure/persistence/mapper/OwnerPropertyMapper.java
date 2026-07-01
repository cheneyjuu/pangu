package com.pangu.infrastructure.persistence.mapper;

import com.pangu.domain.gateway.dto.OwnerQuery;
import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.infrastructure.persistence.annotation.DataScope;
import com.pangu.infrastructure.persistence.entity.OwnerLookupRow;
import com.pangu.infrastructure.persistence.entity.OwnerProfileRow;
import com.pangu.infrastructure.persistence.entity.OwnerPropertyDetailRow;
import com.pangu.infrastructure.persistence.entity.OwnerPropertyVotingViewRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 业主房产关系 MyBatis Mapper 接口 (OPID 实体)
 */
@Mapper
public interface OwnerPropertyMapper {

    /**
     * 根据自然人 ID 及小区租户 ID，查询其绑定的所有房产产权关系列表
     */
    List<PropertyOwnership> selectOwnershipsByUid(@Param("uid") Long uid, @Param("tenantId") Long tenantId);

    /**
     * 统计某个自然人用户在特定小区名下存在欠费记录的房产数量
     * 用于 C端 ABAC 资格审查 (账户状态 account_status != 1 视为非正常/欠费)
     */
    int countUnpaidProperties(@Param("uid") Long uid, @Param("tenantId") Long tenantId);

    /**
     * 根据 opid 查询特定房产绑定关系
     */
    PropertyOwnership selectByOpid(@Param("opid") Long opid);

    /**
     * 管理端网格员根据楼栋管辖范围查询房产关系列表 (行级数据范围过滤)
     */
    @DataScope(buildingAlias = "op")
    List<PropertyOwnership> selectOwnershipsByBuilding(@Param("tenantId") Long tenantId);

    /**
     * M3-2 投票提交：根据 opid 查询完整投票视图（含 uid/tenant_id/building_id/build_area）。
     */
    OwnerPropertyVotingViewRow selectVotingViewByOpid(@Param("opid") Long opid);

    /**
     * M3-2 "我的议题"：列出某业主在某租户下涉及的全部楼栋 ID。
     */
    List<Long> selectBuildingIdsByUid(@Param("uid") Long uid, @Param("tenantId") Long tenantId);

    /**
     * 按手机号片段（前缀 / 中段 / 尾号）模糊检索本租户业主。
     * phone {@code LIKE '%xxx%'} 不走索引前缀但租户内业主规模有限，可接受；上限 20 条。
     */
    List<OwnerLookupRow> searchOwnersByPhoneFragment(@Param("tenantId") Long tenantId,
                                                    @Param("phoneFragment") String phoneFragment);

    /**
     * 拉取本租户全部可提名业主含 SM4 密文 {@code real_name}（供 application 层解密做姓名/拼音匹配）。
     */
    List<OwnerLookupRow> listNominatableOwnersWithName(@Param("tenantId") Long tenantId,
                                                       @Param("limit") int limit);

    /**
     * 业主名册分页查询（M4 读侧）。
     *
     * <p>聚合 {@code t_account / c_user / c_owner_property} 三表，按 uid 分组统计
     * {@code propertyCount} 与 {@code totalBuildArea}；行级数据范围由 {@code @DataScope} 注入
     * （{@code op.building_id IN (...)}）。
     */
    @DataScope(buildingAlias = "op")
    List<OwnerProfileRow> pageOwners(@Param("q") OwnerQuery q);

    /**
     * 业主名册分页总数（与 {@link #pageOwners(OwnerQuery)} 同款过滤）。
     */
    @DataScope(buildingAlias = "op")
    long countOwners(@Param("q") OwnerQuery q);

    /**
     * 业主名册详情（按 uid + tenant 聚合）。
     */
    @DataScope(buildingAlias = "op")
    OwnerProfileRow selectOwnerProfile(@Param("uid") Long uid, @Param("tenantId") Long tenantId);

    /**
     * 业主在指定租户下的房产明细（详情页用）。
     */
    @DataScope(buildingAlias = "op")
    List<OwnerPropertyDetailRow> selectPropertiesByUid(@Param("uid") Long uid,
                                                      @Param("tenantId") Long tenantId);
}
