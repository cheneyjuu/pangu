// 关联业务：记录每次业委会用印的业务对象、文件版本、签章方式和验证结果。
package com.pangu.domain.model.committee;

import java.time.LocalDateTime;

public record CommitteeSealUsageRecord(
        Long usageId,
        Long tenantId,
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
}
