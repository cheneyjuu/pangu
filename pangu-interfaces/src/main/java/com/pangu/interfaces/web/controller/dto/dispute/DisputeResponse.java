package com.pangu.interfaces.web.controller.dto.dispute;

import com.pangu.domain.model.dispute.Dispute;

import java.time.Instant;

/** 异议查询响应 DTO。 */
public record DisputeResponse(
        Long disputeId,
        Long tenantId,
        Long raisedByOwnerId,
        String disputeKind,
        String relatedEntityType,
        Long relatedEntityId,
        int currentReviewLevel,
        String status,
        String businessPayloadJson,
        Instant raisedAt,
        Instant escalatedAt,
        Instant closedAt,
        String litigationOutcome,
        String litigationJudgementUrl,
        long version
) {
    public static DisputeResponse from(Dispute d) {
        return new DisputeResponse(
                d.getDisputeId(), d.getTenantId(), d.getRaisedByOwnerId(),
                d.getDisputeKind().name(), d.getRelatedEntityType(), d.getRelatedEntityId(),
                d.getCurrentReviewLevel(), d.getStatus().name(),
                d.getBusinessPayloadJson(),
                d.getRaisedAt(), d.getEscalatedAt(), d.getClosedAt(),
                d.getLitigationOutcome(), d.getLitigationJudgementUrl(),
                d.getVersion());
    }
}
