package com.pangu.application.waiver.command;

/**
 * 居委会初审审批命令（{@code POST /waivers/{id}/committee-approve|reject}）。
 *
 * @param waiverId       待审 waiver ID
 * @param approverUserId 审批人 sys_user.user_id
 * @param approverDeptType 审批人部门类型（必须 = 2 居委会）
 * @param approve        true=通过，false=驳回
 * @param opinion        审批意见
 */
public record CommitteeReviewCommand(
        Long waiverId,
        Long approverUserId,
        Integer approverDeptType,
        boolean approve,
        String opinion
) {
}
