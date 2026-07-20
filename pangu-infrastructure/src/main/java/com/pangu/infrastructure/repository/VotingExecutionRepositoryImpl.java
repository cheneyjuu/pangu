// 关联业务：持久化正式表决统一执行包、冻结名册、同源分母、送达、票据和审计记录。
package com.pangu.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VotingBallotRecord;
import com.pangu.domain.model.voting.VotingDeliveryRecord;
import com.pangu.domain.model.voting.VotingElectorateSnapshot;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingNonResponseDerivation;
import com.pangu.domain.model.voting.VotingNonResponsePolicy;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.repository.VotingExecutionRepository;
import com.pangu.infrastructure.persistence.entity.DenominatorItemRow;
import com.pangu.infrastructure.persistence.entity.DenominatorSnapshotRow;
import com.pangu.infrastructure.persistence.entity.VotingBallotRecordRow;
import com.pangu.infrastructure.persistence.entity.VotingDeliveryRecordRow;
import com.pangu.infrastructure.persistence.entity.VotingElectorateCandidateRow;
import com.pangu.infrastructure.persistence.entity.VotingElectorateItemRow;
import com.pangu.infrastructure.persistence.entity.VotingElectorateSnapshotRow;
import com.pangu.infrastructure.persistence.entity.VotingExecutionPackageRow;
import com.pangu.infrastructure.persistence.entity.VotingNonResponseDerivationRow;
import com.pangu.infrastructure.persistence.mapper.VotingDenominatorSnapshotMapper;
import com.pangu.infrastructure.persistence.mapper.VotingExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VotingExecutionRepositoryImpl implements VotingExecutionRepository {

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() { };

    private final VotingExecutionMapper mapper;
    private final VotingDenominatorSnapshotMapper denominatorMapper;
    private final ObjectMapper objectMapper;

    @Override
    public VotingExecutionPackage insertPackage(VotingExecutionPackage ballotPackage) {
        VotingExecutionPackageRow row = toRow(ballotPackage);
        mapper.insertPackage(row);
        ballotPackage.assignId(row.getPackageId());
        return ballotPackage;
    }

    @Override
    public Optional<VotingExecutionPackage> findPackage(Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.selectPackage(packageId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<VotingExecutionPackage> findPackageForUpdate(Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.selectPackageForUpdate(packageId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<VotingExecutionPackage> findPackageBySubjectId(Long subjectId) {
        return Optional.ofNullable(mapper.selectPackageBySubjectId(subjectId)).map(this::toDomain);
    }

    @Override
    public void attachSubject(Long packageId, Long tenantId, Long subjectId) {
        mapper.insertPackageSubject(packageId, tenantId, subjectId);
    }

    @Override
    public List<Long> listSubjectIds(Long packageId, Long tenantId) {
        return mapper.selectSubjectIds(packageId, tenantId);
    }

    @Override
    public List<VotingElectorateSnapshot.Candidate> listElectorateCandidates(
            Long tenantId, VotingScope scope, Long scopeReferenceId) {
        if (scope == VotingScope.REPAIR_ALLOCATION) {
            throw new IllegalArgumentException("维修方案范围必须提供精确房屋集合，不能按 planId 查询实时名册");
        }
        return mapper.selectElectorateCandidates(tenantId, scope.getDbValue(), scopeReferenceId).stream()
                .map(this::toCandidate)
                .toList();
    }

    @Override
    public List<VotingElectorateSnapshot.Candidate> listElectorateCandidatesByRoomIds(
            Long tenantId, List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectElectorateCandidatesByRoomIds(tenantId, roomIds.stream().distinct().toList()).stream()
                .map(this::toCandidate)
                .toList();
    }

    @Override
    public VotingElectorateSnapshot insertElectorateSnapshot(VotingElectorateSnapshot snapshot) {
        VotingElectorateSnapshotRow row = toRow(snapshot);
        mapper.insertElectorateSnapshot(row);
        List<VotingElectorateItemRow> itemRows = snapshot.items().stream().map(this::toRow).toList();
        mapper.insertElectorateItems(row.getSnapshotId(), itemRows);
        return snapshot.withSnapshotId(row.getSnapshotId());
    }

    @Override
    public Optional<VotingElectorateSnapshot> findElectorateSnapshot(Long snapshotId, Long tenantId) {
        VotingElectorateSnapshotRow row = mapper.selectElectorateSnapshot(snapshotId, tenantId);
        if (row == null) {
            return Optional.empty();
        }
        List<VotingElectorateSnapshot.Item> items = mapper.selectElectorateItems(snapshotId).stream()
                .map(this::toDomain)
                .toList();
        return Optional.of(toDomain(row, items));
    }

    @Override
    public Optional<VotingElectorateSnapshot.Item> findElectorateItem(
            Long packageId, Long tenantId, Long opid) {
        return Optional.ofNullable(mapper.selectElectorateItemByOpid(packageId, tenantId, opid))
                .map(this::toDomain);
    }

    @Override
    public void lockElectorateItem(Long packageId, Long tenantId, Long electorateItemId) {
        if (mapper.lockElectorateItem(packageId, tenantId, electorateItemId) == null) {
            throw new IllegalStateException("冻结表决人名册项不存在 electorateItemId=" + electorateItemId);
        }
    }

    @Override
    public int updatePackage(VotingExecutionPackage ballotPackage) {
        return mapper.updatePackage(toRow(ballotPackage));
    }

    @Override
    public Long insertSubjectDenominatorSnapshot(Long subjectId,
                                                 VotingScope scope,
                                                 Long scopeReferenceId,
                                                 VotingElectorateSnapshot snapshot) {
        Long snapshotId = denominatorMapper.insertSnapshotIfAbsent(
                subjectId,
                scope.getDbValue(),
                scopeReferenceId,
                snapshot.totalArea(),
                snapshot.totalOwnerCount(),
                snapshot.itemCount(),
                snapshot.aggregateHash());
        if (snapshotId == null) {
            DenominatorSnapshotRow existing = denominatorMapper.selectSnapshotBySubjectId(subjectId);
            if (existing == null
                    || existing.getTotalArea().compareTo(snapshot.totalArea()) != 0
                    || !existing.getTotalOwnerCount().equals(snapshot.totalOwnerCount())
                    || !existing.getAggregateHash().equals(snapshot.aggregateHash())) {
                throw new IllegalStateException("议题已存在与本次冻结名册不一致的计票基数 subjectId=" + subjectId);
            }
            return existing.getSnapshotId();
        }
        List<DenominatorItemRow> items = snapshot.items().stream()
                .map(this::toDenominatorItem)
                .toList();
        denominatorMapper.insertItems(snapshotId, items);
        return snapshotId;
    }

    @Override
    public VotingDeliveryRecord insertDelivery(VotingDeliveryRecord delivery) {
        VotingDeliveryRecordRow row = new VotingDeliveryRecordRow();
        row.setPackageId(delivery.packageId());
        row.setElectorateItemId(delivery.electorateItemId());
        row.setTenantId(delivery.tenantId());
        row.setDeliveryChannel(delivery.deliveryChannel().getDbValue());
        row.setDeliveryMethod(delivery.deliveryMethod());
        row.setEvidenceHash(delivery.evidenceHash());
        row.setDeliveredByUserId(delivery.deliveredByUserId());
        row.setDeliveredAt(delivery.deliveredAt());
        mapper.insertDelivery(row);
        return new VotingDeliveryRecord(
                row.getDeliveryId(), delivery.packageId(), delivery.electorateItemId(), delivery.tenantId(),
                delivery.opid(), delivery.uid(), delivery.deliveryChannel(), delivery.deliveryMethod(),
                delivery.evidenceHash(), delivery.deliveredByUserId(), delivery.deliveredAt());
    }

    @Override
    public boolean deliveryExists(Long packageId,
                                  Long tenantId,
                                  Long electorateItemId,
                                  VoteChannel channel) {
        return mapper.deliveryExists(
                packageId, tenantId, electorateItemId, normalizeDeliveryChannel(channel).getDbValue());
    }

    @Override
    public List<VotingDeliveryRecord> listDeliveries(Long packageId, Long tenantId) {
        return mapper.selectDeliveries(packageId, tenantId).stream()
                .map(row -> new VotingDeliveryRecord(
                        row.getDeliveryId(), row.getPackageId(), row.getElectorateItemId(), row.getTenantId(),
                        row.getRepresentativeOpid(), row.getRepresentativeUid(),
                        VoteChannel.fromDbValue(row.getDeliveryChannel()), row.getDeliveryMethod(),
                        row.getEvidenceHash(), row.getDeliveredByUserId(), row.getDeliveredAt()))
                .toList();
    }

    @Override
    public VotingBallotRecord insertBallot(VotingBallotRecord ballot) {
        VotingBallotRecordRow row = new VotingBallotRecordRow();
        row.setPackageId(ballot.packageId());
        row.setSubjectId(ballot.subjectId());
        row.setVoteId(ballot.voteId());
        row.setElectorateItemId(ballot.electorateItemId());
        row.setTenantId(ballot.tenantId());
        row.setVoteChannel(ballot.voteChannel().getDbValue());
        row.setPackageHash(ballot.packageHash());
        row.setBallotFileHash(ballot.ballotFileHash());
        row.setSignatureHash(ballot.signatureHash());
        row.setRecordedByUserId(ballot.recordedByUserId());
        row.setCastAt(ballot.castAt());
        row.setSupersedesBallotId(ballot.supersedesBallotId());
        row.setResolutionPolicy(ballot.resolutionPolicy() == null ? null : ballot.resolutionPolicy().name());
        row.setResolutionReason(ballot.resolutionReason());
        mapper.insertBallot(row);
        return new VotingBallotRecord(
                row.getBallotId(), ballot.packageId(), ballot.subjectId(), ballot.voteId(),
                ballot.electorateItemId(), ballot.tenantId(), ballot.opid(), ballot.uid(),
                ballot.voteChannel(), ballot.packageHash(), ballot.ballotFileHash(),
                ballot.signatureHash(), ballot.recordedByUserId(), ballot.castAt(),
                ballot.supersedesBallotId(), ballot.resolutionPolicy(), ballot.resolutionReason());
    }

    @Override
    public Optional<VotingBallotRecord> findActiveBallot(Long subjectId,
                                                         Long electorateItemId,
                                                         Long tenantId) {
        return Optional.ofNullable(mapper.selectActiveBallot(subjectId, electorateItemId, tenantId))
                .map(row -> new VotingBallotRecord(
                        row.getBallotId(), row.getPackageId(), row.getSubjectId(), row.getVoteId(),
                        row.getElectorateItemId(), row.getTenantId(), row.getRepresentativeOpid(),
                        row.getRepresentativeUid(), VoteChannel.fromDbValue(row.getVoteChannel()),
                        row.getPackageHash(), row.getBallotFileHash(), row.getSignatureHash(),
                        row.getRecordedByUserId(), row.getCastAt(), row.getSupersedesBallotId(),
                        row.getResolutionPolicy() == null ? null
                                : VotingExecutionPackage.DuplicateBallotPolicy.valueOf(row.getResolutionPolicy()),
                        row.getResolutionReason()));
    }

    @Override
    public int invalidateBallot(Long ballotId, String invalidReason, java.time.Instant invalidatedAt) {
        return mapper.invalidateBallot(ballotId, invalidReason, invalidatedAt);
    }

    @Override
    public List<VotingBallotRecord> listActiveBallots(Long subjectId, Long tenantId) {
        return mapper.selectActiveBallots(subjectId, tenantId).stream()
                .map(this::toBallotRecord)
                .toList();
    }

    @Override
    public void insertNonResponseDerivations(List<VotingNonResponseDerivation> derivations) {
        if (derivations == null || derivations.isEmpty()) {
            return;
        }
        mapper.insertNonResponseDerivations(derivations.stream().map(this::toRow).toList());
    }

    @Override
    public List<VotingNonResponseDerivation> listNonResponseDerivations(Long subjectId, Long tenantId) {
        return mapper.selectNonResponseDerivations(subjectId, tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countAudits(Long packageId, Long tenantId, String eventType) {
        return mapper.countAudits(packageId, tenantId, eventType);
    }

    @Override
    public void insertAudit(Long packageId,
                            Long tenantId,
                            String eventType,
                            String fromStatus,
                            String toStatus,
                            Long actorUserId,
                            String detailJson,
                            java.time.Instant occurredAt) {
        mapper.insertAudit(
                packageId, tenantId, eventType, fromStatus, toStatus,
                actorUserId, detailJson, occurredAt);
    }

    private VotingExecutionPackageRow toRow(VotingExecutionPackage domain) {
        VotingExecutionPackageRow row = new VotingExecutionPackageRow();
        row.setPackageId(domain.getPackageId());
        row.setTenantId(domain.getTenantId());
        row.setBusinessType(domain.getBusinessType().name());
        row.setBusinessReferenceId(domain.getBusinessReferenceId());
        row.setProposalSnapshotType(domain.getProposalSnapshotType());
        row.setProposalSnapshotId(domain.getProposalSnapshotId());
        row.setProposalSnapshotHash(domain.getProposalSnapshotHash());
        row.setRuleSnapshotType(domain.getRuleSnapshotType());
        row.setRuleSnapshotId(domain.getRuleSnapshotId());
        row.setRuleSnapshotHash(domain.getRuleSnapshotHash());
        row.setScope(domain.getScope().getDbValue());
        row.setScopeReferenceId(domain.getScopeReferenceId());
        row.setCollectionMode(domain.getCollectionMode().name());
        row.setDuplicateBallotPolicy(domain.getDuplicateBallotPolicy().name());
        row.setStatus(domain.getStatus().name());
        row.setVoteStartAt(domain.getVoteStartAt());
        row.setVoteEndAt(domain.getVoteEndAt());
        row.setPackageHash(domain.getPackageHash());
        row.setElectorateSnapshotId(domain.getElectorateSnapshotId());
        row.setCreatedByUserId(domain.getCreatedByUserId());
        row.setFrozenByUserId(domain.getFrozenByUserId());
        row.setFrozenAt(domain.getFrozenAt());
        row.setVersion(domain.getVersion());
        return row;
    }

    private VotingBallotRecord toBallotRecord(VotingBallotRecordRow row) {
        return new VotingBallotRecord(
                row.getBallotId(), row.getPackageId(), row.getSubjectId(), row.getVoteId(),
                row.getElectorateItemId(), row.getTenantId(), row.getRepresentativeOpid(),
                row.getRepresentativeUid(), VoteChannel.fromDbValue(row.getVoteChannel()),
                row.getPackageHash(), row.getBallotFileHash(), row.getSignatureHash(),
                row.getRecordedByUserId(), row.getCastAt(), row.getSupersedesBallotId(),
                row.getResolutionPolicy() == null ? null
                        : VotingExecutionPackage.DuplicateBallotPolicy.valueOf(row.getResolutionPolicy()),
                row.getResolutionReason());
    }

    private VotingNonResponseDerivationRow toRow(VotingNonResponseDerivation domain) {
        VotingNonResponseDerivationRow row = new VotingNonResponseDerivationRow();
        row.setDerivationId(domain.derivationId());
        row.setPackageId(domain.packageId());
        row.setSubjectId(domain.subjectId());
        row.setElectorateItemId(domain.electorateItemId());
        row.setTenantId(domain.tenantId());
        row.setRepresentativeOpid(domain.opid());
        row.setRepresentativeUid(domain.uid());
        row.setCertifiedArea(domain.propertyArea());
        row.setNonResponsePolicy(domain.policy().name());
        row.setDerivedChoice(domain.derivedChoice().getDbValue());
        row.setDeliveryEvidenceHash(domain.deliveryEvidenceHash());
        row.setRuleSnapshotHash(domain.ruleSnapshotHash());
        row.setReasonCode(domain.reasonCode());
        row.setRowHash(domain.rowHash());
        row.setDerivedAt(domain.derivedAt());
        return row;
    }

    private VotingNonResponseDerivation toDomain(VotingNonResponseDerivationRow row) {
        return new VotingNonResponseDerivation(
                row.getDerivationId(), row.getPackageId(), row.getSubjectId(), row.getElectorateItemId(),
                row.getTenantId(), row.getRepresentativeOpid(), row.getRepresentativeUid(),
                row.getCertifiedArea(), VotingNonResponsePolicy.valueOf(row.getNonResponsePolicy()),
                VoteChoice.fromDbValue(row.getDerivedChoice()), row.getDeliveryEvidenceHash(),
                row.getRuleSnapshotHash(), row.getReasonCode(), row.getRowHash(), row.getDerivedAt());
    }

    private VotingExecutionPackage toDomain(VotingExecutionPackageRow row) {
        return VotingExecutionPackage.restore(
                row.getPackageId(), row.getTenantId(),
                VotingExecutionPackage.BusinessType.valueOf(row.getBusinessType()),
                row.getBusinessReferenceId(), row.getProposalSnapshotType(), row.getProposalSnapshotId(),
                row.getProposalSnapshotHash(), row.getRuleSnapshotType(), row.getRuleSnapshotId(),
                row.getRuleSnapshotHash(), VotingScope.fromDbValue(row.getScope()), row.getScopeReferenceId(),
                VotingExecutionPackage.CollectionMode.valueOf(row.getCollectionMode()),
                VotingExecutionPackage.DuplicateBallotPolicy.valueOf(row.getDuplicateBallotPolicy()),
                VotingExecutionPackage.Status.valueOf(row.getStatus()), row.getVoteStartAt(), row.getVoteEndAt(),
                row.getPackageHash(), row.getElectorateSnapshotId(), row.getCreatedByUserId(),
                row.getFrozenByUserId(), row.getFrozenAt(), row.getVersion());
    }

    private VotingElectorateSnapshot.Candidate toCandidate(VotingElectorateCandidateRow row) {
        return new VotingElectorateSnapshot.Candidate(
                row.getRosterId(), row.getRoomId(), row.getBuildingId(), row.getCertifiedArea(),
                row.getOpid(), row.getUid(), Integer.valueOf(1).equals(row.getVotingDelegate()));
    }

    private VotingElectorateSnapshotRow toRow(VotingElectorateSnapshot domain) {
        VotingElectorateSnapshotRow row = new VotingElectorateSnapshotRow();
        row.setSnapshotId(domain.snapshotId());
        row.setPackageId(domain.packageId());
        row.setTenantId(domain.tenantId());
        row.setScope(domain.scope().getDbValue());
        row.setScopeReferenceId(domain.scopeReferenceId());
        row.setTotalArea(domain.totalArea());
        row.setTotalOwnerCount(domain.totalOwnerCount());
        row.setItemCount(domain.itemCount());
        row.setAggregateHash(domain.aggregateHash());
        row.setFrozenAt(domain.frozenAt());
        return row;
    }

    private VotingElectorateItemRow toRow(VotingElectorateSnapshot.Item domain) {
        VotingElectorateItemRow row = new VotingElectorateItemRow();
        row.setSnapshotItemId(domain.snapshotItemId());
        row.setSnapshotId(domain.snapshotId());
        row.setRosterId(domain.rosterId());
        row.setRoomId(domain.roomId());
        row.setBuildingId(domain.buildingId());
        row.setCertifiedArea(domain.certifiedArea());
        row.setRepresentativeOpid(domain.representativeOpid());
        row.setRepresentativeUid(domain.representativeUid());
        row.setCoOwnerUidsJson(writeJson(domain.coOwnerUids()));
        row.setRowHash(domain.rowHash());
        return row;
    }

    private VotingElectorateSnapshot.Item toDomain(VotingElectorateItemRow row) {
        return new VotingElectorateSnapshot.Item(
                row.getSnapshotItemId(), row.getSnapshotId(), row.getRosterId(), row.getRoomId(),
                row.getBuildingId(), row.getCertifiedArea(), row.getRepresentativeOpid(),
                row.getRepresentativeUid(), readLongList(row.getCoOwnerUidsJson()), row.getRowHash());
    }

    private VotingElectorateSnapshot toDomain(VotingElectorateSnapshotRow row,
                                               List<VotingElectorateSnapshot.Item> items) {
        return new VotingElectorateSnapshot(
                row.getSnapshotId(), row.getPackageId(), row.getTenantId(),
                VotingScope.fromDbValue(row.getScope()), row.getScopeReferenceId(),
                row.getTotalArea(), row.getTotalOwnerCount(), row.getItemCount(),
                row.getAggregateHash(), row.getFrozenAt(), items);
    }

    private DenominatorItemRow toDenominatorItem(VotingElectorateSnapshot.Item item) {
        DenominatorItemRow row = new DenominatorItemRow();
        row.setRoomId(item.roomId());
        row.setBuildingId(item.buildingId());
        row.setCertifiedArea(item.certifiedArea());
        row.setPrimaryOwnerUid(item.representativeUid());
        row.setCoOwnerUids(item.coOwnerUids().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        row.setEligibilityFlag(1);
        row.setRowHash(item.rowHash());
        return row;
    }

    private VoteChannel normalizeDeliveryChannel(VoteChannel channel) {
        return channel != null && channel.paperLike() ? VoteChannel.PAPER : VoteChannel.ONLINE;
    }

    private String writeJson(List<Long> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化冻结名册共有人失败", ex);
        }
    }

    private List<Long> readLongList(String value) {
        try {
            return objectMapper.readValue(value, LONG_LIST);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("读取冻结名册共有人失败", ex);
        }
    }
}
