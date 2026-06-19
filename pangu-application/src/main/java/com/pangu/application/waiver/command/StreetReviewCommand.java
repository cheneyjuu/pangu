package com.pangu.application.waiver.command;

/**
 * 街道办终审审批命令（{@code POST /waivers/{id}/street-review}）。
 *
 * <p>权限校验由 controller 层 {@code @PreAuthorize("hasAuthority('waiver:approve:street')")}
 * 完成，本 record 不再携带 dept_type 字段。
 *
 * <p>聚合根仍校验：{@code approverUserId} 不可与初审 {@code committee_approver} 相同。
 *
 * @param waiverId         待审 waiver ID
 * @param approverUserId   终审人 sys_user.user_id
 * @param approve          true=通过 → APPROVED + 锁定 payloadHash + 上链；false=驳回
 * @param opinion          审批意见
 */
public record StreetReviewCommand(
        Long waiverId,
        Long approverUserId,
        boolean approve,
        String opinion
) {
}
