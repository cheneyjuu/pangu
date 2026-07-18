// 关联业务：承载上传业主大会议事规则原件并创建待录入配置草稿的应用层输入。
package com.pangu.application.assembly.command;

import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;

import java.time.LocalDate;

public record CreateOwnersAssemblyRuleDraftCommand(
        String ruleName,
        String ruleVersion,
        LocalDate effectiveDate,
        String changeReason,
        OwnersAssemblyRuleConfiguration configuration,
        String originalFileName,
        String contentType,
        byte[] content
) {
}
