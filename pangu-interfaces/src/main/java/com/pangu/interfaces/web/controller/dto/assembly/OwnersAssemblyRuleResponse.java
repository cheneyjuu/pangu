// 关联业务：向管理端输出业主大会议事规则版本，不暴露私有对象存储键和办理人内部标识。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyRule;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 管理端可查看的规则版本与结构化配置。 */
public record OwnersAssemblyRuleResponse(
        Long ruleId,
        String ruleName,
        String ruleVersion,
        LocalDate effectiveDate,
        String changeReason,
        OwnersAssemblyRuleConfiguration configuration,
        String configurationSha256,
        String originalFileName,
        String contentType,
        Long fileSize,
        String sha256,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime activatedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public static OwnersAssemblyRuleResponse from(OwnersAssemblyRule rule) {
        return new OwnersAssemblyRuleResponse(
                rule.ruleId(),
                rule.ruleName(),
                rule.ruleVersion(),
                rule.effectiveDate(),
                rule.changeReason(),
                rule.configuration(),
                rule.configurationSha256(),
                rule.originalFileName(),
                rule.contentType(),
                rule.fileSize(),
                rule.sha256(),
                rule.status().name(),
                rule.submittedAt(),
                rule.activatedAt(),
                rule.createTime(),
                rule.updateTime());
    }
}
