package com.pangu.application.owner;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业主名册视图 DTO（application 层），姓名已解密。
 */
public record OwnerProfileView(
        Long uid,
        Long accountId,
        String phone,
        String realName,
        Integer realNameVerified,
        Integer authLevel,
        Integer accountStatus,
        Integer propertyCount,
        BigDecimal totalBuildArea,
        LocalDateTime createTime
) {
}
