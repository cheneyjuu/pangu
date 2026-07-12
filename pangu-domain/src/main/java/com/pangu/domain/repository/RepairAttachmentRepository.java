// 关联业务：持久化维修工单各阶段附件及其业务绑定状态。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.RepairAttachment;
import com.pangu.domain.model.repair.RepairAttachmentKind;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepairAttachmentRepository {

    RepairAttachment insert(RepairAttachment attachment);

    Optional<RepairAttachment> findById(Long attachmentId, Long workOrderId, Long tenantId);

    List<RepairAttachment> findByIds(Collection<Long> attachmentIds, Long workOrderId, Long tenantId);

    Optional<RepairAttachment> findLatestByKind(
            Long workOrderId, Long tenantId, RepairAttachmentKind kind);

    int countActive(Long workOrderId, Long tenantId, RepairAttachmentKind kind);

    int markBound(Collection<Long> attachmentIds, Long workOrderId, Long tenantId, String boundAction);

    int deleteUnbound(Long attachmentId, Long workOrderId, Long tenantId);
}
