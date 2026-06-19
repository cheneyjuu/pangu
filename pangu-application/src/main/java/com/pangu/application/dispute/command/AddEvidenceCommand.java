package com.pangu.application.dispute.command;

import com.pangu.domain.model.dispute.EvidenceKind;

/** 业主补充证据。dispute 处于终态 (CLOSED_FINAL/WITHDRAWN) 时拒绝。 */
public record AddEvidenceCommand(
        Long disputeId,
        Long requestByOwnerId,
        EvidenceKind evidenceKind,
        String contentUrl,
        String description
) {
}
