// 关联业务：承载对未提交业主大会议事规则草稿的结构化配置和备案元数据更新。
package com.pangu.application.assembly.command;

import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;

import java.time.LocalDate;

public record UpdateOwnersAssemblyRuleDraftCommand(
        String ruleName,
        String ruleVersion,
        LocalDate effectiveDate,
        String changeReason,
        OwnersAssemblyRuleConfiguration configuration
) {
}
