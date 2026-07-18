// 关联业务：映射小区维修征询规则备案版本及其私有原件元数据。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairDecisionRuleRow {
    private Long ruleId;
    private Long tenantId;
    private String ruleName;
    private String ruleVersion;
    private LocalDateTime effectiveAt;
    private String deliveryRule;
    private String nonResponseRule;
    private String objectKey;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String etag;
    private String sha256;
    private String status;
    private Long registeredByAccountId;
    private Long registeredByUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
