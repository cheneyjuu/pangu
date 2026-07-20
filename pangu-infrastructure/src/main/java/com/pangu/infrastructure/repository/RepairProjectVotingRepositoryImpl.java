// 关联业务：实现维修授权提案与统一表决包关联的持久化端口。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.repair.RepairProjectVoting;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.repository.RepairProjectVotingRepository;
import com.pangu.infrastructure.persistence.entity.RepairProjectVotingOwnerTaskRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectVotingRow;
import com.pangu.infrastructure.persistence.mapper.RepairProjectVotingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepairProjectVotingRepositoryImpl implements RepairProjectVotingRepository {

    private final RepairProjectVotingMapper mapper;

    @Override
    public RepairProjectVoting insert(RepairProjectVoting link) {
        RepairProjectVotingRow row = toRow(link);
        mapper.insert(row);
        return toDomain(row);
    }

    @Override
    public Optional<RepairProjectVoting> find(Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.find(projectId, planId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<RepairProjectVoting> findForUpdate(Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findForUpdate(projectId, planId, tenantId)).map(this::toDomain);
    }

    @Override
    public int markVoting(Long linkId, Long tenantId, Long openedByUserId, Instant openedAt, long expectedVersion) {
        return mapper.markVoting(linkId, tenantId, openedByUserId, openedAt, expectedVersion);
    }

    @Override
    public int settle(Long linkId, Long tenantId, RepairProjectVoting.Result result,
                      Long settledByUserId, Instant settledAt, long expectedVersion) {
        return mapper.settle(linkId, tenantId, result.name(), settledByUserId, settledAt, expectedVersion);
    }

    @Override
    public List<RepairProjectVoting> listReadyForOpen(Instant now, int limit) {
        return mapper.listReadyForOpen(now, limit).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RepairProjectVoting.OwnerTask> listOwnerTasks(Long tenantId, Long ownerUid) {
        return mapper.listOwnerTasks(tenantId, ownerUid).stream().map(this::toDomain).toList();
    }

    private RepairProjectVoting toDomain(RepairProjectVotingRow row) {
        return new RepairProjectVoting(
                row.getLinkId(), row.getProjectId(), row.getPlanId(), row.getTenantId(), row.getSubjectId(),
                row.getExecutionPackageId(), row.getRuleId(), row.getRuleConfigurationHash(),
                row.getPaperBallotTemplateAttachmentId(), row.getPaperBallotTemplateHash(),
                VotingExecutionPackage.CollectionMode.valueOf(row.getCollectionMode()),
                RepairProjectVoting.Status.valueOf(row.getStatus()),
                row.getResult() == null ? null : RepairProjectVoting.Result.valueOf(row.getResult()),
                row.getPreparedByUserId(), row.getPreparedAt(), row.getOpenedByUserId(), row.getOpenedAt(),
                row.getSettledByUserId(), row.getSettledAt(), row.getVersion());
    }

    private RepairProjectVoting.OwnerTask toDomain(RepairProjectVotingOwnerTaskRow row) {
        return new RepairProjectVoting.OwnerTask(
                row.getProjectId(), row.getPlanId(), row.getProjectNo(), row.getProjectName(), row.getSubjectId(),
                row.getExecutionPackageId(), row.getOpid(), row.getRoomId(),
                row.getBuildingName(), row.getUnitName(), row.getRoomName(),
                VotingExecutionPackage.CollectionMode.valueOf(row.getCollectionMode()),
                RepairProjectVoting.Status.valueOf(row.getStatus()),
                row.getResult() == null ? null : RepairProjectVoting.Result.valueOf(row.getResult()),
                row.getPackageHash(), row.getVoteStartAt(), row.getVoteEndAt());
    }

    private RepairProjectVotingRow toRow(RepairProjectVoting link) {
        RepairProjectVotingRow row = new RepairProjectVotingRow();
        row.setLinkId(link.linkId());
        row.setProjectId(link.projectId());
        row.setPlanId(link.planId());
        row.setTenantId(link.tenantId());
        row.setSubjectId(link.subjectId());
        row.setExecutionPackageId(link.executionPackageId());
        row.setRuleId(link.ruleId());
        row.setRuleConfigurationHash(link.ruleConfigurationHash());
        row.setPaperBallotTemplateAttachmentId(link.paperBallotTemplateAttachmentId());
        row.setPaperBallotTemplateHash(link.paperBallotTemplateHash());
        row.setCollectionMode(link.collectionMode().name());
        row.setStatus(link.status().name());
        row.setResult(link.result() == null ? null : link.result().name());
        row.setPreparedByUserId(link.preparedByUserId());
        row.setPreparedAt(link.preparedAt());
        row.setOpenedByUserId(link.openedByUserId());
        row.setOpenedAt(link.openedAt());
        row.setSettledByUserId(link.settledByUserId());
        row.setSettledAt(link.settledAt());
        row.setVersion(link.version());
        return row;
    }
}
