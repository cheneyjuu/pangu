package com.pangu.domain.model.attestation;

import java.time.Instant;

/**
 * 司法链存证回执。
 *
 * <p>{@code txHash} 在 stub 阶段为 {@code STUB-{eventId}}；真实存证完成后由
 * 异步消费器通过 {@code chain_attest_status = CONFIRMED} 反填真值。
 *
 * @param txHash       链上交易 hash（stub 阶段为 STUB-{eventId}）
 * @param chainProvider 链路提供方标识（stub 阶段为 OUTBOX_PENDING）
 * @param status       存证状态（PENDING/SUBMITTED/CONFIRMED/FAILED）
 * @param outboxEventId 关联 t_outbox_event.event_id（用于异步追踪）
 * @param submittedAt  提交时间戳
 */
public record AttestationReceipt(
        String txHash,
        String chainProvider,
        AttestationStatus status,
        Long outboxEventId,
        Instant submittedAt
) {
    public AttestationReceipt {
        if (txHash == null || txHash.isBlank()) {
            throw new IllegalArgumentException("txHash must not be blank");
        }
        if (chainProvider == null || chainProvider.isBlank()) {
            throw new IllegalArgumentException("chainProvider must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (outboxEventId == null) {
            throw new IllegalArgumentException("outboxEventId must not be null");
        }
        if (submittedAt == null) {
            throw new IllegalArgumentException("submittedAt must not be null");
        }
    }

    public enum AttestationStatus {
        PENDING(1),
        SUBMITTED(2),
        CONFIRMED(3),
        FAILED(4);

        private final int dbValue;

        AttestationStatus(int dbValue) {
            this.dbValue = dbValue;
        }

        public int getDbValue() {
            return dbValue;
        }
    }
}
