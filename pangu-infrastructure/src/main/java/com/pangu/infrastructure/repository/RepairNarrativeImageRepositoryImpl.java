// 关联业务：实现维修方案正文图片的持久化、绑定和过期草稿查询。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.repair.RepairNarrativeImage;
import com.pangu.domain.repository.RepairNarrativeImageRepository;
import com.pangu.infrastructure.persistence.entity.RepairNarrativeImageRow;
import com.pangu.infrastructure.persistence.mapper.RepairNarrativeImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepairNarrativeImageRepositoryImpl implements RepairNarrativeImageRepository {

    private final RepairNarrativeImageMapper mapper;

    @Override
    public RepairNarrativeImage insert(RepairNarrativeImage image) {
        RepairNarrativeImageRow row = toRow(image);
        mapper.insert(row);
        return toDomain(row);
    }

    @Override
    public Optional<RepairNarrativeImage> findById(Long imageId, Long tenantId) {
        return Optional.ofNullable(mapper.findById(imageId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<RepairNarrativeImage> findByIds(Collection<Long> imageIds, Long tenantId) {
        if (imageIds == null || imageIds.isEmpty()) {
            return List.of();
        }
        return mapper.findByIds(imageIds, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RepairNarrativeImage> findByPlanId(Long planId, Long tenantId) {
        return mapper.findByPlanId(planId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public int bindDraftImages(Collection<Long> imageIds, Long tenantId, Long uploadedByAccountId,
                               Long projectId, Long planId) {
        if (imageIds == null || imageIds.isEmpty()) {
            return 0;
        }
        return mapper.bindDraftImages(imageIds, tenantId, uploadedByAccountId, projectId, planId);
    }

    @Override
    public int deleteDraft(Long imageId, Long tenantId, Long uploadedByAccountId) {
        return mapper.deleteDraft(imageId, tenantId, uploadedByAccountId);
    }

    @Override
    public List<RepairNarrativeImage> listExpiredDrafts(LocalDateTime createdBefore, int limit) {
        return mapper.listExpiredDrafts(createdBefore, limit).stream().map(this::toDomain).toList();
    }

    @Override
    public int deleteExpiredDraft(Long imageId) {
        return mapper.deleteExpiredDraft(imageId);
    }

    private RepairNarrativeImage toDomain(RepairNarrativeImageRow row) {
        return new RepairNarrativeImage(
                row.getImageId(), row.getTenantId(), row.getProjectId(), row.getPlanId(),
                row.getObjectKey(), row.getOriginalFileName(), row.getContentType(), row.getFileSize(),
                row.getEtag(), row.getSha256(), row.getUploadedByAccountId(), row.getUploadedByUserId(),
                RepairNarrativeImage.Status.valueOf(row.getStatus()), row.getCreateTime(), row.getBoundAt());
    }

    private RepairNarrativeImageRow toRow(RepairNarrativeImage image) {
        RepairNarrativeImageRow row = new RepairNarrativeImageRow();
        row.setImageId(image.imageId());
        row.setTenantId(image.tenantId());
        row.setProjectId(image.projectId());
        row.setPlanId(image.planId());
        row.setObjectKey(image.objectKey());
        row.setOriginalFileName(image.originalFileName());
        row.setContentType(image.contentType());
        row.setFileSize(image.fileSize());
        row.setEtag(image.etag());
        row.setSha256(image.sha256());
        row.setUploadedByAccountId(image.uploadedByAccountId());
        row.setUploadedByUserId(image.uploadedByUserId());
        row.setStatus(image.status().name());
        row.setCreateTime(image.createTime());
        row.setBoundAt(image.boundAt());
        return row;
    }
}
