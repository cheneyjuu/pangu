// 关联业务：持久化议题结算结果及其正式表决包、冻结名册、方案和规则追溯信息。
package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.VotingResultRepository;
import com.pangu.infrastructure.persistence.entity.VotingResultRow;
import com.pangu.infrastructure.persistence.mapper.VotingResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link VotingResultRepository} 默认实现。
 *
 * <p>{@link #upsert} 行为：先 SELECT 是否存在 → 存在则 UPDATE（递增 statistics_version），
 * 否则 INSERT。需在调用方包裹事务（或对 t_voting_subject 做 SELECT FOR UPDATE）以保证
 * 重复结算的并发正确性。
 */
@Repository
@RequiredArgsConstructor
public class VotingResultRepositoryImpl implements VotingResultRepository {

    private final VotingResultMapper mapper;

    @Override
    public void upsert(Snapshot snapshot) {
        VotingResultRow existing = mapper.selectBySubjectId(snapshot.subjectId());
        VotingResultRow row = toRow(snapshot);
        if (existing == null) {
            mapper.insert(row);
        } else {
            // 调用方传入的 statisticsVersion 即新版本（已经 +1）；UPDATE 直接覆盖。
            mapper.updateSnapshot(row);
        }
    }

    @Override
    public Optional<Snapshot> findBySubjectId(Long subjectId) {
        VotingResultRow row = mapper.selectBySubjectId(subjectId);
        return row == null ? Optional.empty() : Optional.of(toSnapshot(row));
    }

    private VotingResultRow toRow(Snapshot s) {
        VotingResultRow row = new VotingResultRow();
        row.setSubjectId(s.subjectId());
        row.setStatisticsVersion(s.statisticsVersion());
        row.setTotalArea(s.totalArea());
        row.setTotalOwnerCount(s.totalOwnerCount());
        row.setParticipatingArea(s.participatingArea());
        row.setParticipatingOwnerCount(s.participatingOwnerCount());
        row.setQuorumSatisfied(s.quorumSatisfied() ? 1 : 0);
        row.setPassed(s.passed() ? 1 : 0);
        row.setResultPayload(s.resultPayloadJson());
        row.setDenominatorSnapshotId(s.denominatorSnapshotId());
        row.setAttestationTxHash(s.attestationTxHash());
        row.setExecutionPackageId(s.executionPackageId());
        row.setElectorateSnapshotId(s.electorateSnapshotId());
        row.setProposalSnapshotHash(s.proposalSnapshotHash());
        row.setRuleSnapshotHash(s.ruleSnapshotHash());
        row.setExecutionPackageHash(s.executionPackageHash());
        return row;
    }

    private Snapshot toSnapshot(VotingResultRow row) {
        return new Snapshot(
                row.getSubjectId(),
                row.getStatisticsVersion() == null ? 1 : row.getStatisticsVersion(),
                row.getTotalArea(),
                row.getTotalOwnerCount() == null ? 0L : row.getTotalOwnerCount(),
                row.getParticipatingArea(),
                row.getParticipatingOwnerCount() == null ? 0L : row.getParticipatingOwnerCount(),
                row.getQuorumSatisfied() != null && row.getQuorumSatisfied() == 1,
                row.getPassed() != null && row.getPassed() == 1,
                row.getResultPayload(),
                row.getDenominatorSnapshotId(),
                row.getAttestationTxHash(),
                row.getExecutionPackageId(),
                row.getElectorateSnapshotId(),
                row.getProposalSnapshotHash(),
                row.getRuleSnapshotHash(),
                row.getExecutionPackageHash());
    }
}
