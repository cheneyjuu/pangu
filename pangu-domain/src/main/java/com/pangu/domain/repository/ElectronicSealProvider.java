// 关联业务：隔离盘古电子签章流程与 e签宝等真实服务商或开发环境模拟器。
package com.pangu.domain.repository;

import com.pangu.domain.model.committee.CommitteeSealType;

import java.time.LocalDateTime;

public interface ElectronicSealProvider {

    String providerCode();

    boolean simulated();

    ProvisionedSeal provision(ProvisionRequest request);

    SignedDocument sign(SignRequest request);

    VerificationResult verify(VerificationRequest request);

    record ProvisionRequest(
            Long tenantId,
            String sealName,
            CommitteeSealType sealType,
            String committeeTermName
    ) {
    }

    record ProvisionedSeal(
            String providerSealId,
            String certificateSerial,
            LocalDateTime validFrom,
            LocalDateTime validUntil
    ) {
    }

    record SignRequest(
            String providerSealId,
            String certificateSerial,
            String sourceFileName,
            byte[] sourceContent,
            String sourceFileHash
    ) {
    }

    record SignedDocument(
            byte[] content,
            String contentType,
            String providerTransactionId,
            String certificateSerial,
            String verificationStatus
    ) {
    }

    record VerificationRequest(
            String fileName,
            byte[] content,
            String fileHash
    ) {
    }

    record VerificationResult(
            String providerTransactionId,
            String certificateSerial,
            String verificationStatus
    ) {
    }
}
