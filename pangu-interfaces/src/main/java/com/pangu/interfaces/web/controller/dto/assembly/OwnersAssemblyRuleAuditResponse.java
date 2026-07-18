// 关联业务：向具备规则查看权限的管理端输出规则确认轨迹，不暴露办理人 userId 或 accountId。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyRuleAudit;

import java.time.LocalDateTime;

/** 规则草稿、确认和替代的最小必要审计视图。 */
public record OwnersAssemblyRuleAuditResponse(
        Long auditId,
        String eventType,
        String configurationSha256,
        String changeReason,
        String actorRoleKey,
        String actorCommitteePosition,
        LocalDateTime createTime
) {

    public static OwnersAssemblyRuleAuditResponse from(OwnersAssemblyRuleAudit audit) {
        return new OwnersAssemblyRuleAuditResponse(
                audit.auditId(),
                audit.eventType().name(),
                audit.configurationSha256(),
                audit.changeReason(),
                audit.actorRoleKey(),
                audit.actorCommitteePosition(),
                audit.createTime());
    }
}
