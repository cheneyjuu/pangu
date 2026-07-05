package com.pangu.interfaces.web.controller.dto.owner;

import com.pangu.application.owner.OwnerDetailView;
import com.pangu.application.owner.OwnerProfileView;
import com.pangu.domain.model.asset.OwnerPropertyDetail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 业主名册详情响应：业主聚合视图 + 房产明细列表。
 */
public record OwnerDetailResponse(
        OwnerProfilePart profile,
        List<OwnerPropertyItem> properties
) {

    public static OwnerDetailResponse from(OwnerDetailView v) {
        return new OwnerDetailResponse(
                OwnerProfilePart.from(v.profile()),
                v.properties().stream().map(OwnerPropertyItem::from).toList());
    }

    /** 业主本体（与列表 {@link OwnerListResponse} 字段一致）。 */
    public record OwnerProfilePart(
            Long uid,
            String realName,
            String phoneMasked,
            Integer realNameVerified,
            Integer authLevel,
            Integer accountStatus,
            Integer propertyCount,
            BigDecimal totalBuildArea,
            LocalDateTime createTime
    ) {
        public static OwnerProfilePart from(OwnerProfileView v) {
            return new OwnerProfilePart(
                    v.uid(),
                    v.realName(),
                    PhoneMasker.mask(v.phone()),
                    v.realNameVerified(),
                    v.authLevel(),
                    v.accountStatus(),
                    v.propertyCount(),
                    v.totalBuildArea(),
                    v.createTime());
        }
    }

    /** 房产逐条明细。 */
    public record OwnerPropertyItem(
            Long opid,
            Long tenantId,
            String communityName,
            Long buildingId,
            String buildingName,
            String unitName,
            Long roomId,
            String roomName,
            BigDecimal buildArea,
            boolean jointOwnership,
            boolean votingDelegate,
            Integer accountStatus,
            String verifyType,
            String verifyStatus
    ) {
        public static OwnerPropertyItem from(OwnerPropertyDetail d) {
            return new OwnerPropertyItem(
                    d.opid(),
                    d.tenantId(),
                    d.communityName(),
                    d.buildingId(),
                    d.buildingName(),
                    d.unitName(),
                    d.roomId(),
                    d.roomName(),
                    d.buildArea(),
                    d.jointOwnership(),
                    d.votingDelegate(),
                    d.accountStatus(),
                    d.verifyType(),
                    d.verifyStatus());
        }
    }
}
