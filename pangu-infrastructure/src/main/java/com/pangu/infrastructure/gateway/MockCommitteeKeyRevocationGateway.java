package com.pangu.infrastructure.gateway;

import com.pangu.domain.gateway.CommitteeKeyRevocationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 梯度 C 的老主任密钥回收 mock 实现。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "platform.committee-key-revocation.provider", havingValue = "mock")
public class MockCommitteeKeyRevocationGateway implements CommitteeKeyRevocationGateway {

    @Override
    public void revokeOutgoingDirectorKeys(Long tenantId, Long confirmedByUserId) {
        log.info("mock revoke outgoing committee director keys tenantId={} confirmedByUserId={}",
                tenantId, confirmedByUserId);
    }
}
