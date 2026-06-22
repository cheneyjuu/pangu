package com.pangu.domain.model.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业主名册领域视图（M4 读侧）。
 *
 * <p>用于管理端「业主名册」列表与详情查询。包含跨表聚合字段：
 * {@code propertyCount}（本租户房产套数）与 {@code totalBuildArea}（决策权重视图）。
 *
 * <p>姓名采用「密文原值 + 服务层容错解密」策略：
 * 仓储返回 {@code realNameCipher} 原值（不挂 SM4 TypeHandler，避免开发期 MOCK_ 明文触发 500），
 * 由 application 层 {@link com.pangu.domain.security.NameDecryptor} 尝试解密，失败回退原值。
 */
public record OwnerProfile(
        Long uid,
        Long accountId,
        String phone,
        String realNameCipher,
        Integer realNameVerified,
        Integer authLevel,
        Integer accountStatus,
        Integer propertyCount,
        BigDecimal totalBuildArea,
        LocalDateTime createTime
) {
}
