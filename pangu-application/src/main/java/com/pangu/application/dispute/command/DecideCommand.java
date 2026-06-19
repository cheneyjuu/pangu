package com.pangu.application.dispute.command;

import com.pangu.domain.model.dispute.DecisionKind;

/**
 * 行政机关出具层级决议。
 *
 * @param disputeId       异议主键
 * @param decisionKind    UPHELD / REJECTED / PARTIAL_UPHELD
 * @param decidedByUserId 决议作出人（sys_user.user_id）
 * @param content         决议正文（必填，≤ 2000 字符）
 * @param docUrl          决议文书 URL（可选）
 */
public record DecideCommand(
        Long disputeId,
        DecisionKind decisionKind,
        Long decidedByUserId,
        String content,
        String docUrl
) {
}
