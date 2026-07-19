// 关联业务：表达小区已经备案并可供楼栋维修征询形成快照的不可变规则版本。
package com.pangu.domain.model.repair;

import com.pangu.domain.model.repair.RepairProjectGovernance.NonResponseRule;

import java.time.LocalDateTime;

/**
 * 小区维修事项表决依据版本。
 *
 * <p>新版本以新增记录并替代旧版本的方式生效，历史版本及原始文件不得覆盖。
 */
public record RepairDecisionRule(
        Long ruleId,
        Long tenantId,
        String ruleName,
        String ruleVersion,
        LocalDateTime effectiveAt,
        String deliveryRule,
        NonResponseRule nonResponseRule,
        String objectKey,
        String originalFileName,
        String contentType,
        Long fileSize,
        String etag,
        String sha256,
        Status status,
        Long registeredByAccountId,
        Long registeredByUserId,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public enum Status {
        ACTIVE,
        SUPERSEDED
    }
}
