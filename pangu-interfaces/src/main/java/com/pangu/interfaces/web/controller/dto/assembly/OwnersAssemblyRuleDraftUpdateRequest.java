// 关联业务：更新尚未提交主任或副主任确认的业主大会议事规则草稿。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.application.assembly.command.UpdateOwnersAssemblyRuleDraftCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 草稿元数据和结构化规则配置的更新请求。 */
public record OwnersAssemblyRuleDraftUpdateRequest(
        @NotBlank @Size(max = 200) String ruleName,
        @NotBlank @Size(max = 64) String ruleVersion,
        @NotNull LocalDate effectiveDate,
        @NotBlank @Size(max = 1000) String changeReason,
        @NotNull @Valid OwnersAssemblyRuleConfigurationRequest configuration
) {

    public UpdateOwnersAssemblyRuleDraftCommand toCommand() {
        return new UpdateOwnersAssemblyRuleDraftCommand(
                ruleName, ruleVersion, effectiveDate, changeReason, configuration.toDomain());
    }
}
