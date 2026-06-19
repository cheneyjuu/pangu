package com.pangu.interfaces.web.controller.dto.disclosure;

import com.pangu.domain.model.disclosure.DisclosureType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 财务公示 compose 请求体。
 *
 * <p>{@code tenantId} / {@code composedByUserId} 由 {@code SecurityUtils} 注入，
 * 请求体仅携带业务参数（period + type），与 LockRequest 一致。
 *
 * @param period         公示期间，月度 {@code 'YYYY-MM'} 或季度 {@code 'YYYYQn'}（n=1..4）
 * @param disclosureType 公示类型（本期仅 MAINTENANCE_FUND 真正可用）
 */
public record ComposeRequest(
        @NotNull(message = "period must not be null")
        @Pattern(regexp = "^[0-9]{4}(-(0[1-9]|1[0-2])|Q[1-4])$",
                 message = "period 必须为 'YYYY-MM' 或 'YYYYQn' 格式")
        String period,

        @NotNull(message = "disclosureType must not be null")
        DisclosureType disclosureType
) {
}
