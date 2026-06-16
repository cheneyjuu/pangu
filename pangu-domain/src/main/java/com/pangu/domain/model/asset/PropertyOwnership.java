package com.pangu.domain.model.asset;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 房产所有权与投票代表权关系领域模型 (PropertyOwnership 实体)
 * 连接物理房产(PropertyUnit)与社区业主身份(OwnerIdentity)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyOwnership {

    /** 关联物理房产房间 ID */
    private Long roomId;

    /** 关联社区业主 ID (OPID) */
    private Long opid;

    /** 是否指定行使表决权的代表：true-是代表账号, false-非代表 */
    private boolean isVotingRepresentative;

    /** 房屋绑定账单与认证状态：1-正常, 2-欠费挂起, 3-冻结 */
    private Integer accountStatus;

    /**
     * 判定该产权关系是否具备计票有效性
     * 必须是指定的投票代表，且账户处于正常状态（非欠费挂起/冻结，依方案C具体执行）
     */
    public boolean isValidForVoting() {
        return this.isVotingRepresentative && Integer.valueOf(1).equals(this.accountStatus);
    }
}
