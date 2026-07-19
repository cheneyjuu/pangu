// 关联业务：向管理端输出小区维修事项表决依据版本，不暴露私有对象存储键。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairDecisionRule;

import java.time.LocalDateTime;

public record RepairDecisionRuleResponse(
        Long ruleId,
        String ruleName,
        String ruleVersion,
        LocalDateTime effectiveAt,
        String deliveryRule,
        String nonResponseRule,
        String originalFileName,
        Long fileSize,
        String sha256,
        String status,
        Long registeredByUserId,
        LocalDateTime createTime
) {
    public static RepairDecisionRuleResponse from(RepairDecisionRule rule) {
        return new RepairDecisionRuleResponse(
                rule.ruleId(), rule.ruleName(), rule.ruleVersion(), rule.effectiveAt(),
                rule.deliveryRule(), rule.nonResponseRule().name(), rule.originalFileName(),
                rule.fileSize(), rule.sha256(), rule.status().name(),
                rule.registeredByUserId(), rule.createTime());
    }
}
