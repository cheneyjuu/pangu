package com.pangu.domain.model.community;

/**
 * 有效房屋名册聚合出的社区楼栋目录。
 */
public record CommunityBuilding(
        Long buildingId,
        String buildingName,
        long unitCount,
        long roomCount
) {
}
