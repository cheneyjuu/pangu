package com.pangu.application.disclosure.command;

/**
 * 公示快照差分审计命令。
 *
 * @param tenantId            租户 ID
 * @param prevSnapshotId      上一期 snapshot 主键
 * @param currSnapshotId      当期 snapshot 主键
 * @param auditedByUserId     审计人 sys_user.user_id
 */
public record CompareCommand(
        Long tenantId,
        Long prevSnapshotId,
        Long currSnapshotId,
        Long auditedByUserId
) {
}
