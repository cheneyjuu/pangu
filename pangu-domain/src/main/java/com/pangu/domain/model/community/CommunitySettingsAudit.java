// 关联业务：承载社区设置变更记录及其审计载荷、操作账号和工作身份信息。
package com.pangu.domain.model.community;

import java.time.Instant;

/**
 * 社区设置变更审计日志行。
 */
public record CommunitySettingsAudit(
        Long auditId,
        Long tenantId,
        String operationType,
        String payloadJson,
        Long operatorAccountId,
        Long operatorUserId,
        String operatorName,
        String operatorRoleName,
        Instant createTime
) {
}
