package com.pangu.application.voting;

import com.pangu.domain.gateway.VoteCastMonitorGateway;
import com.pangu.domain.gateway.VoteCastMonitorGateway.VoteCastCounters;
import com.pangu.domain.model.voting.VoteCastMonitorSnapshot;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 管理端投票监控基线查询服务。
 */
@Service
@RequiredArgsConstructor
public class VoteMonitorQueryService {

    private final VotingSubjectRepository subjectRepository;
    private final VoteCastMonitorGateway monitorGateway;

    @Value("${platform.voting.monitor.unsigned-ratio-threshold:0.30}")
    private BigDecimal unsignedRatioThreshold;

    @Value("${platform.voting.monitor.rapid-interval-threshold:1}")
    private long rapidIntervalThreshold;

    @Transactional(readOnly = true)
    public VoteCastMonitorSnapshot query(Long subjectId, Long tenantId) {
        VotingSubject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + subjectId));
        if (!subject.getTenantId().equals(tenantId)) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                    "议题不存在 subjectId=" + subjectId);
        }

        VoteCastCounters counters = monitorGateway.loadCounters(subjectId);
        BigDecimal unsignedRatio = ratio(counters.unsignedCount(), counters.totalCount());
        boolean unsignedAlert = counters.totalCount() > 0
                && unsignedRatio.compareTo(unsignedRatioThreshold) > 0;
        boolean rapidAlert = rapidIntervalThreshold > 0
                && counters.rapidIntervalCount() >= rapidIntervalThreshold;
        return new VoteCastMonitorSnapshot(
                subjectId,
                counters.totalCount(),
                counters.unsignedCount(),
                unsignedRatio,
                unsignedRatioThreshold,
                unsignedAlert,
                counters.rapidIntervalCount(),
                rapidIntervalThreshold,
                rapidAlert);
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
