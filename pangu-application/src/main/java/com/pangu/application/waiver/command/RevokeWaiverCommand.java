package com.pangu.application.waiver.command;

/**
 * 人工撤销 waiver 命令。允许 DRAFT / PENDING_* / APPROVED 阶段调用。
 *
 * <p>权限：
 * <ul>
 *   <li>申请发起人本人 (initiator_user_id) 可在审批前任一阶段撤销；</li>
 *   <li>街道办 / 居委会管理员可在自身审批阶段前撤销（控制层加 PreAuthorize 把关）。</li>
 * </ul>
 *
 * @param waiverId      待撤销 waiver ID
 * @param operatorUserId 操作人 sys_user.user_id（用于审计）
 */
public record RevokeWaiverCommand(
        Long waiverId,
        Long operatorUserId
) {
}
