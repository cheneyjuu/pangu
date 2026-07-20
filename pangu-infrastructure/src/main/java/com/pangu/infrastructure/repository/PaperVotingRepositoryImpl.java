// 关联业务：以纸质渠道台账持久化送达核对、纸票回收、录入复核和逐事项最终结果。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.PaperBallot;
import com.pangu.domain.model.voting.PaperBallotEntry;
import com.pangu.domain.model.voting.PaperBallotOutcome;
import com.pangu.domain.model.voting.PaperVotingDelivery;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.repository.PaperVotingRepository;
import com.pangu.infrastructure.persistence.entity.PaperBallotEntryItemRow;
import com.pangu.infrastructure.persistence.entity.PaperBallotEntryRow;
import com.pangu.infrastructure.persistence.entity.PaperBallotOutcomeRow;
import com.pangu.infrastructure.persistence.entity.PaperBallotRow;
import com.pangu.infrastructure.persistence.entity.PaperVotingDeliveryRow;
import com.pangu.infrastructure.persistence.mapper.PaperVotingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaperVotingRepositoryImpl implements PaperVotingRepository {

    private final PaperVotingMapper mapper;

    @Override
    public PaperVotingDelivery insertDelivery(PaperVotingDelivery delivery) {
        PaperVotingDeliveryRow row = toRow(delivery);
        mapper.insertDelivery(row);
        return findDelivery(row.getPaperDeliveryId(), delivery.packageId(), delivery.tenantId()).orElseThrow();
    }

    @Override
    public Optional<PaperVotingDelivery> findDelivery(Long paperDeliveryId, Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.selectDelivery(paperDeliveryId, packageId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<PaperVotingDelivery> findDeliveryForUpdate(Long paperDeliveryId, Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.selectDeliveryForUpdate(paperDeliveryId, packageId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public int confirmDelivery(Long paperDeliveryId,
                               Long tenantId,
                               Long reviewedByUserId,
                               Instant reviewedAt,
                               Long unifiedDeliveryId,
                               Long expectedVersion) {
        return mapper.confirmDelivery(
                paperDeliveryId, tenantId, reviewedByUserId, reviewedAt, unifiedDeliveryId, expectedVersion);
    }

    @Override
    public int rejectDelivery(Long paperDeliveryId,
                              Long tenantId,
                              Long reviewedByUserId,
                              Instant reviewedAt,
                              String reviewNote,
                              Long expectedVersion) {
        return mapper.rejectDelivery(
                paperDeliveryId, tenantId, reviewedByUserId, reviewedAt, reviewNote, expectedVersion);
    }

    @Override
    public PaperBallot insertBallot(PaperBallot ballot) {
        PaperBallotRow row = toRow(ballot);
        mapper.insertBallot(row);
        return findBallot(row.getPaperBallotId(), ballot.packageId(), ballot.tenantId()).orElseThrow();
    }

    @Override
    public Optional<PaperBallot> findBallot(Long paperBallotId, Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.selectBallot(paperBallotId, packageId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<PaperBallot> findBallotForUpdate(Long paperBallotId, Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.selectBallotForUpdate(paperBallotId, packageId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public PaperBallotEntry insertEntry(PaperBallotEntry entry) {
        PaperBallotEntryRow row = toRow(entry);
        mapper.insertEntry(row);
        List<PaperBallotEntryItemRow> items = entry.items().stream().map(this::toRow).toList();
        mapper.insertEntryItems(row.getEntryId(), items);
        return findEntry(row.getEntryId(), entry.paperBallotId(), entry.tenantId()).orElseThrow();
    }

    @Override
    public Optional<PaperBallotEntry> findEntry(Long entryId, Long paperBallotId, Long tenantId) {
        return Optional.ofNullable(mapper.selectEntry(entryId, paperBallotId, tenantId))
                .map(row -> toDomain(row, mapper.selectEntryItems(row.getEntryId())));
    }

    @Override
    public Optional<PaperBallotEntry> findEntryForUpdate(Long entryId, Long paperBallotId, Long tenantId) {
        return Optional.ofNullable(mapper.selectEntryForUpdate(entryId, paperBallotId, tenantId))
                .map(row -> toDomain(row, mapper.selectEntryItems(row.getEntryId())));
    }

    @Override
    public Optional<PaperBallotEntry> findLatestEntry(Long paperBallotId, Long tenantId) {
        return Optional.ofNullable(mapper.selectLatestEntry(paperBallotId, tenantId))
                .map(row -> toDomain(row, mapper.selectEntryItems(row.getEntryId())));
    }

    @Override
    public int nextEntryVersion(Long paperBallotId, Long tenantId) {
        return mapper.selectNextEntryVersion(paperBallotId, tenantId);
    }

    @Override
    public int confirmEntry(Long entryId, Long tenantId, Long reviewedByUserId, Instant reviewedAt) {
        return mapper.confirmEntry(entryId, tenantId, reviewedByUserId, reviewedAt);
    }

    @Override
    public int rejectEntry(Long entryId,
                           Long tenantId,
                           Long reviewedByUserId,
                           Instant reviewedAt,
                           String reviewNote) {
        return mapper.rejectEntry(entryId, tenantId, reviewedByUserId, reviewedAt, reviewNote);
    }

    @Override
    public void insertOutcome(PaperBallotOutcome outcome) {
        PaperBallotOutcomeRow row = toRow(outcome);
        mapper.insertOutcome(row);
    }

    @Override
    public List<PaperBallotOutcome> listOutcomes(Long paperBallotId, Long tenantId) {
        return mapper.selectOutcomes(paperBallotId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public int markBallotInEntry(Long paperBallotId, Long tenantId, Long expectedVersion) {
        return mapper.markBallotInEntry(paperBallotId, tenantId, expectedVersion);
    }

    @Override
    public int markBallotCompleted(Long paperBallotId, Long tenantId) {
        return mapper.markBallotCompleted(paperBallotId, tenantId);
    }

    @Override
    public int voidBallot(Long paperBallotId,
                          Long tenantId,
                          Long voidedByUserId,
                          Instant voidedAt,
                          String voidReason,
                          Long expectedVersion) {
        return mapper.voidBallot(
                paperBallotId, tenantId, voidedByUserId, voidedAt, voidReason, expectedVersion);
    }

    @Override
    public List<PaperVotingDelivery> listDeliveries(Long packageId, Long tenantId) {
        return mapper.selectDeliveries(packageId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<PaperBallot> listBallots(Long packageId, Long tenantId) {
        return mapper.selectBallots(packageId, tenantId).stream().map(this::toDomain).toList();
    }

    private PaperVotingDeliveryRow toRow(PaperVotingDelivery domain) {
        PaperVotingDeliveryRow row = new PaperVotingDeliveryRow();
        row.setPaperDeliveryId(domain.paperDeliveryId());
        row.setPackageId(domain.packageId());
        row.setElectorateItemId(domain.electorateItemId());
        row.setTenantId(domain.tenantId());
        row.setProxyAuthorizationId(domain.proxyAuthorizationId());
        row.setRecipientName(domain.recipientName());
        row.setDeliveryMethod(domain.deliveryMethod());
        row.setEvidenceSourceType(domain.evidenceSourceType());
        row.setEvidenceSourceId(domain.evidenceSourceId());
        row.setEvidenceHash(domain.evidenceHash());
        row.setDeliveredByUserId(domain.deliveredByUserId());
        row.setDeliveredAt(domain.deliveredAt());
        row.setStatus(domain.status().name());
        return row;
    }

    private PaperVotingDelivery toDomain(PaperVotingDeliveryRow row) {
        return new PaperVotingDelivery(
                row.getPaperDeliveryId(), row.getPackageId(), row.getElectorateItemId(), row.getTenantId(),
                row.getOpid(), row.getProxyAuthorizationId(), row.getRecipientName(), row.getDeliveryMethod(),
                row.getEvidenceSourceType(), row.getEvidenceSourceId(), row.getEvidenceHash(),
                row.getDeliveredByUserId(), row.getDeliveredAt(),
                PaperVotingDelivery.Status.valueOf(row.getStatus()), row.getReviewedByUserId(), row.getReviewedAt(),
                row.getReviewNote(), row.getUnifiedDeliveryId(), row.getCreateTime(), row.getUpdateTime(), row.getVersion());
    }

    private PaperBallotRow toRow(PaperBallot domain) {
        PaperBallotRow row = new PaperBallotRow();
        row.setPaperBallotId(domain.paperBallotId());
        row.setPackageId(domain.packageId());
        row.setElectorateItemId(domain.electorateItemId());
        row.setTenantId(domain.tenantId());
        row.setProxyAuthorizationId(domain.proxyAuthorizationId());
        row.setBallotNumber(domain.ballotNumber());
        row.setTemplateHash(domain.templateHash());
        row.setMaterialSourceType(domain.materialSourceType());
        row.setMaterialSourceId(domain.materialSourceId());
        row.setMaterialHash(domain.materialHash());
        row.setReceivedByUserId(domain.receivedByUserId());
        row.setReceivedAt(domain.receivedAt());
        row.setStatus(domain.status().name());
        return row;
    }

    private PaperBallot toDomain(PaperBallotRow row) {
        return new PaperBallot(
                row.getPaperBallotId(), row.getPackageId(), row.getElectorateItemId(), row.getTenantId(), row.getOpid(),
                row.getProxyAuthorizationId(), row.getBallotNumber(), row.getTemplateHash(),
                row.getMaterialSourceType(), row.getMaterialSourceId(),
                row.getMaterialHash(), row.getReceivedByUserId(), row.getReceivedAt(),
                PaperBallot.Status.valueOf(row.getStatus()), row.getVoidedByUserId(), row.getVoidedAt(),
                row.getVoidReason(), row.getCreateTime(), row.getUpdateTime(), row.getVersion());
    }

    private PaperBallotEntryRow toRow(PaperBallotEntry domain) {
        PaperBallotEntryRow row = new PaperBallotEntryRow();
        row.setEntryId(domain.entryId());
        row.setPaperBallotId(domain.paperBallotId());
        row.setTenantId(domain.tenantId());
        row.setVersionNumber(domain.versionNumber());
        row.setStatus(domain.status().name());
        row.setEnteredByUserId(domain.enteredByUserId());
        row.setEnteredAt(domain.enteredAt());
        return row;
    }

    private PaperBallotEntryItemRow toRow(PaperBallotEntry.Item domain) {
        PaperBallotEntryItemRow row = new PaperBallotEntryItemRow();
        row.setEntryItemId(domain.entryItemId());
        row.setEntryId(domain.entryId());
        row.setSubjectId(domain.subjectId());
        row.setDetermination(domain.determination().name());
        row.setChoice(domain.choice() == null ? null : domain.choice().getDbValue());
        row.setInvalidReasonCode(domain.invalidReasonCode() == null ? null : domain.invalidReasonCode().name());
        row.setInvalidReasonDescription(domain.invalidReasonDescription());
        return row;
    }

    private PaperBallotEntry toDomain(PaperBallotEntryRow row, List<PaperBallotEntryItemRow> itemRows) {
        return new PaperBallotEntry(
                row.getEntryId(), row.getPaperBallotId(), row.getTenantId(), row.getVersionNumber(),
                PaperBallotEntry.Status.valueOf(row.getStatus()), row.getEnteredByUserId(), row.getEnteredAt(),
                row.getReviewedByUserId(), row.getReviewedAt(), row.getReviewNote(),
                itemRows.stream().map(this::toDomain).toList());
    }

    private PaperBallotEntry.Item toDomain(PaperBallotEntryItemRow row) {
        return new PaperBallotEntry.Item(
                row.getEntryItemId(), row.getEntryId(), row.getSubjectId(),
                PaperBallotEntry.Determination.valueOf(row.getDetermination()),
                row.getChoice() == null ? null : VoteChoice.fromDbValue(row.getChoice()),
                row.getInvalidReasonCode() == null ? null
                        : PaperBallotEntry.InvalidReasonCode.valueOf(row.getInvalidReasonCode()),
                row.getInvalidReasonDescription());
    }

    private PaperBallotOutcomeRow toRow(PaperBallotOutcome domain) {
        PaperBallotOutcomeRow row = new PaperBallotOutcomeRow();
        row.setOutcomeId(domain.outcomeId());
        row.setPaperBallotId(domain.paperBallotId());
        row.setEntryId(domain.entryId());
        row.setSubjectId(domain.subjectId());
        row.setStatus(domain.status().name());
        row.setUnifiedBallotId(domain.unifiedBallotId());
        row.setConflictingBallotId(domain.conflictingBallotId());
        row.setReason(domain.reason());
        row.setFinalizedAt(domain.finalizedAt());
        return row;
    }

    private PaperBallotOutcome toDomain(PaperBallotOutcomeRow row) {
        return new PaperBallotOutcome(
                row.getOutcomeId(), row.getPaperBallotId(), row.getEntryId(), row.getSubjectId(),
                PaperBallotOutcome.Status.valueOf(row.getStatus()), row.getUnifiedBallotId(),
                row.getConflictingBallotId(), row.getReason(), row.getFinalizedAt());
    }
}
