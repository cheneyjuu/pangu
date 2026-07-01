package com.pangu.domain.model.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 业主检索摘要领域模型。
 *
 * <p>用于「按手机号 / 姓名 / 拼音关联业主」场景（如换届选举提名候选人时定位业主）：
 * 承载辨识业主所需的最小字段——自然人 {@code uid}、登录手机号、代表房产的楼栋/房号、
 * 以及业主真实姓名 {@code realName}（仅候选人提名链路在 application 层解密填充；
 * 其他链路保持 {@code null}，避免 SM4 密文外泄）。
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

    /**
     * 业主真实姓名（明文）。
     *
     * <p>仅在候选人提名检索链路由 application 层使用 {@code NameDecryptor} 解密
     * {@code t_account.real_name} 密文后填入；其他检索（如按手机前缀的 fast-path）一律为 {@code null}。
     *
     * <p>下游 DTO 在序列化时应做空值处理（{@code null} → 留空即可）。
     */
    private final String realName;
}
