// 关联业务：向管理端输出小区维修征询规则原件的短时私有预览地址。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairDecisionRulePreviewTicket;

import java.time.Instant;

public record RepairDecisionRulePreviewTicketResponse(
        Long ruleId,
        String previewUrl,
        Instant expiresAt
) {
    public static RepairDecisionRulePreviewTicketResponse from(RepairDecisionRulePreviewTicket ticket) {
        return new RepairDecisionRulePreviewTicketResponse(
                ticket.ruleId(), ticket.previewUrl(), ticket.expiresAt());
    }
}
