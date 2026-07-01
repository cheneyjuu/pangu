package com.pangu.infrastructure.gateway;

import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.gateway.dto.OwnerQuery;
import com.pangu.domain.model.asset.OwnerProfile;
import com.pangu.domain.model.asset.OwnerPropertyDetail;
import com.pangu.domain.model.asset.OwnerSummary;
import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.infrastructure.persistence.entity.OwnerLookupRow;
import com.pangu.infrastructure.persistence.entity.OwnerProfileRow;
import com.pangu.infrastructure.persistence.entity.OwnerPropertyDetailRow;
import com.pangu.infrastructure.persistence.mapper.OwnerPropertyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    public List<OwnerSummary> searchOwnersByPhone(String phoneFragment, Long tenantId) {
        return ownerPropertyMapper.searchOwnersByPhoneFragment(tenantId, phoneFragment).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<OwnerSummary> listAllNominatableOwners(Long tenantId, int limit) {
        return ownerPropertyMapper.listNominatableOwnersWithName(tenantId, limit).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public Page<OwnerProfile> pageOwners(OwnerQuery query) {
        List<OwnerProfile> items = ownerPropertyMapper.pageOwners(query).stream()
                .map(this::toProfile)
                .toList();
        long total = ownerPropertyMapper.countOwners(query);
        return new Page<>(items, total, query.page(), query.size());
    }

    @Override
    public Optional<OwnerProfile> findOwnerProfile(Long uid, Long tenantId) {
        OwnerProfileRow row = ownerPropertyMapper.selectOwnerProfile(uid, tenantId);
        return Optional.ofNullable(row).map(this::toProfile);
    }

    @Override
    public List<OwnerPropertyDetail> listPropertiesByUid(Long uid, Long tenantId) {
        return ownerPropertyMapper.selectPropertiesByUid(uid, tenantId).stream()
                .map(this::toDetail)
                .toList();
    }

    private OwnerSummary toSummary(OwnerLookupRow row) {
        // row.realName 在手机号 fast-path 为 null；在姓名/拼音预扫描路径为 SM4 密文，由 application 解密。
        return OwnerSummary.builder()
                .uid(row.getUid())
                .phone(row.getPhone())
                .buildingId(row.getBuildingId())
                .roomId(row.getRoomId())
                .realName(row.getRealName())
                .build();
    }

    private OwnerProfile toProfile(OwnerProfileRow row) {
        return new OwnerProfile(
                row.getUid(),
                row.getAccountId(),
                row.getPhone(),
                row.getRealNameCipher(),
                row.getRealNameVerified(),
                row.getAuthLevel(),
                row.getAccountStatus(),
                row.getPropertyCount(),
                row.getTotalBuildArea(),
                row.getCreateTime());
    }

    private OwnerPropertyDetail toDetail(OwnerPropertyDetailRow row) {
        boolean delegate = row.getVotingDelegate() != null && row.getVotingDelegate() == 1;
        return new OwnerPropertyDetail(
                row.getOpid(),
                row.getBuildingId(),
                row.getRoomId(),
                row.getBuildArea(),
                delegate,
                row.getAccountStatus());
    }
}
