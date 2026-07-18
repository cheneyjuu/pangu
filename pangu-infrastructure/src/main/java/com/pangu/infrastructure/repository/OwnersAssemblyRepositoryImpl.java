// 关联业务：实现业主大会会议、表决安排、材料及冻结议事规则快照的持久化边界。
package com.pangu.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.assembly.OwnersAssemblyDeliveryRecord;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleSnapshot;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblySubjectDraft;
import com.pangu.domain.model.assembly.OwnersAssemblyVoteRecord;
import com.pangu.domain.repository.OwnersAssemblyRepository;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyDeliveryRecordRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyMaterialRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyPackageRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyRuleSnapshotRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblySessionRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblySubjectDraftRow;
import com.pangu.infrastructure.persistence.entity.OwnersAssemblyVoteRecordRow;
import com.pangu.infrastructure.persistence.mapper.OwnersAssemblyMapper;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
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
    private final ObjectMapper objectMapper;

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
    public Optional<OwnersAssemblySession> findSessionForUpdate(Long sessionId, Long tenantId) {
        return Optional.ofNullable(mapper.findSessionForUpdate(sessionId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<OwnersAssemblySession> listSessions(Long tenantId) {
        return mapper.listSessions(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public int updateSessionStatus(Long sessionId, Long tenantId, String status) {
        return mapper.updateSessionStatus(sessionId, tenantId, status);
    }

    @Override
    public OwnersAssemblyPackage insertPackage(OwnersAssemblyPackage ballotPackage) {
        OwnersAssemblyPackageRow row = toRow(ballotPackage);
        mapper.insertPackage(row);
        return findPackage(row.getPackageId(), ballotPackage.tenantId()).orElseThrow();
    }

    @Override
    public OwnersAssemblyRuleSnapshot insertRuleSnapshot(OwnersAssemblyRuleSnapshot ruleSnapshot) {
        OwnersAssemblyRuleSnapshotRow row = toRow(ruleSnapshot);
        mapper.insertRuleSnapshot(row);
        return findRuleSnapshot(row.getRuleSnapshotId(), ruleSnapshot.tenantId()).orElseThrow();
    }

    @Override
    public Optional<OwnersAssemblyRuleSnapshot> findRuleSnapshotBySession(Long sessionId, Long tenantId) {
        return Optional.ofNullable(mapper.findRuleSnapshotBySession(sessionId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<OwnersAssemblyRuleSnapshot> findRuleSnapshot(Long ruleSnapshotId, Long tenantId) {
        return Optional.ofNullable(mapper.findRuleSnapshot(ruleSnapshotId, tenantId)).map(this::toDomain);
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
    public Optional<OwnersAssemblyPackage> findLatestPackageBySession(Long sessionId, Long tenantId) {
        return Optional.ofNullable(mapper.findLatestPackageBySession(sessionId, tenantId)).map(this::toDomain);
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
    public OwnersAssemblySubjectDraft insertSubjectDraft(OwnersAssemblySubjectDraft draft) {
        OwnersAssemblySubjectDraftRow row = toRow(draft);
        mapper.insertSubjectDraft(row);
        return new OwnersAssemblySubjectDraft(
                row.getDraftId(), draft.sessionId(), draft.tenantId(), draft.subjectType(), draft.scope(),
                draft.scopeReferenceId(), draft.title(), draft.content(),
                draft.proposedByUserId(), draft.createTime());
    }

    @Override
    public List<OwnersAssemblySubjectDraft> listSubjectDrafts(Long sessionId, Long tenantId) {
        return mapper.listSubjectDrafts(sessionId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public OwnersAssemblyMaterial insertMaterial(OwnersAssemblyMaterial material) {
        OwnersAssemblyMaterialRow row = toRow(material);
        mapper.insertMaterial(row);
        return new OwnersAssemblyMaterial(
                row.getMaterialId(), material.sessionId(), material.tenantId(), material.materialType(),
                material.objectKey(), material.originalFileName(), material.contentType(), material.fileSize(),
                material.etag(), material.contentSha256(), material.uploadedByAccountId(),
                material.uploadedByUserId(), material.createTime());
    }

    @Override
    public Optional<OwnersAssemblyMaterial> findMaterial(Long materialId, Long sessionId, Long tenantId) {
        return Optional.ofNullable(mapper.findMaterial(materialId, sessionId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<OwnersAssemblyMaterial> listMaterials(Long sessionId, Long tenantId) {
        return mapper.listMaterials(sessionId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<OwnersAssemblyMaterial> listPackageMaterials(Long packageId, Long tenantId) {
        return mapper.listPackageMaterials(packageId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public void linkPackageMaterial(Long packageId, Long tenantId, Long materialId) {
        if (mapper.insertPackageMaterial(packageId, tenantId, materialId) != 1) {
            throw new IllegalStateException("业主大会表决包材料不属于该会次或租户");
        }
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
    public Optional<Instant> findOwnerParticipationAt(Long packageId, Long tenantId, Long uid) {
        return Optional.ofNullable(mapper.findOwnerParticipationAt(packageId, tenantId, uid))
                .map(this::toInstant);
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

    private OwnersAssemblySubjectDraft toDomain(OwnersAssemblySubjectDraftRow row) {
        return new OwnersAssemblySubjectDraft(
                row.getDraftId(), row.getSessionId(), row.getTenantId(),
                SubjectType.valueOf(row.getSubjectType()), VotingScope.valueOf(row.getScope()),
                row.getScopeReferenceId(), row.getTitle(), row.getContent(),
                row.getProposedByUserId(), toInstant(row.getCreateTime()));
    }

    private OwnersAssemblyMaterial toDomain(OwnersAssemblyMaterialRow row) {
        return new OwnersAssemblyMaterial(
                row.getMaterialId(), row.getSessionId(), row.getTenantId(),
                OwnersAssemblyMaterial.MaterialType.valueOf(row.getMaterialType()), row.getObjectKey(),
                row.getOriginalFileName(), row.getContentType(), row.getFileSize(), row.getEtag(),
                row.getContentSha256(), row.getUploadedByAccountId(), row.getUploadedByUserId(),
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

    private OwnersAssemblySubjectDraftRow toRow(OwnersAssemblySubjectDraft domain) {
        OwnersAssemblySubjectDraftRow row = new OwnersAssemblySubjectDraftRow();
        row.setDraftId(domain.draftId());
        row.setSessionId(domain.sessionId());
        row.setTenantId(domain.tenantId());
        row.setSubjectType(domain.subjectType().name());
        row.setScope(domain.scope().name());
        row.setScopeReferenceId(domain.scopeReferenceId());
        row.setTitle(domain.title());
        row.setContent(domain.content());
        row.setProposedByUserId(domain.proposedByUserId());
        return row;
    }

    private OwnersAssemblyMaterialRow toRow(OwnersAssemblyMaterial domain) {
        OwnersAssemblyMaterialRow row = new OwnersAssemblyMaterialRow();
        row.setMaterialId(domain.materialId());
        row.setSessionId(domain.sessionId());
        row.setTenantId(domain.tenantId());
        row.setMaterialType(domain.materialType().name());
        row.setObjectKey(domain.objectKey());
        row.setOriginalFileName(domain.originalFileName());
        row.setContentType(domain.contentType());
        row.setFileSize(domain.fileSize());
        row.setEtag(domain.etag());
        row.setContentSha256(domain.contentSha256());
        row.setUploadedByAccountId(domain.uploadedByAccountId());
        row.setUploadedByUserId(domain.uploadedByUserId());
        return row;
    }

    private OwnersAssemblyPackage toDomain(OwnersAssemblyPackageRow row) {
        return new OwnersAssemblyPackage(
                row.getPackageId(),
                row.getSessionId(),
                row.getTenantId(),
                row.getRuleSnapshotId(),
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
        row.setRuleSnapshotId(domain.ruleSnapshotId());
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

    private OwnersAssemblyRuleSnapshot toDomain(OwnersAssemblyRuleSnapshotRow row) {
        return new OwnersAssemblyRuleSnapshot(
                row.getRuleSnapshotId(),
                row.getSessionId(),
                row.getTenantId(),
                row.getRuleId(),
                row.getRuleName(),
                row.getRuleVersion(),
                row.getEffectiveDate(),
                row.getSourceFileName(),
                row.getSourceSha256(),
                configurationFromJson(row.getConfigurationJson()),
                row.getConfigurationSha256(),
                row.getSnapshottedByAccountId(),
                row.getSnapshottedByUserId(),
                toInstant(row.getCreateTime()));
    }

    private OwnersAssemblyRuleSnapshotRow toRow(OwnersAssemblyRuleSnapshot domain) {
        OwnersAssemblyRuleSnapshotRow row = new OwnersAssemblyRuleSnapshotRow();
        row.setRuleSnapshotId(domain.ruleSnapshotId());
        row.setSessionId(domain.sessionId());
        row.setTenantId(domain.tenantId());
        row.setRuleId(domain.ruleId());
        row.setRuleName(domain.ruleName());
        row.setRuleVersion(domain.ruleVersion());
        row.setEffectiveDate(domain.effectiveDate());
        row.setSourceFileName(domain.sourceFileName());
        row.setSourceSha256(domain.sourceSha256());
        row.setConfigurationJson(configurationToJson(domain.configuration()));
        row.setConfigurationSha256(domain.configurationSha256());
        row.setSnapshottedByAccountId(domain.snapshottedByAccountId());
        row.setSnapshottedByUserId(domain.snapshottedByUserId());
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

    private OwnersAssemblyRuleConfiguration configurationFromJson(String json) {
        try {
            return objectMapper.readValue(json, OwnersAssemblyRuleConfiguration.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("业主大会议事规则快照配置数据无法读取", ex);
        }
    }

    private String configurationToJson(OwnersAssemblyRuleConfiguration configuration) {
        try {
            return objectMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("业主大会议事规则快照配置数据无法保存", ex);
        }
    }
}
