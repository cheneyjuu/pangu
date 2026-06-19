package com.pangu.application.lock.command;

/**
 * 业委会主任解锁初签命令。
 *
 * @param lockId           治理锁主键
 * @param approverUserId   业委会主任审批人 sys_user.user_id
 * @param signature        初签摘要 / 签名串（≤ 128 字符）
 */
public record CommitteeUnlockCommand(
        Long lockId,
        Long approverUserId,
        String signature
) {
}
