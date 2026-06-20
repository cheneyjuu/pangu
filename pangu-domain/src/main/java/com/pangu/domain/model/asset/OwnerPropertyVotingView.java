package com.pangu.domain.model.asset;

import java.math.BigDecimal;

/**
 * 业主投票视角下的房产视图（M3-2 引入）。
 *
 * <p>为什么不复用 {@link PropertyOwnership}：后者只承载结算用的 {@code roomId/opid/...}，
 * 投票提交链路需要校验 {@code uid 归属 / building_id 范围 / build_area} 等额外字段，
 * 直接在领域层定义一个一次性视图避免污染既有模型。
 *
 * @param opid              业主身份 ID
 * @param uid               自然人 ID（用于 opid 归属校验）
 * @param tenantId          租户 ID（防跨租户引用）
 * @param buildingId        楼栋 ID（用于 scope=BUILDING 议题范围校验）
 * @param buildArea         建筑面积（投票时落 t_vote_item.property_area 快照）
 * @param votingDelegate    是否指定的投票代表（false 表示未指定，本次不允许投票）
 * @param accountStatus     1=正常, 2=欠费挂起, 3=冻结
 */
public record OwnerPropertyVotingView(
        Long opid,
        Long uid,
        Long tenantId,
        Long buildingId,
        BigDecimal buildArea,
        boolean votingDelegate,
        int accountStatus
) {
    /** 是否处于可投票状态：投票代表 + 账户正常。 */
    public boolean isValidForVoting() {
        return votingDelegate && accountStatus == 1;
    }
}
