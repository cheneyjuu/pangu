package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.infrastructure.persistence.entity.VotingSubjectRow;
import com.pangu.infrastructure.persistence.mapper.VotingSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link VotingSubjectRepository} 默认实现。
 *
 * <p>VotingSubject 是 lombok @SuperBuilder 的 POJO，本类完成 row → builder 翻译；
 * 写路径仅暴露 {@code updatePartyRatioFloor / updateStatus}（聚合根本期不直接写整行）。
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

    private VotingSubject toAggregate(VotingSubjectRow r) {
        return VotingSubject.builder()
                .subjectId(r.getSubjectId())
                .tenantId(r.getTenantId())
                .title(r.getTitle())
                .scope(r.getScope() == null ? VotingScope.COMMUNITY : VotingScope.fromDbValue(r.getScope()))
                .scopeReferenceId(r.getScopeReferenceId())
                .partyRatioFloor(r.getPartyRatioFloor())
                .build();
    }
}
