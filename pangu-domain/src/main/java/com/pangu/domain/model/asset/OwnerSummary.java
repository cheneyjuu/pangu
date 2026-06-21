package com.pangu.domain.model.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 业主检索摘要领域模型。
 *
 * <p>用于「按手机号关联业主」场景（如换届选举提名候选人时定位业主）：
 * 仅承载辨识业主所需的最小字段——自然人 {@code uid}、登录手机号、代表房产的楼栋/房号。
 * 不含姓名（{@code real_name} 为 SM4 加密列，不在检索链路解密）。
 */
@Getter
@Builder
@AllArgsConstructor
public class OwnerSummary {

    /** 业主自然人 ID（c_user.uid）。 */
    private final Long uid;

    /** 登录手机号（t_account.phone，明文唯一）。 */
    private final String phone;

    /** 代表房产所属楼栋 ID（同一业主多套房时取其一）。 */
    private final Long buildingId;

    /** 代表房产房间 ID（同一业主多套房时取其一）。 */
    private final Long roomId;
}
