// 关联业务：实现维修工单附件台账及业务绑定状态的持久化访问。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.repair.RepairAttachment;
import com.pangu.domain.model.repair.RepairAttachmentKind;
import com.pangu.domain.model.repair.RepairAttachmentStatus;
import com.pangu.domain.repository.RepairAttachmentRepository;
import com.pangu.infrastructure.persistence.entity.RepairAttachmentRow;
import com.pangu.infrastructure.persistence.mapper.RepairAttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepairAttachmentRepositoryImpl implements RepairAttachmentRepository {

    private final RepairAttachmentMapper mapper;

    @Override
    public RepairAttachment insert(RepairAttachment attachment) {
        RepairAttachmentRow row = toRow(attachment);
        mapper.insert(row);
        return findById(row.getAttachmentId(), row.getWorkOrderId(), row.getTenantId()).orElseThrow();
    }

    @Override
    public Optional<RepairAttachment> findById(Long attachmentId, Long workOrderId, Long tenantId) {
        return Optional.ofNullable(mapper.findById(attachmentId, workOrderId, tenantId)).map(this::toDomain);
    }

    @Override
    public List<RepairAttachment> findByIds(Collection<Long> attachmentIds, Long workOrderId, Long tenantId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        return mapper.findByIds(attachmentIds, workOrderId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<RepairAttachment> findLatestByKind(
            Long workOrderId, Long tenantId, RepairAttachmentKind kind) {
        return Optional.ofNullable(mapper.findLatestByKind(workOrderId, tenantId, kind.name()))
                .map(this::toDomain);
    }

    @Override
    public int countActive(Long workOrderId, Long tenantId, RepairAttachmentKind kind) {
        return mapper.countActive(workOrderId, tenantId, kind.name());
    }

    @Override
    public int markBound(Collection<Long> attachmentIds, Long workOrderId, Long tenantId, String boundAction) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return 0;
        }
        return mapper.markBound(attachmentIds, workOrderId, tenantId, boundAction);
    }

    @Override
    public int deleteUnbound(Long attachmentId, Long workOrderId, Long tenantId) {
        return mapper.deleteUnbound(attachmentId, workOrderId, tenantId);
    }

    private RepairAttachmentRow toRow(RepairAttachment attachment) {
        RepairAttachmentRow row = new RepairAttachmentRow();
        row.setAttachmentId(attachment.attachmentId());
        row.setWorkOrderId(attachment.workOrderId());
        row.setTenantId(attachment.tenantId());
        row.setAttachmentKind(attachment.kind().name());
        row.setObjectKey(attachment.objectKey());
        row.setOriginalFileName(attachment.originalFileName());
        row.setContentType(attachment.contentType());
        row.setDeclaredSize(attachment.declaredSize());
        row.setActualSize(attachment.actualSize());
        row.setEtag(attachment.etag());
        row.setStatus(attachment.status().name());
        row.setUploadedByAccountId(attachment.uploadedByAccountId());
        row.setBoundAction(attachment.boundAction());
        row.setCreateTime(attachment.createTime());
        row.setConfirmedAt(attachment.confirmedAt());
        return row;
    }

    private RepairAttachment toDomain(RepairAttachmentRow row) {
        return new RepairAttachment(
                row.getAttachmentId(), row.getWorkOrderId(), row.getTenantId(),
                RepairAttachmentKind.valueOf(row.getAttachmentKind()), row.getObjectKey(),
                row.getOriginalFileName(), row.getContentType(), row.getDeclaredSize(),
                row.getActualSize(), row.getEtag(), RepairAttachmentStatus.valueOf(row.getStatus()),
                row.getUploadedByAccountId(), row.getBoundAction(), row.getCreateTime(), row.getConfirmedAt());
    }
}
