// 关联业务：向管理端展示电子或实物用印的文件版本、经办人和验签审计结果。
package com.pangu.interfaces.web.controller.dto.committee;

import com.pangu.domain.model.committee.CommitteeSealUsageRecord;

import java.time.LocalDateTime;

public record CommitteeSealUsageResponse(
        Long usageId,
        Long electronicSealId,
        String sealName,
        String businessType,
        Long businessId,
        String businessTitle,
        String sealingMethod,
        Long sourceAttachmentId,
        Long sealedAttachmentId,
        String sourceFileHash,
        String sealedFileHash,
        String providerTransactionId,
        String certificateSerial,
        String verificationStatus,
        boolean simulated,
        Long operatorUserId,
        String operatorName,
        String remark,
        LocalDateTime createTime
) {
    public static CommitteeSealUsageResponse from(CommitteeSealUsageRecord usage) {
        return new CommitteeSealUsageResponse(
                usage.usageId(), usage.electronicSealId(), usage.sealName(), usage.businessType(),
                usage.businessId(), usage.businessTitle(), usage.sealingMethod(), usage.sourceAttachmentId(),
                usage.sealedAttachmentId(), usage.sourceFileHash(), usage.sealedFileHash(),
                usage.providerTransactionId(), usage.certificateSerial(), usage.verificationStatus(),
                usage.simulated(), usage.operatorUserId(), usage.operatorName(), usage.remark(), usage.createTime());
    }
}
