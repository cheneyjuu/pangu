package com.pangu.application.voting.command;

/**
 * 党组书记前置审查命令（候选人资格审查的第一道闸）。
 *
 * <p>{@code approve=true} → 前置审查通过(PENDING_PARTY_REVIEW → PENDING_COMMITTEE_REVIEW)，
 * 移交居委会资格审查；{@code approve=false} → 前置审查驳回(→ REJECTED)。
 *
 * @param candidateId    候选人 ID
 * @param approve        true=前置审查通过 / false=驳回
 * @param operatorUserId 党组书记 sys_user.user_id（审计）
 */
public record PartyReviewCandidateCommand(
        Long candidateId,
        boolean approve,
        Long operatorUserId
) {
}
