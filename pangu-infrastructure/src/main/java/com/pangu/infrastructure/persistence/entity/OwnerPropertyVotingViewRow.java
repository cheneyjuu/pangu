package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * c_owner_property 行 → {@code OwnerPropertyVotingView} 中转。
 *
 * <p>独立于 {@link com.pangu.domain.model.asset.PropertyOwnership}：投票链路要校验
 * uid/tenant/building/area，结算视图只关心 roomId/opid。
 */
@Data
public class OwnerPropertyVotingViewRow {

    private Long opid;
    private Long uid;
    private Long tenantId;
    private Long buildingId;
    private BigDecimal buildArea;
    /** is_voting_delegate：0=非代表，1=投票代表。 */
    private int votingDelegate;
    /** 1=正常, 2=欠费挂起, 3=冻结。 */
    private int accountStatus;
}
