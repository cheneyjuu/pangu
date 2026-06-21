package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

/**
 * 业主手机号检索行 → {@code OwnerSummary} 中转。
 *
 * <p>用于「按手机号关联业主」（换届选举提名候选人定位业主）。
 * phone 作普通明文列映射（不走 SM4 TypeHandler），不含 real_name（加密列，检索链路不解密）。
 */
@Data
public class OwnerLookupRow {

    /** 业主自然人 ID（c_user.uid）。 */
    private Long uid;

    /** 登录手机号（t_account.phone，明文唯一）。 */
    private String phone;

    /** 代表房产所属楼栋 ID（同一业主多套房时取其一）。 */
    private Long buildingId;

    /** 代表房产房间 ID（同一业主多套房时取其一）。 */
    private Long roomId;
}
