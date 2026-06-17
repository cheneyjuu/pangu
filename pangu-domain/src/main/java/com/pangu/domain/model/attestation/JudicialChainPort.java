package com.pangu.domain.model.attestation;

/**
 * 司法链存证端口（Hexagonal Port）。
 *
 * <p>本期 infrastructure 适配器（{@code OutboxJudicialChainAdapter}）的实现策略：
 * <ul>
 *   <li>{@link #attest(AttestationPayload)}：仅落 outbox + 即时返回 stub receipt，
 *       业务流程不阻塞、不依赖实际链路；</li>
 *   <li>{@link #verify(String)}：本期返回 {@code verified=false} + 「未实现」标记，
 *       等待后续接入司法链厂商 SDK；</li>
 *   <li>异步消费器（与本期不同 commit）会读 outbox PENDING 行，调用真实链路 SDK
 *       并反填 {@code blockchain_tx_hash} + {@code chain_attest_status=CONFIRMED}。</li>
 * </ul>
 */
public interface JudicialChainPort {

    /**
     * 提交存证 payload。本期实现仅落 outbox 后即时返回 stub receipt。
     */
    AttestationReceipt attest(AttestationPayload payload);

    /**
     * 核验链上记录与本地 payload。本期实现返回 {@code verified=false}。
     */
    AttestationVerifyResult verify(String txHash);
}
