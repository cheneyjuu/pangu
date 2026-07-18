// 关联业务：映射业主大会正式办理时冻结的议事规则版本及其结构化配置。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OwnersAssemblyRuleSnapshotRow {
    private Long ruleSnapshotId;
    private Long sessionId;
    private Long tenantId;
    private Long ruleId;
    private String ruleName;
    private String ruleVersion;
    private LocalDate effectiveDate;
    private String sourceFileName;
    private String sourceSha256;
    private String configurationJson;
    private String configurationSha256;
    private Long snapshottedByAccountId;
    private Long snapshottedByUserId;
    private LocalDateTime createTime;
}
