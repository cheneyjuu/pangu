package com.pangu.application.voting;

import com.pangu.domain.model.voting.ElectionRejectReasonCode;

/**
 * 校验 E3 拒绝理由码与 JSONB 证据链。
 */
public final class RejectEvidencePolicy {

    private RejectEvidencePolicy() {
    }

    public static void requireForReject(boolean approve, String reasonCode, String evidenceJson) {
        if (approve) {
            return;
        }
        try {
            ElectionRejectReasonCode.parse(reasonCode);
        } catch (IllegalArgumentException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.REJECT_REASON_CODE_REQUIRED,
                    e.getMessage(), e);
        }
        if (evidenceJson == null || evidenceJson.isBlank()) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.REJECT_EVIDENCE_REQUIRED,
                    "驳回必须提交 JSONB 证据链");
        }
        String evidence = evidenceJson.trim();
        if ("{}".equals(evidence) || !evidence.startsWith("{") || !evidence.endsWith("}")) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.REJECT_EVIDENCE_REQUIRED,
                    "驳回证据链必须是非空 JSON object");
        }
    }
}
