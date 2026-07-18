// 关联业务：登记一份小区实际生效的楼栋维修征询规则原件和结构化执行规则。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProjectGovernance.NonResponseRule;

import java.time.LocalDate;

public record RegisterRepairDecisionRuleCommand(
        String ruleName,
        String ruleVersion,
        LocalDate effectiveDate,
        String deliveryRule,
        NonResponseRule nonResponseRule,
        String originalFileName,
        String contentType,
        byte[] content
) {
}
