// 关联业务：保存物业管理模式变更申请的私有业主大会决议和辅助证据元数据。
package com.pangu.domain.model.community;

import java.time.Instant;

/**
 * 物业管理模式变更申请的私有材料。
 */
public record PropertyManagementModeChangeMaterial(
        Long materialId,
        Long requestId,
        PropertyManagementModeChangeMaterialType materialType,
        String objectKey,
        String originalFileName,
        String contentType,
        long fileSize,
        String etag,
        String sha256,
        Long uploadedByAccountId,
        String status,
        Instant createdAt
) {
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
