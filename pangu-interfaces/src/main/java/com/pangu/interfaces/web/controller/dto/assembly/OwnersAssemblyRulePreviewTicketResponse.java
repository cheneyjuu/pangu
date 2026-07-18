// 关联业务：向管理端输出业主大会议事规则原件的短时私有预览信息。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.application.assembly.OwnersAssemblyRulePreviewTicket;

import java.time.Instant;

/** 规则原件短时预览凭证。 */
public record OwnersAssemblyRulePreviewTicketResponse(
        Long ruleId,
        String originalFileName,
        String contentType,
        Long fileSize,
        String previewUrl,
        Instant expiresAt
) {

    public static OwnersAssemblyRulePreviewTicketResponse from(OwnersAssemblyRulePreviewTicket ticket) {
        return new OwnersAssemblyRulePreviewTicketResponse(
                ticket.ruleId(),
                ticket.originalFileName(),
                ticket.contentType(),
                ticket.fileSize(),
                ticket.previewUrl(),
                ticket.expiresAt());
    }
}
