package com.pangu.bootstrap.voting;

import com.pangu.application.voting.VoteMonitorQueryService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.domain.gateway.VoteCastMonitorGateway;
import com.pangu.domain.gateway.VoteCastMonitorGateway.VoteCastCounters;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteCastMonitorSnapshot;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * E2b 监控告警阈值判定单元测试。
 */
@ExtendWith(MockitoExtension.class)
public class VoteMonitorQueryServiceTest {

    @Mock
    private VotingSubjectRepository subjectRepository;
    @Mock
    private VoteCastMonitorGateway monitorGateway;

    @InjectMocks
    private VoteMonitorQueryService service;

    private static final Long SUBJECT_ID = 7001L;
    private static final Long TENANT = 10001L;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(service, "unsignedRatioThreshold", new BigDecimal("0.30"));
        ReflectionTestUtils.setField(service, "rapidIntervalThreshold", 2L);
    }

    @Test
    public void query_countersExceedThresholds_returnsAlerts() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(subject(TENANT)));
        when(monitorGateway.loadCounters(SUBJECT_ID))
                .thenReturn(new VoteCastCounters(SUBJECT_ID, 10L, 4L, 2L));

        VoteCastMonitorSnapshot snapshot = service.query(SUBJECT_ID, TENANT);

        assertEquals(10L, snapshot.totalCount());
        assertEquals(4L, snapshot.unsignedCount());
        assertEquals(0, new BigDecimal("0.4000").compareTo(snapshot.unsignedRatio()));
        assertTrue(snapshot.unsignedRatioAlert());
        assertEquals(2L, snapshot.rapidIntervalCount());
        assertTrue(snapshot.rapidIntervalAlert());
    }

    @Test
    public void query_noVotes_returnsZeroRatioAndNoAlerts() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(subject(TENANT)));
        when(monitorGateway.loadCounters(SUBJECT_ID))
                .thenReturn(VoteCastCounters.empty(SUBJECT_ID));

        VoteCastMonitorSnapshot snapshot = service.query(SUBJECT_ID, TENANT);

        assertEquals(0L, snapshot.totalCount());
        assertEquals(0, BigDecimal.ZERO.setScale(4).compareTo(snapshot.unsignedRatio()));
        assertFalse(snapshot.unsignedRatioAlert());
        assertFalse(snapshot.rapidIntervalAlert());
    }

    @Test
    public void query_crossTenant_throwsNotFound() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(subject(99999L)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.query(SUBJECT_ID, TENANT));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_FOUND, ex.getReason());
    }

    private VotingSubject subject(Long tenantId) {
        return VotingSubject.builder()
                .subjectId(SUBJECT_ID)
                .tenantId(tenantId)
                .title("监控测试议题")
                .subjectType(SubjectType.GENERAL)
                .scope(VotingScope.COMMUNITY)
                .status(SubjectStatus.VOTING)
                .voteStartAt(Instant.parse("2026-07-01T00:00:00Z"))
                .voteEndAt(Instant.parse("2026-07-15T00:00:00Z"))
                .version(0L)
                .build();
    }
}
