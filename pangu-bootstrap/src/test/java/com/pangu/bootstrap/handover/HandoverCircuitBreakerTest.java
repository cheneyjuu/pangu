package com.pangu.bootstrap.handover;

import com.pangu.application.handover.HandoverCircuitBreaker;
import com.pangu.domain.model.handover.TenantTermState;
import com.pangu.domain.model.handover.TenantTermStatus;
import com.pangu.domain.repository.TenantTermStateRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link HandoverCircuitBreaker} 纯单元测试：验证「查询派生」语义只是仓储结果的透传——
 * breaker 自身不含任何状态，命中/空两路均原样返回 {@link VotingSubjectRepository} 的判定。
 */
@ExtendWith(MockitoExtension.class)
public class HandoverCircuitBreakerTest {

    @Mock
    private TenantTermStateRepository termStateRepository;

    @Mock
    private VotingSubjectRepository subjectRepository;

    @InjectMocks
    private HandoverCircuitBreaker breaker;

    @Test
    public void activeElection_persistentLockWins() {
        when(termStateRepository.findByTenantId(10001L)).thenReturn(Optional.of(new TenantTermState(
                10001L, TenantTermStatus.HANDOVER_LOCK, 777L, null, null, null)));

        assertEquals(Optional.of(777L), breaker.activeElectionSubjectId(10001L),
                "持久 HANDOVER_LOCK 应优先返回锁定选举 subjectId");
    }

    @Test
    public void activeElection_fallsBackToQueryDerivedHit() {
        when(termStateRepository.findByTenantId(10001L)).thenReturn(Optional.empty());
        when(subjectRepository.findActiveElectionSubjectId(10001L)).thenReturn(Optional.of(777L));
        assertEquals(Optional.of(777L), breaker.activeElectionSubjectId(10001L),
                "无持久锁时应透传在途选举 subjectId");
    }

    @Test
    public void activeElection_fallsBackToQueryDerivedEmpty() {
        when(termStateRepository.findByTenantId(10001L)).thenReturn(Optional.empty());
        when(subjectRepository.findActiveElectionSubjectId(10001L)).thenReturn(Optional.empty());
        assertTrue(breaker.activeElectionSubjectId(10001L).isEmpty(),
                "无在途选举时应透传空");
    }
}
