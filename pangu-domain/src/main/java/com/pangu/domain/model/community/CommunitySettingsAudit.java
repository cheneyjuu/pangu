package com.pangu.domain.model.community;

import java.time.Instant;

/**
 * 社区设置变更审计日志行。
 */
public record CommunitySettingsAudit(
        Long auditId,
        Long tenantId,
        String operationType,
        Long operatorUserId,
        Instant createTime
) {
}
