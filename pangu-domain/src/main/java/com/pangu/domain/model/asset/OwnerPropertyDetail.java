package com.pangu.domain.model.asset;

import java.math.BigDecimal;

/**
 * 业主名册详情页 · 房产逐条视图。
 *
 * <p>{@link PropertyOwnership} 没有 {@code building_id / build_area} 字段，
 * 详情页需展示楼栋号与产权面积，故单独建模。
 */
public record OwnerPropertyDetail(
        Long opid,
        Long buildingId,
        Long roomId,
        BigDecimal buildArea,
        boolean votingDelegate,
        Integer accountStatus
) {
}
