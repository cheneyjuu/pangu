package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

/** 三方施工合同的一方签署记录。 */
public record RepairContractSignature(
        String partyType,
        String signerName,
        Long signerUserId,
        String signatureMethod,
        String signatureFileHash,
        LocalDateTime signedAt
) {
}
