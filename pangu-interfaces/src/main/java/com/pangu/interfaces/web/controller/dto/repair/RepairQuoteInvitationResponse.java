package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairQuoteInvitation;

import java.time.LocalDateTime;

public record RepairQuoteInvitationResponse(
        Long quoteInvitationId,
        Long supplierDeptId,
        String supplierName,
        String status,
        int invitationRound,
        String invitationType,
        String revisionReason,
        LocalDateTime deadline,
        LocalDateTime sentAt
) {
    public static RepairQuoteInvitationResponse from(RepairQuoteInvitation invitation) {
        return new RepairQuoteInvitationResponse(
                invitation.quoteInvitationId(), invitation.supplierDeptId(), invitation.supplierName(),
                invitation.status(), invitation.invitationRound(), invitation.invitationType(),
                invitation.revisionReason(), invitation.deadline(), invitation.sentAt());
    }
}
