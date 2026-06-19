package com.pangu.domain.model.dispute;

import java.time.Instant;

/**
 * 异议证据值对象（一行 t_dispute_evidence 的语义快照）。
 */
public record DisputeEvidence(
        Long evidenceId,
        Long disputeId,
        EvidenceKind kind,
        String contentUrl,
        String description,
        Instant uploadedAt
) {
}
