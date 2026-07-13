// 关联业务：保存物业服务组织登记所附营业执照、服务合同和业主大会决议等私有材料。
package com.pangu.domain.model.propertyservice;

import java.time.Instant;

/**
 * 物业服务组织登记材料。
 */
public record PropertyServiceOrganizationMaterial(
        Long materialId,
        Long organizationId,
        PropertyServiceOrganizationMaterialType materialType,
        String objectKey,
        String originalFileName,
        String contentType,
        long fileSize,
        String etag,
        String sha256,
        Long uploadedByAccountId,
        String status,
        Instant createdAt) {
}
