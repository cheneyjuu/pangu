package com.pangu.application.lock.command;

/**
 * 街道办解锁终签命令。
 *
 * @param lockId           治理锁主键
 * @param approverUserId   街道办审批人 sys_user.user_id
 * @param signature        终签摘要 / 签名串（≤ 128 字符）
 */
public record StreetUnlockCommand(
        Long lockId,
        Long approverUserId,
        String signature
) {
}
