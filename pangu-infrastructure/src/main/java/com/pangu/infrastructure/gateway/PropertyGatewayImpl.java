package com.pangu.infrastructure.gateway;

import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.model.asset.OwnerSummary;
import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.infrastructure.persistence.entity.OwnerLookupRow;
import com.pangu.infrastructure.persistence.mapper.OwnerPropertyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 房产业主资产网关 MyBatis 基础设施层具体实现
 */
@Repository
public class PropertyGatewayImpl implements PropertyGateway {

    @Autowired
    private OwnerPropertyMapper ownerPropertyMapper;

    @Override
    public boolean hasUnpaidFees(Long uid, Long tenantId) {
        // 统计欠费状态房产数，大于0则说明存在欠费
        return ownerPropertyMapper.countUnpaidProperties(uid, tenantId) > 0;
    }

    @Override
    public List<PropertyOwnership> getOwnerships(Long uid, Long tenantId) {
        return ownerPropertyMapper.selectOwnershipsByUid(uid, tenantId);
    }

    @Override
    public List<OwnerSummary> searchOwnersByPhone(String phonePrefix, Long tenantId) {
        return ownerPropertyMapper.searchOwnersByPhonePrefix(tenantId, phonePrefix).stream()
                .map(this::toSummary)
                .toList();
    }

    private OwnerSummary toSummary(OwnerLookupRow row) {
        return OwnerSummary.builder()
                .uid(row.getUid())
                .phone(row.getPhone())
                .buildingId(row.getBuildingId())
                .roomId(row.getRoomId())
                .build();
    }
}
