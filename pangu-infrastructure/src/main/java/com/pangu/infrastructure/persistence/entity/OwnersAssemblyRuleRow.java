// 关联业务：映射业主大会议事规则版本、私有原件元数据及其确认生命周期。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OwnersAssemblyRuleRow {
    private Long ruleId;
    private Long tenantId;
    private String ruleName;
    private String ruleVersion;
    private LocalDate effectiveDate;
    private String changeReason;
    private String configurationJson;
    private String configurationSha256;
    private String objectKey;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String etag;
    private String sha256;
    private String status;
    private Long draftedByAccountId;
    private Long draftedByUserId;
    private Long submittedByAccountId;
    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private Long activatedByAccountId;
    private Long activatedByUserId;
    private LocalDateTime activatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
