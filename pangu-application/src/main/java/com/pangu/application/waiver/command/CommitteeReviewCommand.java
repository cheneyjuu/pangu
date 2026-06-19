package com.pangu.application.waiver.command;

/**
 * 居委会初审审批命令（{@code POST /waivers/{id}/committee-review}）。
 *
 * <p>权限校验由 controller 层 {@code @PreAuthorize("hasAuthority('waiver:approve:committee')")}
 * 完成，本 record 不再携带 dept_type 字段。
 *
 * @param waiverId       待审 waiver ID
 * @param approverUserId 审批人 sys_user.user_id
 * @param approve        true=通过，false=驳回
 * @param opinion        审批意见
 */
public record CommitteeReviewCommand(
        Long waiverId,
        Long approverUserId,
        boolean approve,
        String opinion
) {
}
