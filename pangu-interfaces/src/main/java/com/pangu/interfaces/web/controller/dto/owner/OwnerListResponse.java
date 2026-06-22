package com.pangu.interfaces.web.controller.dto.owner;

import com.pangu.application.owner.OwnerProfileView;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业主名册列表项（M4 读侧）。
 *
 * <p>手机号脱敏（{@code 138****0012}）以避免 PII 直出；姓名由 service 层容错解密后照出。
 * 字段口径与详情页 {@link OwnerDetailResponse#profile} 保持一致。
 */
public record OwnerListResponse(
        Long uid,
        String realName,
        String phoneMasked,
        Integer realNameVerified,
        Integer authLevel,
        Integer accountStatus,
        Integer propertyCount,
        BigDecimal totalBuildArea,
        LocalDateTime createTime
) {
    public static OwnerListResponse from(OwnerProfileView v) {
        return new OwnerListResponse(
                v.uid(),
                v.realName(),
                PhoneMasker.mask(v.phone()),
                v.realNameVerified(),
                v.authLevel(),
                v.accountStatus(),
                v.propertyCount(),
                v.totalBuildArea(),
                v.createTime());
    }
}
