// 关联业务：向注册人和审核人展示材料元数据，不泄露私有 OSS 对象键。
package com.pangu.interfaces.web.controller.dto.registration;

import com.pangu.domain.model.registration.CommunityRegistrationMaterial;

import java.time.Instant;

/**
 * 小区注册材料响应。
 */
public record CommunityRegistrationMaterialResponse(
        Long materialId,
        String materialType,
        String originalFileName,
        String contentType,
        long fileSize,
        String sha256,
        Long uploadedByAccountId,
        Instant createdAt
) {
    public static CommunityRegistrationMaterialResponse from(CommunityRegistrationMaterial material) {
        return new CommunityRegistrationMaterialResponse(
                material.materialId(), material.materialType().name(), material.originalFileName(),
                material.contentType(), material.fileSize(), material.sha256(),
                material.uploadedByAccountId(), material.createdAt());
    }
}
