package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidatePoolSnapshot;
import com.pangu.domain.model.voting.CandidateStatus;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.infrastructure.persistence.entity.CandidatePoolCount;
import com.pangu.infrastructure.persistence.entity.ElectionCandidateRow;
import com.pangu.infrastructure.persistence.mapper.ElectionCandidateMapper;
import com.pangu.infrastructure.persistence.mapper.PartyRatioPolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link ElectionCandidateRegistry} 默认实现。
 *
 * <p>候选人池快照（{@link #countActivePool}）复用 {@link PartyRatioPolicyMapper#countCurrentCandidatePool}，
 * 与放宽断路器对账查询同口径；M3-3 起的提名/审查/列表/计数门走专属
 * {@link ElectionCandidateMapper}。
 */
@Repository
@RequiredArgsConstructor
public class ElectionCandidateRegistryImpl implements ElectionCandidateRegistry {

    private final PartyRatioPolicyMapper mapper;
    private final ElectionCandidateMapper candidateMapper;

    @Override
    public CandidatePoolSnapshot countActivePool(Long subjectId) {
        CandidatePoolCount count = mapper.countCurrentCandidatePool(subjectId);
        if (count == null) {
            return new CandidatePoolSnapshot(0L, 0L);
        }
        return new CandidatePoolSnapshot(count.getPartyCount(), count.getEligibleCount());
    }

    @Override
    public List<Candidate> findApprovedCandidates(Long subjectId) {
        return candidateMapper.selectApprovedBySubject(subjectId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Candidate> findBySubject(Long subjectId) {
        return candidateMapper.selectBySubject(subjectId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Candidate> findById(Long candidateId) {
        return Optional.ofNullable(candidateMapper.selectById(candidateId)).map(this::toDomain);
    }

    @Override
    public Long nominate(Long subjectId, Long uid, String name, boolean partyMember) {
        ElectionCandidateRow row = new ElectionCandidateRow();
        row.setSubjectId(subjectId);
        row.setUid(uid);
        row.setName(name);
        row.setIsPartyMember(partyMember ? 1 : 0);
        try {
            candidateMapper.insertCandidate(row);
        } catch (DuplicateKeyException e) {
            throw new DuplicateCandidateException(
                    "候选人重复提名 subjectId=" + subjectId + " uid=" + uid, e);
        }
        return row.getCandidateId();
    }

    @Override
    public int updateQualification(Long candidateId, int newStatusDbValue) {
        return candidateMapper.updateQualification(candidateId, newStatusDbValue);
    }

    @Override
    public long countSupportByOpid(Long subjectId, Long opid) {
        return candidateMapper.countSupportByOpid(subjectId, opid);
    }

    private Candidate toDomain(ElectionCandidateRow r) {
        return Candidate.builder()
                .candidateId(r.getCandidateId())
                .subjectId(r.getSubjectId())
                .uid(r.getUid())
                .name(r.getName())
                .partyMember(r.getIsPartyMember() != null && r.getIsPartyMember() == 1)
                .qualificationStatus(r.getQualificationStatus() == null
                        ? null : CandidateStatus.fromDbValue(r.getQualificationStatus()))
                .build();
    }
}
