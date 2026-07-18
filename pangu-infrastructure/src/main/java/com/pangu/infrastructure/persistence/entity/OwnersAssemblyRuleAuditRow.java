// 关联业务：映射业主大会议事规则草稿、确认、启用和替代事件的审计留痕。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnersAssemblyRuleAuditRow {
    private Long auditId;
    private Long ruleId;
    private Long tenantId;
    private String eventType;
    private String configurationSha256;
    private String changeReason;
    private Long actorAccountId;
    private Long actorUserId;
    private String actorRoleKey;
    private String actorCommitteePosition;
    private LocalDateTime createTime;
}
