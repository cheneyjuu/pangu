// 关联业务：映射维修工程责任、资金承担和执行依据的版本化确认事实。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RepairResponsibilityDeterminationRow {
    private Long determinationId;
    private Long projectId;
    private Long tenantId;
    private Integer versionNo;
    private String status;
    private String responsibilityPath;
    private String fundingSourceType;
    private String executionAuthorityType;
    private Long basisAttachmentId;
    private String basisReference;
    private String responsiblePartyName;
    private String responsiblePartyReference;
    private BigDecimal approvedAmount;
    private Long proposedByAccountId;
    private Long proposedByUserId;
    private LocalDateTime proposedAt;
    private Long confirmedByAccountId;
    private Long confirmedByUserId;
    private LocalDateTime confirmedAt;
    private String confirmationNote;
    private LocalDateTime createTime;
}
