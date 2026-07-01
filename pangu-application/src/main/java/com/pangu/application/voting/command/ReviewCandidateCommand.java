package com.pangu.application.voting.command;

/**
 * 候选人资格审查命令（M3-3，G 端审查）。
 *
 * @param candidateId    候选人 ID
 * @param approve        true=通过(APPROVED) / false=驳回(REJECTED)
 * @param operatorUserId 审查人 sys_user.user_id（审计）
 */
public record ReviewCandidateCommand(
        Long candidateId,
        boolean approve,
        Long operatorUserId,
        String rejectReasonCode,
        String rejectEvidenceJson
) {
    public ReviewCandidateCommand(Long candidateId, boolean approve, Long operatorUserId) {
        this(candidateId, approve, operatorUserId, null, null);
    }
}
