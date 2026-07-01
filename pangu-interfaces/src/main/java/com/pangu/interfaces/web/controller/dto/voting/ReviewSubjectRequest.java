package com.pangu.interfaces.web.controller.dto.voting;

/**
 * ELECTION 议题双签审批请求。
 *
 * @param decision 审批决定
 * @param reason   驳回原因；decision=REJECT 时必填
 */
public record ReviewSubjectRequest(
        Decision decision,
        String reason
) {
    public enum Decision {
        APPROVE,
        REJECT
    }
}
