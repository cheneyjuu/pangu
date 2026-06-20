package com.pangu.interfaces.web.controller.dto.voting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理端候选人提名请求体（M3-3）。
 *
 * <p>由居委会/业委会筹备组在管理端录入候选人名单：关联业主 {@code uid} + 姓名 + 党员标记。
 * 提名后候选人状态置 {@code PENDING_REVIEW}，待 G 端资格审查。
 */
public record NominateCandidateRequest(
        @NotNull Long uid,
        @NotBlank @Size(max = 64) String name,
        boolean partyMember
) {
}
