package com.pangu.application.disclosure.command;

/**
 * 公示快照锁定 + 发布命令（DRAFT → LOCKED → PUBLISHED 单事务）。
 *
 * @param tenantId    租户 ID（用于 governance_lock 编排，与 snapshot.tenantId 校验一致）
 * @param snapshotId  待发布的 snapshot 主键
 * @param userId      操作人 sys_user.user_id（业委会主任）
 */
public record LockAndPublishCommand(
        Long tenantId,
        Long snapshotId,
        Long userId
) {
}
