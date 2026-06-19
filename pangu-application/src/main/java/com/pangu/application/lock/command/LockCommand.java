package com.pangu.application.lock.command;

import com.pangu.domain.model.lock.LockEntityType;

/**
 * 创建治理锁的命令对象。
 *
 * <p>调用方（{@code GovernanceLockController} 或 disclosure publish 内部委派）负责：
 * <ul>
 *   <li>从 {@code SecurityUtils} 解析 lockedByUserId / tenantId；</li>
 *   <li>对 {@code lockPayloadHash} 完成 SHA256 计算（M1 已有 {@code PayloadHasher} 工具）；</li>
 *   <li>校验调用者权限（{@code @PreAuthorize}）。</li>
 * </ul>
 *
 * @param tenantId         租户 ID
 * @param entityType       锁定的业务实体类型
 * @param entityId         业务实体主键
 * @param lockedByUserId   锁定操作人 sys_user.user_id
 * @param lockPayloadHash  锁定瞬间的整体快照 SHA256（64-hex）
 */
public record LockCommand(
        Long tenantId,
        LockEntityType entityType,
        Long entityId,
        Long lockedByUserId,
        String lockPayloadHash
) {
}
