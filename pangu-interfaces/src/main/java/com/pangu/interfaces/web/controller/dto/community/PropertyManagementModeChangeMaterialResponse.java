// 关联业务：向管理端返回物业管理模式变更私有材料元数据，不暴露对象存储键。
package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.domain.model.community.PropertyManagementModeChangeMaterial;

import java.time.Instant;

/**
 * 物业管理模式变更材料响应。
 */
public record PropertyManagementModeChangeMaterialResponse(
        Long materialId,
        String materialType,
        String originalFileName,
        String contentType,
        long fileSize,
        String sha256,
        Long uploadedByAccountId,
        Instant createdAt
) {
    public static PropertyManagementModeChangeMaterialResponse from(PropertyManagementModeChangeMaterial material) {
        return new PropertyManagementModeChangeMaterialResponse(
                material.materialId(), material.materialType().name(), material.originalFileName(),
                material.contentType(), material.fileSize(), material.sha256(), material.uploadedByAccountId(),
                material.createdAt());
    }
}
