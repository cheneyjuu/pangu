package com.pangu.infrastructure.repository;

import com.pangu.domain.model.assembly.OwnersAssemblyDeliveryRecord;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblyVoteRecord;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyDeliveryRecordRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyPackageRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblySessionRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyVoteRecordRow;
import com.pangu.infrastructure.persistence.mapper.OwnersAssemblyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OwnersAssemblyRepositoryImpl implements OwnersAssemblyRepository {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final OwnersAssemblyMapper mapper;

    @Override
    public OwnersAssemblySession insertSession(OwnersAssemblySession session) {
        OwnersAssemblySessionRow row = toRow(session);
        mapper.insertSession(row);
        return findSession(row.getSessionId(), session.tenantId()).orElseThrow();
    }

    @Override
    public Optional<OwnersAssemblySession> findSession(Long sessionId, Long tenantId) {
        return Optional.ofNullable(mapper.findSession(sessionId, tenantId)).map(this::toDomain);
    }

    @Override
    public OwnersAssemblyPackage insertPackage(OwnersAssemblyPackage ballotPackage) {
        OwnersAssemblyPackageRow row = toRow(ballotPackage);
        mapper.insertPackage(row);
        return findPackage(row.getPackageId(), ballotPackage.tenantId()).orElseThrow();
    }

    @Override
    public Optional<OwnersAssemblyPackage> findPackage(Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.findPackage(packageId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<OwnersAssemblyPackage> findPackageForUpdate(Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.findPackageForUpdate(packageId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<OwnersAssemblyPackage> findPackageBySubjectId(Long subjectId) {
        return Optional.ofNullable(mapper.findPackageBySubjectId(subjectId)).map(this::toDomain);
    }

    @Override
    public void linkSubject(Long packageId, Long tenantId, Long subjectId) {
        mapper.insertSubjectLink(packageId, tenantId, subjectId);
    }

    @Override
    public List<Long> listSubjectIds(Long packageId, Long tenantId) {
        return mapper.listSubjectIds(packageId, tenantId);
    }

    @Override
    public int lockPackage(Long packageId,
                           Long tenantId,
                           String packageHash,
                           Instant publicNoticeStartAt,
                           Instant publicNoticeEndAt,
                           Long lockedByUserId) {
        return mapper.lockPackage(packageId, tenantId, packageHash,
                toLocal(publicNoticeStartAt), toLocal(publicNoticeEndAt), lockedByUserId);
    }

    @Override
    public int markPackageVoting(Long packageId, Long tenantId) {
        return mapper.markPackageVoting(packageId, tenantId);
    }

    @Override
    public int markPackageSettled(Long packageId, Long tenantId) {
        return mapper.markPackageSettled(packageId, tenantId);
    }

    @Override
    public OwnersAssemblyDeliveryRecord insertDelivery(OwnersAssemblyDeliveryRecord delivery) {
        OwnersAssemblyDeliveryRecordRow row = toRow(delivery);
        mapper.insertDelivery(row);
        return new OwnersAssemblyDeliveryRecord(
                row.getDeliveryId(),
                delivery.packageId(),
                delivery.tenantId(),
                delivery.opid(),
                delivery.uid(),
                delivery.deliveryChannel(),
                delivery.deliveryMethod(),
                delivery.evidenceHash(),
                delivery.deliveredByUserId(),
                delivery.deliveredAt());
    }

    @Override
    public boolean deliveryExists(Long packageId, Long tenantId, Long opid, Long uid, String deliveryChannel) {
        return mapper.deliveryExists(packageId, tenantId, opid, uid, deliveryChannel);
    }

    @Override
    public OwnersAssemblyVoteRecord insertVoteRecord(OwnersAssemblyVoteRecord voteRecord) {
        OwnersAssemblyVoteRecordRow row = toRow(voteRecord);
        mapper.insertVoteRecord(row);
        return new OwnersAssemblyVoteRecord(
                row.getAssemblyVoteId(),
                voteRecord.packageId(),
                voteRecord.subjectId(),
                voteRecord.voteId(),
                voteRecord.tenantId(),
                voteRecord.opid(),
                voteRecord.uid(),
                voteRecord.voteChannel(),
                voteRecord.packageHash(),
                voteRecord.ballotFileHash(),
                voteRecord.signatureHash(),
                voteRecord.valid(),
                voteRecord.invalidatedByVoteId(),
                voteRecord.invalidReason(),
                voteRecord.createTime());
    }

    @Override
    public Optional<OwnersAssemblyVoteRecord> findActiveVoteRecord(Long subjectId, Long opid) {
        return Optional.ofNullable(mapper.findActiveVoteRecord(subjectId, opid)).map(this::toDomain);
    }

    @Override
    public int invalidateVoteRecordByVoteId(Long voteId, Long invalidatedByVoteId, String invalidReason) {
        return mapper.invalidateVoteRecordByVoteId(voteId, invalidatedByVoteId, invalidReason);
    }

    @Override
    public boolean allSubjectsPassed(Long packageId, Long tenantId) {
        return mapper.allSubjectsPassed(packageId, tenantId);
    }

    private OwnersAssemblySession toDomain(OwnersAssemblySessionRow row) {
        return new OwnersAssemblySession(
                row.getSessionId(),
                row.getTenantId(),
                row.getTitle(),
                row.getPreparationMode(),
                row.getStatus(),
                row.getCreatedByUserId(),
                toInstant(row.getCreateTime()));
    }

    private OwnersAssemblySessionRow toRow(OwnersAssemblySession domain) {
        OwnersAssemblySessionRow row = new OwnersAssemblySessionRow();
        row.setSessionId(domain.sessionId());
        row.setTenantId(domain.tenantId());
        row.setTitle(domain.title());
        row.setPreparationMode(domain.preparationMode());
        row.setStatus(domain.status());
        row.setCreatedByUserId(domain.createdByUserId());
        return row;
    }

    private OwnersAssemblyPackage toDomain(OwnersAssemblyPackageRow row) {
        return new OwnersAssemblyPackage(
                row.getPackageId(),
                row.getSessionId(),
                row.getTenantId(),
                row.getPackageVersion(),
                row.getStatus(),
                row.getVotingChannelPolicy(),
                row.getPublicNoticeDays(),
                row.getAnnouncementHash(),
                row.getAttachmentManifestHash(),
                row.getBallotTemplateHash(),
                row.getElectronicSealHash(),
                row.getPackageHash(),
                toInstant(row.getPublicNoticeStartAt()),
                toInstant(row.getPublicNoticeEndAt()),
                toInstant(row.getVoteStartAt()),
                toInstant(row.getVoteEndAt()),
                row.getLockedByUserId(),
                toInstant(row.getLockedAt()));
    }

    private OwnersAssemblyPackageRow toRow(OwnersAssemblyPackage domain) {
        OwnersAssemblyPackageRow row = new OwnersAssemblyPackageRow();
        row.setPackageId(domain.packageId());
        row.setSessionId(domain.sessionId());
        row.setTenantId(domain.tenantId());
        row.setPackageVersion(domain.packageVersion());
        row.setStatus(domain.status());
        row.setVotingChannelPolicy(domain.votingChannelPolicy());
        row.setPublicNoticeDays(domain.publicNoticeDays());
        row.setAnnouncementHash(domain.announcementHash());
        row.setAttachmentManifestHash(domain.attachmentManifestHash());
        row.setBallotTemplateHash(domain.ballotTemplateHash());
        row.setElectronicSealHash(domain.electronicSealHash());
        row.setPackageHash(domain.packageHash());
        row.setVoteStartAt(toLocal(domain.voteStartAt()));
        row.setVoteEndAt(toLocal(domain.voteEndAt()));
        row.setLockedByUserId(domain.lockedByUserId());
        return row;
    }

    private OwnersAssemblyDeliveryRecordRow toRow(OwnersAssemblyDeliveryRecord domain) {
        OwnersAssemblyDeliveryRecordRow row = new OwnersAssemblyDeliveryRecordRow();
        row.setDeliveryId(domain.deliveryId());
        row.setPackageId(domain.packageId());
        row.setTenantId(domain.tenantId());
        row.setOpid(domain.opid());
        row.setUid(domain.uid());
        row.setDeliveryChannel(domain.deliveryChannel());
        row.setDeliveryMethod(domain.deliveryMethod());
        row.setEvidenceHash(domain.evidenceHash());
        row.setDeliveredByUserId(domain.deliveredByUserId());
        row.setDeliveredAt(toLocal(domain.deliveredAt()));
        return row;
    }

    private OwnersAssemblyVoteRecord toDomain(OwnersAssemblyVoteRecordRow row) {
        return new OwnersAssemblyVoteRecord(
                row.getAssemblyVoteId(),
                row.getPackageId(),
                row.getSubjectId(),
                row.getVoteId(),
                row.getTenantId(),
                row.getOpid(),
                row.getUid(),
                row.getVoteChannel(),
                row.getPackageHash(),
                row.getBallotFileHash(),
                row.getSignatureHash(),
                row.getValidFlag() != null && row.getValidFlag() == 1,
                row.getInvalidatedByVoteId(),
                row.getInvalidReason(),
                toInstant(row.getCreateTime()));
    }

    private OwnersAssemblyVoteRecordRow toRow(OwnersAssemblyVoteRecord domain) {
        OwnersAssemblyVoteRecordRow row = new OwnersAssemblyVoteRecordRow();
        row.setAssemblyVoteId(domain.assemblyVoteId());
        row.setPackageId(domain.packageId());
        row.setSubjectId(domain.subjectId());
        row.setVoteId(domain.voteId());
        row.setTenantId(domain.tenantId());
        row.setOpid(domain.opid());
        row.setUid(domain.uid());
        row.setVoteChannel(domain.voteChannel());
        row.setPackageHash(domain.packageHash());
        row.setBallotFileHash(domain.ballotFileHash());
        row.setSignatureHash(domain.signatureHash());
        row.setValidFlag(domain.valid() ? 1 : 0);
        row.setInvalidatedByVoteId(domain.invalidatedByVoteId());
        row.setInvalidReason(domain.invalidReason());
        return row;
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toInstant();
    }

    private LocalDateTime toLocal(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZONE);
    }
}
