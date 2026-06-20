package com.pangu.bootstrap.handover;

import com.pangu.application.handover.HandoverCircuitBreaker;
import com.pangu.application.voting.ProposalLifecycleService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.command.ProposeSubjectCommand;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProposalLifecycleService#propose} 的换届熔断闸门单元测试（Mockito）。
 *
 * <p>聚焦"立项熔断"分支，不启 Spring 容器：
 * <ul>
 *   <li>换届在途 + GENERAL/MAJOR 立项 → 抛 {@code PROPOSE_FROZEN_HANDOVER}，
 *       且**绝不**落库（{@code subjectRepository.insert} 一次都不能被调用）；</li>
 *   <li>换届在途 + ELECTION 立项（带 maxWinners）→ **放行**正常落库
 *       （避免下一届换届自我死锁）；</li>
 *   <li>无换届 + GENERAL 立项 → 正常落库（回归不破）。</li>
 * </ul>
 */
public class ProposalHandoverGuardTest {

    private static final long TENANT = 10001L;
    private static final long PROPOSER = 800101L;

    private final VotingSubjectRepository subjectRepository = mock(VotingSubjectRepository.class);
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository =
            mock(OwnerPropertyVotingRepository.class);
    private final HandoverCircuitBreaker handoverCircuitBreaker = mock(HandoverCircuitBreaker.class);

    private final ProposalLifecycleService service = new ProposalLifecycleService(
            subjectRepository, ownerPropertyVotingRepository, handoverCircuitBreaker);

    private ProposeSubjectCommand cmd(SubjectType type, Integer maxWinners) {
        return new ProposeSubjectCommand(
                TENANT, type, VotingScope.COMMUNITY, null, "换届熔断立项测试",
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-15T00:00:00Z"),
                PROPOSER, new BigDecimal("0.50"), maxWinners);
    }

    @Test
    public void handoverInProgress_blocksGeneralProposeAndSkipsInsert() {
        when(handoverCircuitBreaker.activeElectionSubjectId(TENANT)).thenReturn(Optional.of(888L));

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.propose(cmd(SubjectType.GENERAL, null)));

        assertEquals(VotingApplicationException.Reason.PROPOSE_FROZEN_HANDOVER, ex.getReason());
        verify(subjectRepository, never()).insert(any(VotingSubject.class));
    }

    @Test
    public void handoverInProgress_blocksMajorProposeAndSkipsInsert() {
        when(handoverCircuitBreaker.activeElectionSubjectId(TENANT)).thenReturn(Optional.of(888L));

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.propose(cmd(SubjectType.MAJOR, null)));

        assertEquals(VotingApplicationException.Reason.PROPOSE_FROZEN_HANDOVER, ex.getReason());
        verify(subjectRepository, never()).insert(any(VotingSubject.class));
    }

    @Test
    public void handoverInProgress_allowsElectionPropose() {
        // 换届在途仍放行 ELECTION 立项 —— 否则下一届换届无法发起（自我死锁）。
        when(subjectRepository.insert(any(VotingSubject.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        VotingSubject persisted = service.propose(cmd(SubjectType.ELECTION, 2));

        assertEquals(SubjectType.ELECTION, persisted.getSubjectType());
        assertEquals(2, persisted.getMaxWinners());
        // ELECTION 放行：熔断检测压根不该被触发
        verify(handoverCircuitBreaker, never()).activeElectionSubjectId(any());
        verify(subjectRepository).insert(any(VotingSubject.class));
    }

    @Test
    public void noHandover_allowsGeneralPropose() {
        when(handoverCircuitBreaker.activeElectionSubjectId(TENANT)).thenReturn(Optional.empty());
        when(subjectRepository.insert(any(VotingSubject.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        VotingSubject persisted = service.propose(cmd(SubjectType.GENERAL, null));

        assertEquals(SubjectType.GENERAL, persisted.getSubjectType());
        verify(subjectRepository).insert(any(VotingSubject.class));
    }
}
