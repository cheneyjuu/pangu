package com.pangu.interfaces.web.controller.dto.voting;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * G 端候选人资格审查请求体（M3-3）。
 *
 * <p>{@code approve=true} → 资格通过(APPROVED)；{@code approve=false} → 驳回(REJECTED)。
 */
public record ReviewCandidateRequest(
        @NotNull Boolean approve,
        @Size(max = 2, message = "rejectReasonCode must be C1-C5")
        String rejectReasonCode,
        JsonNode rejectEvidence
) {
    public String rejectEvidenceJson() {
        return rejectEvidence == null ? null : rejectEvidence.toString();
    }
}
