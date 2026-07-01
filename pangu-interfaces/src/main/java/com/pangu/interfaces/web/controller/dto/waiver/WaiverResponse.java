package com.pangu.interfaces.web.controller.dto.waiver;

import com.pangu.domain.model.waiver.PartyRatioWaiver;
import com.pangu.domain.model.waiver.WaiverStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 党员比例放宽申请响应体（GET / 提交 / 审批 操作返回）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>仅暴露稳定的业务字段，不暴露 chain attest 重试细节（以免给攻击者反馈链上状态）；</li>
 *   <li>{@code partyPoolSize / totalEligibleSize} 即申请瞬间快照，便于前端展示「为何申请放宽」；</li>
 *   <li>{@code requestedRatio} / 当前 status 字段是核心，前端基于此做按钮可见性判断。</li>
 * </ul>
 */
public record WaiverResponse(
        Long waiverId,
        Long subjectId,
        Long tenantId,
        Long initiatorUserId,
        BigDecimal requestedRatio,
        long partyPoolSize,
        long totalEligibleSize,
        String reasonText,
        String reasonEvidenceKeys,
        WaiverStatus status,
        Long committeeApprover,
        Instant committeeApprovalAt,
        String committeeOpinion,
        String committeeRejectReasonCode,
        String committeeRejectEvidenceJson,
        Long streetApprover,
        Instant streetApprovalAt,
        String streetOpinion,
        String streetRejectReasonCode,
        String streetRejectEvidenceJson,
        Instant appliedAt,
        String localPayloadHash,
        Instant localPayloadLockedAt,
        long version
) {
    public static WaiverResponse from(PartyRatioWaiver waiver) {
        return new WaiverResponse(
                waiver.getWaiverId(),
                waiver.getSubjectId(),
                waiver.getTenantId(),
                waiver.getInitiatorUserId(),
                waiver.getRequestedRatio(),
                waiver.getPartyPoolSize(),
                waiver.getTotalEligibleSize(),
                waiver.getReasonText(),
                waiver.getReasonEvidenceKeys(),
                waiver.getStatus(),
                waiver.getCommitteeApprover(),
                waiver.getCommitteeApprovalAt(),
                waiver.getCommitteeOpinion(),
                waiver.getCommitteeRejectReasonCode(),
                waiver.getCommitteeRejectEvidenceJson(),
                waiver.getStreetApprover(),
                waiver.getStreetApprovalAt(),
                waiver.getStreetOpinion(),
                waiver.getStreetRejectReasonCode(),
                waiver.getStreetRejectEvidenceJson(),
                waiver.getAppliedAt(),
                waiver.getLocalPayloadHash(),
                waiver.getLocalPayloadLockedAt(),
                waiver.getVersion()
        );
    }
}
