package com.pangu.interfaces.web.controller.dto.lock;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 治理锁解锁双签请求体。同一 DTO 用于 committee-sign / street-sign 两个端点。
 *
 * @param signature 审批人签名串（业务自定义内容；最多 128 字符，对齐 schema VARCHAR(128)）
 */
public record UnlockRequest(
        @NotNull(message = "signature must not be null")
        @Size(max = 128, message = "signature length must be <= 128")
        String signature
) {
}
