// 关联业务：保存经业委会主任或副主任确认后可用于业主大会正式办理的议事规则版本。
package com.pangu.domain.model.assembly;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业主大会议事规则版本。
 *
 * <p>规则原件和结构化配置属于同一版本；ACTIVE 版本不可修改，变更必须创建新草稿并替代历史版本。
 */
public record OwnersAssemblyRule(
        Long ruleId,
        Long tenantId,
        String ruleName,
        String ruleVersion,
        LocalDate effectiveDate,
        String changeReason,
        OwnersAssemblyRuleConfiguration configuration,
        String configurationSha256,
        String objectKey,
        String originalFileName,
        String contentType,
        Long fileSize,
        String etag,
        String sha256,
        Status status,
        Long draftedByAccountId,
        Long draftedByUserId,
        Long submittedByAccountId,
        Long submittedByUserId,
        LocalDateTime submittedAt,
        Long activatedByAccountId,
        Long activatedByUserId,
        LocalDateTime activatedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public enum Status {
        DRAFT,
        PENDING_CONFIRMATION,
        ACTIVE,
        SUPERSEDED
    }
}
