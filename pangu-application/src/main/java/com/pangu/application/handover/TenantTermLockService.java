package com.pangu.application.handover;

import com.pangu.domain.gateway.CommitteeKeyRevocationGateway;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.TenantTermStateRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 租户任期锁应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantTermLockService {

    private final TenantTermStateRepository termStateRepository;
    private final VotingSubjectRepository votingSubjectRepository;
    private final CommitteeKeyRevocationGateway keyRevocationGateway;

    public void engageAfterElectionSettled(VotingSubject subject) {
        if (subject.getSubjectType() == SubjectType.ELECTION) {
            termStateRepository.engageHandoverLock(subject.getTenantId(), subject.getSubjectId());
            int suspended = votingSubjectRepository.suspendVotingClocksForHandover(
                    subject.getTenantId(), subject.getSubjectId());
            log.info("Clock Suspend engaged tenantId={} electionSubjectId={} suspendedSubjects={}",
                    subject.getTenantId(), subject.getSubjectId(), suspended);
        }
    }

    public void confirmHandover(Long tenantId, Long confirmedByUserId) {
        int resumed = votingSubjectRepository.resumeVotingClocksAfterHandover(tenantId);
        termStateRepository.releaseHandoverLock(tenantId, confirmedByUserId);
        keyRevocationGateway.revokeOutgoingDirectorKeys(tenantId, confirmedByUserId);
        log.info("Clock Suspend resumed tenantId={} confirmedByUserId={} resumedSubjects={}",
                tenantId, confirmedByUserId, resumed);
    }
}
