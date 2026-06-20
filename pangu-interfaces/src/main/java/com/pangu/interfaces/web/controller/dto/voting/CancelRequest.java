package com.pangu.interfaces.web.controller.dto.voting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * B/G 端撤回请求体（M3-2）。
 *
 * <p>{@code reason} 必填且 ≤ 500 字符；落 {@code t_voting_subject.cancel_reason}，被 trigger 12 兜底校验。
 */
public record CancelRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
