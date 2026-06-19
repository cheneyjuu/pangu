package com.pangu.interfaces.web.controller.dto.lock;

import com.pangu.domain.model.lock.LockEntityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 创建治理锁的请求体。
 *
 * <p>{@code lockedByUserId} / {@code tenantId} 由 {@code SecurityUtils} 注入，请求体不携带，
 * 防止客户端伪造发起人身份。{@code lockPayloadHash} 由调用方按业务约定计算
 * （SHA256，64-hex），M2-3 财务公示落地后由 disclosure publish 内部委派时自动填充。
 *
 * @param entityType      锁定的业务实体类型（FINANCE_DISCLOSURE / ELECTION_DISCLOSURE / FUND_LEDGER_PUBLISH）
 * @param entityId        业务实体主键
 * @param lockPayloadHash 锁定瞬间整体快照的 SHA256（64-hex 字符串）
 */
public record LockRequest(
        @NotNull(message = "entityType must not be null")
        LockEntityType entityType,

        @NotNull(message = "entityId must not be null")
        @Positive(message = "entityId must be positive")
        Long entityId,

        @NotNull(message = "lockPayloadHash must not be null")
        @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "lockPayloadHash must be 64-hex SHA256")
        String lockPayloadHash
) {
}
