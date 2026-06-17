package com.pangu.interfaces.web.controller.dto.waiver;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 居委会发起党员比例放宽申请的请求体。
 *
 * <p>{@code subjectId} 来自 URL path（{@code POST /api/v1/elections/{subjectId}/waivers}）；
 * 申请人 / 部门 / 租户从 {@code SecurityUtils} 上下文解析，不在请求体中重复传递，
 * 防止客户端伪造发起人身份。
 *
 * @param requestedRatio     申请放宽至的党员比例 [0.00, 0.50)
 * @param reasonText         申请理由（实质字符 ≥ 50；水文检测必过）
 * @param reasonEvidenceKeys OSS 凭证 key 数组（可选，逗号分隔后落库）
 */
public record SubmitWaiverRequest(
        @NotNull(message = "requestedRatio must not be null")
        @DecimalMin(value = "0.00", inclusive = true, message = "requestedRatio must be >= 0.00")
        @DecimalMax(value = "0.49", inclusive = true, message = "requestedRatio must be < 0.50")
        BigDecimal requestedRatio,

        @NotBlank(message = "reasonText must not be blank")
        @Size(max = 4000, message = "reasonText length must be <= 4000")
        String reasonText,

        @Size(max = 1000, message = "reasonEvidenceKeys joined string must be <= 1000 chars")
        String reasonEvidenceKeys
) {
}
