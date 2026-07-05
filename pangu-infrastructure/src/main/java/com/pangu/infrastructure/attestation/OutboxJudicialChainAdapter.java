package com.pangu.infrastructure.attestation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.attestation.AttestationPayload;
import com.pangu.domain.model.attestation.AttestationReceipt;
import com.pangu.domain.model.attestation.AttestationVerifyResult;
import com.pangu.domain.model.attestation.JudicialChainPort;
import com.pangu.infrastructure.persistence.entity.OutboxEventRow;
import com.pangu.infrastructure.persistence.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 司法链 Outbox 适配器：本期不真实出链，仅落 outbox 后即时返回 stub receipt。
 *
 * <p>设计原则：
 * <ul>
 *   <li>业务流程不阻塞、不依赖实际链路；</li>
 *   <li>落 outbox 与业务事务共享同一 {@code @Transactional}，保证 attest 调用与业务写入的原子一致性；</li>
 *   <li>真实存证由后续异步消费器（不在本期 commit）读 PENDING 行调用真实链路 SDK 后回填。</li>
 * </ul>
 *
 * <p>事件类型 {@code event_type} 限定为 V2.4 CHECK 约束的三类（VOTING_RESULT_ATTEST / WAIVER_APPROVED_ATTEST / FUND_LEDGER_ATTEST）；
 * 其它字符串提交将被显式拒绝，避免静默丢入 outbox 后异步消费器无法识别。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "platform.attestation.provider", havingValue = "outbox-pending", matchIfMissing = true)
public class OutboxJudicialChainAdapter implements JudicialChainPort {

    private static final int EVENT_TYPE_VOTING_RESULT_ATTEST = 1;
    private static final int EVENT_TYPE_WAIVER_APPROVED_ATTEST = 2;
    private static final int EVENT_TYPE_FUND_LEDGER_ATTEST = 3;

    private static final String STUB_CHAIN_PROVIDER = "OUTBOX_PENDING";

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AttestationReceipt attest(AttestationPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("AttestationPayload 不可为空");
        }
        OutboxEventRow row = new OutboxEventRow();
        row.setEventType(resolveEventType(payload.eventType()));
        row.setBusinessRefId(payload.businessRefId());
        row.setTenantId(payload.tenantId());
        row.setPayloadJson(serializePayload(payload));
        row.setStatus(AttestationReceipt.AttestationStatus.PENDING.getDbValue());
        outboxEventMapper.insert(row);

        log.info("司法链 outbox 落定 eventId={} type={} businessRefId={} tenantId={}",
                row.getEventId(), payload.eventType(), payload.businessRefId(), payload.tenantId());

        return new AttestationReceipt(
                "STUB-" + row.getEventId(),
                STUB_CHAIN_PROVIDER,
                AttestationReceipt.AttestationStatus.PENDING,
                row.getEventId(),
                Instant.now());
    }

    @Override
    public AttestationVerifyResult verify(String txHash) {
        // 本期不实现核验链路，预留接口
        return new AttestationVerifyResult(
                false,
                null,
                Instant.now(),
                "OUTBOX_PENDING adapter verify not implemented; awaiting real chain provider");
    }

    private int resolveEventType(String eventType) {
        return switch (eventType) {
            case "VOTING_RESULT_ATTEST" -> EVENT_TYPE_VOTING_RESULT_ATTEST;
            case "WAIVER_APPROVED_ATTEST" -> EVENT_TYPE_WAIVER_APPROVED_ATTEST;
            case "FUND_LEDGER_ATTEST" -> EVENT_TYPE_FUND_LEDGER_ATTEST;
            default -> throw new IllegalArgumentException("未知司法链事件类型: " + eventType);
        };
    }

    private String serializePayload(AttestationPayload payload) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("eventType", payload.eventType());
        json.put("businessRefId", payload.businessRefId());
        json.put("tenantId", payload.tenantId());
        json.put("localPayloadHash", payload.localPayloadHash());
        json.put("businessPayload", payload.businessPayload());
        json.put("attestedAt", payload.attestedAt().toString());
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("司法链 outbox payload 序列化失败", ex);
        }
    }
}
