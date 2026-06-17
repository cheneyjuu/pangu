package com.pangu.domain.model.attestation;

import java.time.Instant;
import java.util.Map;

/**
 * 司法链存证 payload。
 *
 * <p>本期不真实出链：infrastructure 层适配器
 * （{@code OutboxJudicialChainAdapter}）仅将该 payload 落入
 * {@code t_outbox_event}，并即时返回伪 {@code STUB-{eventId}} 作为占位
 * tx hash；真实存证由后续异步消费器接管。
 *
 * @param eventType    事件类型（VOTING_RESULT_ATTEST / WAIVER_APPROVED_ATTEST / FUND_LEDGER_ATTEST）
 * @param businessRefId 业务实体 ID（subject_id / waiver_id 等）
 * @param tenantId     租户 ID
 * @param localPayloadHash  本地不可变 payload 的 SHA256（64 hex），即时锁定
 * @param businessPayload 业务关键字段字典（含原始数据指纹，用于上链后核对）
 * @param attestedAt   payload 锁定时间戳
 */
public record AttestationPayload(
        String eventType,
        Long businessRefId,
        Long tenantId,
        String localPayloadHash,
        Map<String, Object> businessPayload,
        Instant attestedAt
) {
    public AttestationPayload {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (businessRefId == null) {
            throw new IllegalArgumentException("businessRefId must not be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (localPayloadHash == null || localPayloadHash.length() != 64) {
            throw new IllegalArgumentException("localPayloadHash must be 64-hex SHA256 digest");
        }
        if (businessPayload == null) {
            throw new IllegalArgumentException("businessPayload must not be null");
        }
        if (attestedAt == null) {
            throw new IllegalArgumentException("attestedAt must not be null");
        }
        // 防御性拷贝避免上层后续修改
        businessPayload = Map.copyOf(businessPayload);
    }
}
