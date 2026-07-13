// 关联业务：向管理端返回物业服务组织登记材料元数据，不暴露私有对象键。
package com.pangu.interfaces.web.controller.dto.propertyservice;

import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationMaterial;

import java.time.Instant;

/**
 * 物业服务组织登记材料响应。
 */
public record PropertyServiceOrganizationMaterialResponse(
        Long materialId,
        String materialType,
        String originalFileName,
        String contentType,
        long fileSize,
        String sha256,
        Long uploadedByAccountId,
        Instant createdAt
) {
    public static PropertyServiceOrganizationMaterialResponse from(PropertyServiceOrganizationMaterial material) {
        return new PropertyServiceOrganizationMaterialResponse(
                material.materialId(), material.materialType().name(), material.originalFileName(),
                material.contentType(), material.fileSize(), material.sha256(),
                material.uploadedByAccountId(), material.createdAt());
    }
}
