package com.pangu.application.voting.command;

/**
 * 候选人提名命令（M3-3，管理端提名）。
 *
 * @param subjectId     选举议题 ID
 * @param uid           候选人关联业主 uid
 * @param name          候选人姓名
 * @param partyMember   是否党员
 * @param tenantId      租户 ID（service 校验与议题一致）
 * @param operatorUserId 操作人 sys_user.user_id（审计）
 */
public record NominateCandidateCommand(
        Long subjectId,
        Long uid,
        String name,
        boolean partyMember,
        Long tenantId,
        Long operatorUserId
) {
}
