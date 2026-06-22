package com.pangu.domain.gateway;

import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.dto.OwnerQuery;
import com.pangu.domain.model.asset.OwnerProfile;
import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.model.asset.OwnerSummary;
import com.pangu.domain.model.asset.PropertyOwnership;

import java.util.List;
import java.util.Optional;

/**
 * 房产业主资产关系领域网关接口
 */
public interface PropertyGateway {

    /**
     * 判断某个自然人用户在特定小区名下是否存在任意欠费房产记录
     */
    boolean hasUnpaidFees(Long uid, Long tenantId);

    /**
     * 获取业主名下的房产绑定列表
     */
    List<PropertyOwnership> getOwnerships(Long uid, Long tenantId);

    /**
     * 按手机号前缀检索本小区业主（用于换届选举提名候选人时定位业主）。
     *
     * @param phonePrefix 手机号前缀（明文）
     * @param tenantId    小区租户 ID（仅返回在该租户名下有房产的业主）
     * @return 命中业主摘要列表（uid / 手机号 / 楼栋 / 房号），上限由实现限制
     */
    List<OwnerSummary> searchOwnersByPhone(String phonePrefix, Long tenantId);

    /**
     * 业主名册分页查询（M4 读侧）。
     *
     * <p>支持手机号前缀 / 楼栋 / 房号过滤，行级数据范围由 {@code @DataScope} 注入。
     * 排序：{@code uid DESC}（新业主优先），与现有读侧默认一致。
     */
    Page<OwnerProfile> pageOwners(OwnerQuery query);

    /**
     * 按 uid + 租户查询业主名册详情聚合（不含房产明细，房产明细单独走
     * {@link #listPropertiesByUid(Long, Long)}）。
     */
    Optional<OwnerProfile> findOwnerProfile(Long uid, Long tenantId);

    /**
     * 业主在指定租户下的房产明细（详情页用，含楼栋/面积/代表权/账户状态）。
     */
    List<OwnerPropertyDetail> listPropertiesByUid(Long uid, Long tenantId);
}
