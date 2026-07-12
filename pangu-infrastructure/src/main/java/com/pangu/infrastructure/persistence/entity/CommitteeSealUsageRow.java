// 关联业务：映射业委会用印文件、操作人、服务商流水和验签结果。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommitteeSealUsageRow {
    private Long usageId;
    private Long tenantId;
    private Long electronicSealId;
    private String sealName;
    private String businessType;
    private Long businessId;
    private String businessTitle;
    private String sealingMethod;
    private Long sourceAttachmentId;
    private Long sealedAttachmentId;
    private String sourceFileHash;
    private String sealedFileHash;
    private String providerTransactionId;
    private String certificateSerial;
    private String verificationStatus;
    private Integer simulated;
    private Long operatorUserId;
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;
}
