// 关联业务：描述维修工单附件在对象存储中的完整性、绑定状态和审计归属。
package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

public record RepairAttachment(
        Long attachmentId,
        Long workOrderId,
        Long tenantId,
        RepairAttachmentKind kind,
        String objectKey,
        String originalFileName,
        String contentType,
        long declaredSize,
        Long actualSize,
        String etag,
        RepairAttachmentStatus status,
        Long uploadedByAccountId,
        String boundAction,
        LocalDateTime createTime,
        LocalDateTime confirmedAt
) {
}
