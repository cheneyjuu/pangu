// 关联业务：向有权限的办理人员返回书面委托原件短时预览地址。
package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.application.voting.VotingProxyAuthorizationService;

import java.time.Instant;

public record VotingProxyAuthorizationPreviewTicketResponse(String previewUrl, Instant expiresAt) {
    public static VotingProxyAuthorizationPreviewTicketResponse from(
            VotingProxyAuthorizationService.PreviewTicket ticket) {
        return new VotingProxyAuthorizationPreviewTicketResponse(ticket.url(), ticket.expiresAt());
    }
}
