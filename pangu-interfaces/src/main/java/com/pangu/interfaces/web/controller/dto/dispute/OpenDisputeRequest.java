package com.pangu.interfaces.web.controller.dto.dispute;

import com.pangu.domain.model.dispute.DisputeKind;
import jakarta.validation.constraints.NotNull;

/**
 * 业主提起异议请求体（C 端）。
 *
 * <p>{@code businessPayloadJson} 是已序列化好的 JSON 字符串（软关联 voucher_id / disputed_amount 等），
 * M3-1 阶段不做强 schema 校验，等业务侧 voucher / proposal 表落地后引入 validator。
 */
public record OpenDisputeRequest(
        @NotNull DisputeKind disputeKind,
        Long relatedPropertyOpid,
        String relatedEntityType,
        Long relatedEntityId,
        String businessPayloadJson
) {
}
