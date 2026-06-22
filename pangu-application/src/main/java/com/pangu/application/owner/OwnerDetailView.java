package com.pangu.application.owner;

import com.pangu.domain.model.asset.OwnerPropertyDetail;

import java.util.List;

/**
 * 业主名册详情聚合视图：业主本体 + 房产明细列表。
 */
public record OwnerDetailView(
        OwnerProfileView profile,
        List<OwnerPropertyDetail> properties
) {
}
