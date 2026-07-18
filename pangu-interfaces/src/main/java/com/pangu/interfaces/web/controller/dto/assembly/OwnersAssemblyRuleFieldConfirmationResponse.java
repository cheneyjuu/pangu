// 关联业务：向有权查看的管理端展示议事规则逐项核对进度，不暴露核对人的内部账号标识。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyRuleFieldConfirmation;

import java.time.LocalDateTime;

/** 业主大会议事规则字段核对的最小必要视图。 */
public record OwnersAssemblyRuleFieldConfirmationResponse(
        String field,
        Integer sourcePageNumber,
        String sourceClause,
        String status,
        String confirmedByCommitteePosition,
        LocalDateTime confirmedAt
) {

    public static OwnersAssemblyRuleFieldConfirmationResponse from(
            OwnersAssemblyRuleFieldConfirmation confirmation) {
        return new OwnersAssemblyRuleFieldConfirmationResponse(
                confirmation.field().name(),
                confirmation.sourcePageNumber(),
                confirmation.sourceClause(),
                confirmation.status().name(),
                confirmation.confirmedByCommitteePosition(),
                confirmation.confirmedAt());
    }
}
