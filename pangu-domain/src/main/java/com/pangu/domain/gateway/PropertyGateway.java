package com.pangu.domain.gateway;

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
}
