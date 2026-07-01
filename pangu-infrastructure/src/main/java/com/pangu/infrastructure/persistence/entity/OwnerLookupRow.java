package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

/**
 * 业主提名检索行 → {@code OwnerSummary} 中转。
 *
 * <p>用于候选人提名定位业主，两条查询共用：
 * <ul>
 *   <li>{@code searchOwnersByPhoneFragment}：手机号模糊，{@code realName} 留 {@code null}；</li>
 *   <li>{@code listNominatableOwnersWithName}：拉本租户全部业主含 SM4 密文 {@code realName}，
 *       由 application 层 {@code NameDecryptor} 解密后做姓名/拼音匹配。</li>
 * </ul>
 *
 * <p>{@code realName} 不挂 SM4 TypeHandler，保留密文交给上层处理。
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

    /** 业主真实姓名 SM4 密文（手机号路径下为 {@code null}）。 */
    private String realName;
}

