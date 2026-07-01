package com.pangu.interfaces.web.controller.dto.waiver;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 审批请求体（居委会初审 / 街道办终审通用）。
 *
 * <p>审批人身份从 {@code SecurityUtils} 解析；客户端只提交「通过/驳回」与意见。
 *
 * @param approve 是否通过：true=通过，false=驳回（流转 → REJECTED 终止态）
 * @param opinion 审批意见（≤ 500 字符）
 */
public record ReviewWaiverRequest(
        @NotNull(message = "approve must not be null")
        Boolean approve,

        @Size(max = 500, message = "opinion length must be <= 500")
        String opinion,

        @Size(max = 2, message = "rejectReasonCode must be C1-C5")
        String rejectReasonCode,

        JsonNode rejectEvidence
) {
    public String rejectEvidenceJson() {
        return rejectEvidence == null ? null : rejectEvidence.toString();
    }
}
