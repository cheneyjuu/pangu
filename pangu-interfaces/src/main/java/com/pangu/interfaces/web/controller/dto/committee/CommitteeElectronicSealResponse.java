// 关联业务：向管理端展示电子印章类型、证书、保管人、届期、状态和模拟标识。
package com.pangu.interfaces.web.controller.dto.committee;

import com.pangu.domain.model.committee.CommitteeElectronicSeal;

import java.time.LocalDateTime;

public record CommitteeElectronicSealResponse(
        Long sealId,
        String sealName,
        String sealType,
        String providerCode,
        String providerSealId,
        String certificateSerial,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        String status,
        Long custodianUserId,
        String custodianName,
        String committeeTermName,
        boolean simulated,
        LocalDateTime createTime
) {
    public static CommitteeElectronicSealResponse from(CommitteeElectronicSeal seal) {
        return new CommitteeElectronicSealResponse(
                seal.sealId(), seal.sealName(), seal.sealType().name(), seal.providerCode(),
                seal.providerSealId(), seal.certificateSerial(), seal.validFrom(), seal.validUntil(),
                seal.status().name(), seal.custodianUserId(), seal.custodianName(),
                seal.committeeTermName(), seal.simulated(), seal.createTime());
    }
}
