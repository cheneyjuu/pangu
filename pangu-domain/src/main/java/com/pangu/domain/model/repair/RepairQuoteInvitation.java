package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

public record RepairQuoteInvitation(
        Long quoteInvitationId,
        Long workOrderId,
        Long supplierDeptId,
        String supplierName,
        String status,
        int invitationRound,
        String invitationType,
        String revisionReason,
        LocalDateTime deadline,
        LocalDateTime sentAt
) {
}
