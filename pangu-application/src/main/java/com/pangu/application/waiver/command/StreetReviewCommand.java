package com.pangu.application.waiver.command;

/**
 * 街道办终审审批命令（{@code POST /waivers/{id}/street-approve|reject}）。
 *
 * <p>校验：
 * <ul>
 *   <li>{@code approverDeptType = 1}（街道办）；</li>
 *   <li>{@code approverUserId} 不可与初审 committee_approver 相同（聚合根强校验）。</li>
 * </ul>
 *
 * @param waiverId         待审 waiver ID
 * @param approverUserId   终审人 sys_user.user_id
 * @param approverDeptType 终审人部门类型（必须 = 1 街道办）
 * @param approve          true=通过 → APPROVED + 锁定 payloadHash + 上链；false=驳回
 * @param opinion          审批意见
 */
public record StreetReviewCommand(
        Long waiverId,
        Long approverUserId,
        Integer approverDeptType,
        boolean approve,
        String opinion
) {
}
