// 关联业务：记录物业管理模式申请、材料、审核与执行全过程的不可变审计流水。
package com.pangu.domain.model.community;

import java.time.Instant;

/**
 * 物业管理模式变更申请审计记录。
 */
public record PropertyManagementModeChangeAudit(
        Long auditId,
        Long requestId,
        Long actorAccountId,
        Long actorUserId,
        Long actorDeptId,
        String eventType,
        PropertyManagementModeChangeStatus fromStatus,
        PropertyManagementModeChangeStatus toStatus,
        String payloadJson,
        Instant createdAt
) {
}
