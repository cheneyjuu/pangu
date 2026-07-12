// 关联业务：记录业主自治组织电子印章台账、保管人、届期和证书状态。
package com.pangu.domain.model.committee;

import java.time.LocalDateTime;

public record CommitteeElectronicSeal(
        Long sealId,
        Long tenantId,
        String sealName,
        CommitteeSealType sealType,
        String providerCode,
        String providerSealId,
        String certificateSerial,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        CommitteeSealStatus status,
        Long custodianUserId,
        String custodianName,
        String committeeTermName,
        boolean simulated,
        Long createdByUserId,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
