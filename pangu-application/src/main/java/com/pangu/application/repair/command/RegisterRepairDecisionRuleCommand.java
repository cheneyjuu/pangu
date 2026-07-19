// 关联业务：登记一份小区已经生效、适用于维修事项的表决依据原件和结构化执行规则。
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
