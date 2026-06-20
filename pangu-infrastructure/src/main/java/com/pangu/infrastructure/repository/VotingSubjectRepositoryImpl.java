package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.ElectionSubject;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.infrastructure.persistence.entity.VotingSubjectRow;
import com.pangu.infrastructure.persistence.mapper.VotingSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * {@link VotingSubjectRepository} 默认实现。
 *
 * <p>VotingSubject 是 lombok @SuperBuilder 的 POJO，本类完成 row → builder 翻译；
 * 写路径 M2 时仅暴露 {@code updatePartyRatioFloor / updateStatus}；
 * M3-2 增加 {@code insert / cancel / findPublishedReadyForOpen / findVisibleForOwner}。
 */
@Repository
@RequiredArgsConstructor
public class VotingSubjectRepositoryImpl implements VotingSubjectRepository {

    private final VotingSubjectMapper mapper;

    @Override
    public Optional<VotingSubject> findById(Long subjectId) {
        return Optional.ofNullable(mapper.selectById(subjectId)).map(this::toAggregate);
    }

    @Override
    public Optional<VotingSubject> findByIdForUpdate(Long subjectId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(subjectId)).map(this::toAggregate);
    }

    @Override
    public int updatePartyRatioFloor(Long subjectId, BigDecimal partyRatioFloor, long expectedVersion) {
        return mapper.updatePartyRatioFloor(subjectId, partyRatioFloor, expectedVersion);
    }

    @Override
    public int updateStatus(Long subjectId, int newStatusDbValue, long expectedVersion) {
        return mapper.updateStatus(subjectId, newStatusDbValue, expectedVersion);
    }

    @Override
    public List<VotingSubject> findExpiredVoting(Instant now, int limit) {
        return mapper.selectExpiredVoting(now, limit).stream().map(this::toAggregate).toList();
    }

    @Override
    public VotingSubject insert(VotingSubject subject) {
        VotingSubjectRow row = toRow(subject);
        mapper.insert(row);
        subject.setSubjectId(row.getSubjectId());
        subject.setVersion(0L);
        return subject;
    }

    @Override
    public int cancel(VotingSubject subject, long expectedVersion) {
        return mapper.cancel(
                subject.getSubjectId(),
                subject.getCancelledAt(),
                subject.getCancelledByUserId(),
                subject.getCancelReason(),
                expectedVersion);
    }

    @Override
    public List<VotingSubject> findPublishedReadyForOpen(Instant now, int limit) {
        return mapper.selectPublishedReadyForOpen(now, limit).stream().map(this::toAggregate).toList();
    }

    @Override
    public List<VotingSubject> findVisibleForOwner(Long tenantId, List<Long> buildingIds, int limit, int offset) {
        List<Long> safeBuildings = buildingIds == null ? Collections.emptyList() : buildingIds;
        return mapper.selectVisibleForOwner(tenantId, safeBuildings, limit, offset)
                .stream().map(this::toAggregate).toList();
    }

    @Override
    public Optional<Long> findActiveElectionSubjectId(Long tenantId) {
        return Optional.ofNullable(mapper.selectActiveElectionSubjectId(tenantId));
    }

    private VotingSubjectRow toRow(VotingSubject s) {
        VotingSubjectRow row = new VotingSubjectRow();
        row.setSubjectId(s.getSubjectId());
        row.setTenantId(s.getTenantId());
        row.setTitle(s.getTitle());
        row.setSubjectType(s.getSubjectType() == null ? null : s.getSubjectType().getDbValue());
        row.setScope(s.getScope() == null ? null : s.getScope().getDbValue());
        row.setScopeReferenceId(s.getScopeReferenceId());
        row.setStatus(s.getStatus() == null ? null : s.getStatus().getDbValue());
        row.setVoteStartAt(s.getVoteStartAt());
        row.setVoteEndAt(s.getVoteEndAt());
        row.setPartyRatioFloor(s.getPartyRatioFloor());
        row.setProposedByUserId(s.getProposedByUserId());
        row.setMaxWinners(s.getMaxWinners());
        // settled_at / publish_at / cancelled_* 由 DB / 业务流程后续维护，不在 insert 中显式塞值
        return row;
    }

    private VotingSubject toAggregate(VotingSubjectRow r) {
        SubjectType type = r.getSubjectType() == null ? null : SubjectType.fromDbValue(r.getSubjectType());
        if (type == SubjectType.ELECTION) {
            // ELECTION 构造 ElectionSubject：携带 maxWinners，候选人 list 留空，
            // 由 DefaultVotingEngineRouter 在 settle 前回填 APPROVED 候选人。
            return ElectionSubject.builder()
                    .subjectId(r.getSubjectId())
                    .tenantId(r.getTenantId())
                    .title(r.getTitle())
                    .subjectType(type)
                    .status(r.getStatus() == null ? null : SubjectStatus.fromDbValue(r.getStatus()))
                    .scope(r.getScope() == null ? VotingScope.COMMUNITY : VotingScope.fromDbValue(r.getScope()))
                    .scopeReferenceId(r.getScopeReferenceId())
                    .partyRatioFloor(r.getPartyRatioFloor())
                    .version(r.getVersion())
                    .voteStartAt(r.getVoteStartAt())
                    .voteEndAt(r.getVoteEndAt())
                    .proposedByUserId(r.getProposedByUserId())
                    .cancelledAt(r.getCancelledAt())
                    .cancelledByUserId(r.getCancelledByUserId())
                    .cancelReason(r.getCancelReason())
                    .maxWinners(r.getMaxWinners())
                    .candidates(Collections.emptyList())
                    .build();
        }
        return VotingSubject.builder()
                .subjectId(r.getSubjectId())
                .tenantId(r.getTenantId())
                .title(r.getTitle())
                .subjectType(type)
                .status(r.getStatus() == null ? null : SubjectStatus.fromDbValue(r.getStatus()))
                .scope(r.getScope() == null ? VotingScope.COMMUNITY : VotingScope.fromDbValue(r.getScope()))
                .scopeReferenceId(r.getScopeReferenceId())
                .partyRatioFloor(r.getPartyRatioFloor())
                .version(r.getVersion())
                .voteStartAt(r.getVoteStartAt())
                .voteEndAt(r.getVoteEndAt())
                .proposedByUserId(r.getProposedByUserId())
                .cancelledAt(r.getCancelledAt())
                .cancelledByUserId(r.getCancelledByUserId())
                .cancelReason(r.getCancelReason())
                .maxWinners(r.getMaxWinners())
                .build();
    }
}

