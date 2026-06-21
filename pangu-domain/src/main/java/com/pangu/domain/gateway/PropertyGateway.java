package com.pangu.domain.gateway;

import com.pangu.domain.model.asset.OwnerSummary;
import com.pangu.domain.model.asset.PropertyOwnership;
import java.util.List;

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
}
