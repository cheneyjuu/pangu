// 关联业务：留存主任或副主任对业主大会议事规则每个结构化字段的逐项核对结论。
package com.pangu.domain.model.assembly;

import java.time.LocalDateTime;

/**
 * 业主大会议事规则字段确认记录。
 *
 * <p>一条记录对应当前规则配置中的一个字段及其原件页码、条款依据。配置在待确认阶段不可修改，
 * 因此确认记录绑定配置摘要，防止后续配置变化借用旧确认结论。
 */
public record OwnersAssemblyRuleFieldConfirmation(
        Long confirmationId,
        Long ruleId,
        Long tenantId,
        String configurationSha256,
        OwnersAssemblyRuleConfiguration.RuleConfigurationField field,
        Integer sourcePageNumber,
        String sourceClause,
        Status status,
        Long confirmedByAccountId,
        Long confirmedByUserId,
        String confirmedByCommitteePosition,
        LocalDateTime confirmedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public enum Status {
        PENDING,
        CONFIRMED
    }
}
