// 关联业务：留存议事规则草稿、提交确认、启用和替代过程的不可抵赖审计事件。
package com.pangu.domain.model.assembly;

import java.time.LocalDateTime;

/** 业主大会议事规则版本的关键操作审计记录。 */
public record OwnersAssemblyRuleAudit(
        Long auditId,
        Long ruleId,
        Long tenantId,
        EventType eventType,
        String configurationSha256,
        String changeReason,
        Long actorAccountId,
        Long actorUserId,
        String actorRoleKey,
        String actorCommitteePosition,
        LocalDateTime createTime
) {

    public enum EventType {
        DRAFT_CREATED,
        DRAFT_UPDATED,
        SUBMITTED_FOR_CONFIRMATION,
        ACTIVATED,
        SUPERSEDED
    }
}
