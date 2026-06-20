package com.pangu.interfaces.web.controller.dto.voting;

import jakarta.validation.constraints.NotNull;

/**
 * G 端候选人资格审查请求体（M3-3）。
 *
 * <p>{@code approve=true} → 资格通过(APPROVED)；{@code approve=false} → 驳回(REJECTED)。
 */
public record ReviewCandidateRequest(
        @NotNull Boolean approve
) {
}
