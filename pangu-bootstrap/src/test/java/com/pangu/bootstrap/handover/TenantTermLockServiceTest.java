package com.pangu.bootstrap.handover;

import com.pangu.application.handover.TenantTermLockService;
import com.pangu.domain.gateway.CommitteeKeyRevocationGateway;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.TenantTermStateRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link TenantTermLockService} 任期锁副作用单元测试。
 */
@ExtendWith(MockitoExtension.class)
public class TenantTermLockServiceTest {

    @Mock
    private TenantTermStateRepository termStateRepository;

    @Mock
    private VotingSubjectRepository votingSubjectRepository;

    @Mock
    private CommitteeKeyRevocationGateway keyRevocationGateway;

    @InjectMocks
    private TenantTermLockService service;

    @Test
    public void engageAfterElectionSettled_electionEngagesHandoverLock() {
        VotingSubject subject = subject(7001L, SubjectType.ELECTION);

        service.engageAfterElectionSettled(subject);

        verify(termStateRepository).engageHandoverLock(10001L, 7001L);
        verify(votingSubjectRepository).suspendVotingClocksForHandover(10001L, 7001L);
        verify(keyRevocationGateway, never()).revokeOutgoingDirectorKeys(10001L, 7001L);
    }

    @Test
    public void engageAfterElectionSettled_generalDoesNotEngageHandoverLock() {
        VotingSubject subject = subject(7002L, SubjectType.GENERAL);

        service.engageAfterElectionSettled(subject);

        verify(termStateRepository, never()).engageHandoverLock(10001L, 7002L);
        verify(votingSubjectRepository, never()).suspendVotingClocksForHandover(10001L, 7002L);
    }

    @Test
    public void confirmHandover_releasesLockThenRevokesOutgoingDirectorKeys() {
        service.confirmHandover(10001L, 800001L);

        InOrder order = inOrder(votingSubjectRepository, termStateRepository, keyRevocationGateway);
        order.verify(votingSubjectRepository).resumeVotingClocksAfterHandover(10001L);
        order.verify(termStateRepository).releaseHandoverLock(10001L, 800001L);
        order.verify(keyRevocationGateway).revokeOutgoingDirectorKeys(10001L, 800001L);
    }

    private VotingSubject subject(Long subjectId, SubjectType subjectType) {
        return VotingSubject.builder()
                .subjectId(subjectId)
                .tenantId(10001L)
                .title(subjectType == SubjectType.ELECTION ? "换届选举" : "一般议题")
                .subjectType(subjectType)
                .status(SubjectStatus.SETTLED)
                .build();
    }
}
