package com.pangu.application.voting.command;

import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 议题立项命令（M3-2）。
 *
 * @param tenantId         租户 ID（service 内会与 SecurityUtils.getTenantId 校验一致）
 * @param subjectType      议题类型；本期 ELECTION 一律拒绝
 * @param scope            分母范围
 * @param scopeReferenceId 范围引用 ID（COMMUNITY 时可空）
 * @param title            议题标题
 * @param voteStartAt      投票开放时间（scheduler 触发 PUBLISHED→VOTING 的依据）
 * @param voteEndAt        投票截止时间
 * @param proposedByUserId 发起人 sys_user.user_id（service 校验 = 当前登录 user）
 * @param partyRatioFloor  党员比例下限（可空；MAJOR 默认 0.50，由 application 写入）
 */
public record ProposeSubjectCommand(
        Long tenantId,
        SubjectType subjectType,
        VotingScope scope,
        Long scopeReferenceId,
        String title,
        Instant voteStartAt,
        Instant voteEndAt,
        Long proposedByUserId,
        BigDecimal partyRatioFloor
) {
}
