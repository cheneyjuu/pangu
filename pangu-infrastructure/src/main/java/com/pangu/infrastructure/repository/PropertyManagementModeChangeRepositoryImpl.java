// 关联业务：持久化物业管理模式变更申请、决议材料、审核审计并原子切换租户生效模式。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.community.PropertyManagementMode;
import com.pangu.domain.model.community.PropertyManagementModeChangeAudit;
import com.pangu.domain.model.community.PropertyManagementModeChangeMaterial;
import com.pangu.domain.model.community.PropertyManagementModeChangeMaterialType;
import com.pangu.domain.model.community.PropertyManagementModeChangeRequest;
import com.pangu.domain.model.community.PropertyManagementModeChangeStatus;
import com.pangu.domain.repository.PropertyManagementModeChangeRepository;
import com.pangu.infrastructure.persistence.mapper.PropertyManagementModeChangeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 物业管理模式变更仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class PropertyManagementModeChangeRepositoryImpl implements PropertyManagementModeChangeRepository {

    private final PropertyManagementModeChangeMapper mapper;

    @Override
    public List<PropertyManagementModeChangeRequest> listByTenant(Long tenantId) {
        return mapper.selectRequests(tenantId).stream().map(this::toRequest).toList();
    }

    @Override
    public Optional<PropertyManagementModeChangeRequest> findByTenantAndId(Long tenantId, Long requestId) {
        return Optional.ofNullable(mapper.selectRequest(tenantId, requestId)).map(this::toRequest);
    }

    @Override
    public Optional<PropertyManagementModeChangeRequest> findByTenantAndIdForUpdate(Long tenantId, Long requestId) {
        return Optional.ofNullable(mapper.selectRequestForUpdate(tenantId, requestId)).map(this::toRequest);
    }

    @Override
    public PropertyManagementModeChangeRequest insertRequest(PropertyManagementModeChangeRequest request) {
        PropertyManagementModeChangeMapper.RequestRow row = toRow(request);
        try {
            mapper.insertRequest(row);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateActiveRequestException("物业管理模式变更申请唯一约束冲突", ex);
        }
        return findByTenantAndId(request.tenantId(), row.getRequestId())
                .orElseThrow(() -> new IllegalStateException("物业管理模式变更申请创建后未能回读"));
    }

    @Override
    public int updateRequest(PropertyManagementModeChangeRequest request, int expectedVersion) {
        return mapper.updateRequest(toRow(request), expectedVersion);
    }

    @Override
    public PropertyManagementModeChangeMaterial insertMaterial(PropertyManagementModeChangeMaterial material) {
        PropertyManagementModeChangeMapper.MaterialRow row = toRow(material);
        mapper.insertMaterial(row);
        return toMaterial(row);
    }

    @Override
    public Optional<PropertyManagementModeChangeMaterial> findMaterial(Long requestId, Long materialId) {
        return Optional.ofNullable(mapper.selectMaterial(requestId, materialId)).map(this::toMaterial);
    }

    @Override
    public List<PropertyManagementModeChangeMaterial> listMaterials(Long requestId) {
        return mapper.selectMaterials(requestId).stream().map(this::toMaterial).toList();
    }

    @Override
    public int deactivateMaterial(Long requestId, Long materialId) {
        return mapper.deactivateMaterial(requestId, materialId);
    }

    @Override
    public long countActiveMaterialsByType(Long requestId, String materialType) {
        return mapper.countActiveMaterialsByType(requestId, materialType);
    }

    @Override
    public List<PropertyManagementModeChangeAudit> listAudits(Long requestId) {
        return mapper.selectAudits(requestId).stream().map(this::toAudit).toList();
    }

    @Override
    public void insertAudit(Long requestId,
                            Long actorAccountId,
                            Long actorUserId,
                            Long actorDeptId,
                            String eventType,
                            String fromStatus,
                            String toStatus,
                            String payloadJson) {
        mapper.insertAudit(requestId, actorAccountId, actorUserId, actorDeptId,
                eventType, fromStatus, toStatus, payloadJson);
    }

    @Override
    public int applyMode(Long tenantId,
                         PropertyManagementMode expectedCurrentMode,
                         PropertyManagementMode requestedMode,
                         String historyJson) {
        return mapper.applyMode(
                tenantId,
                expectedCurrentMode == null ? null : expectedCurrentMode.name(),
                requestedMode.name(),
                historyJson);
    }

    private PropertyManagementModeChangeRequest toRequest(PropertyManagementModeChangeMapper.RequestRow row) {
        return new PropertyManagementModeChangeRequest(
                row.getRequestId(), row.getTenantId(), parseMode(row.getCurrentPropertyMode()),
                PropertyManagementMode.valueOf(row.getRequestedPropertyMode()),
                row.getOwnersAssemblyResolutionReference(), row.getChangeReason(),
                PropertyManagementModeChangeStatus.valueOf(row.getStatus()),
                row.getApplicantAccountId(), row.getApplicantUserId(), row.getApplicantDeptId(),
                row.getSubmittedAt(), row.getReviewerAccountId(), row.getReviewerUserId(), row.getReviewerDeptId(),
                row.getReviewComment(), row.getReviewedAt(), row.getExecutedAt(),
                row.getVersion() == null ? 0 : row.getVersion(), row.getCreateTime(), row.getUpdateTime());
    }

    private PropertyManagementModeChangeMaterial toMaterial(PropertyManagementModeChangeMapper.MaterialRow row) {
        return new PropertyManagementModeChangeMaterial(
                row.getMaterialId(), row.getRequestId(),
                PropertyManagementModeChangeMaterialType.valueOf(row.getMaterialType()),
                row.getObjectKey(), row.getOriginalFileName(), row.getContentType(),
                row.getFileSize() == null ? 0 : row.getFileSize(), row.getEtag(), row.getSha256(),
                row.getUploadedByAccountId(), row.getStatus(), row.getCreateTime());
    }

    private PropertyManagementModeChangeAudit toAudit(PropertyManagementModeChangeMapper.AuditRow row) {
        return new PropertyManagementModeChangeAudit(
                row.getAuditId(), row.getRequestId(), row.getActorAccountId(), row.getActorUserId(),
                row.getActorDeptId(), row.getEventType(), parseStatus(row.getFromStatus()),
                parseStatus(row.getToStatus()), row.getPayloadJson(), row.getCreateTime());
    }

    private PropertyManagementModeChangeMapper.RequestRow toRow(PropertyManagementModeChangeRequest request) {
        PropertyManagementModeChangeMapper.RequestRow row = new PropertyManagementModeChangeMapper.RequestRow();
        row.setRequestId(request.requestId());
        row.setTenantId(request.tenantId());
        row.setCurrentPropertyMode(modeName(request.currentPropertyMode()));
        row.setRequestedPropertyMode(request.requestedPropertyMode().name());
        row.setOwnersAssemblyResolutionReference(request.ownersAssemblyResolutionReference());
        row.setChangeReason(request.changeReason());
        row.setStatus(request.status().name());
        row.setApplicantAccountId(request.applicantAccountId());
        row.setApplicantUserId(request.applicantUserId());
        row.setApplicantDeptId(request.applicantDeptId());
        row.setSubmittedAt(request.submittedAt());
        row.setReviewerAccountId(request.reviewerAccountId());
        row.setReviewerUserId(request.reviewerUserId());
        row.setReviewerDeptId(request.reviewerDeptId());
        row.setReviewComment(request.reviewComment());
        row.setReviewedAt(request.reviewedAt());
        row.setExecutedAt(request.executedAt());
        row.setVersion(request.version());
        row.setCreateTime(request.createdAt());
        row.setUpdateTime(request.updatedAt());
        return row;
    }

    private PropertyManagementModeChangeMapper.MaterialRow toRow(PropertyManagementModeChangeMaterial material) {
        PropertyManagementModeChangeMapper.MaterialRow row = new PropertyManagementModeChangeMapper.MaterialRow();
        row.setMaterialId(material.materialId());
        row.setRequestId(material.requestId());
        row.setMaterialType(material.materialType().name());
        row.setObjectKey(material.objectKey());
        row.setOriginalFileName(material.originalFileName());
        row.setContentType(material.contentType());
        row.setFileSize(material.fileSize());
        row.setEtag(material.etag());
        row.setSha256(material.sha256());
        row.setUploadedByAccountId(material.uploadedByAccountId());
        row.setStatus(material.status());
        row.setCreateTime(material.createdAt());
        return row;
    }

    private PropertyManagementMode parseMode(String value) {
        return value == null ? null : PropertyManagementMode.valueOf(value);
    }

    private PropertyManagementModeChangeStatus parseStatus(String value) {
        return value == null ? null : PropertyManagementModeChangeStatus.valueOf(value);
    }

    private String modeName(PropertyManagementMode mode) {
        return mode == null ? null : mode.name();
    }
}
