// 关联业务：保存小区注册审核材料的私有对象元数据与审计归属。
package com.pangu.domain.model.registration;

import java.time.Instant;

/**
 * 小区注册审核材料。
 */
public record CommunityRegistrationMaterial(
        Long materialId,
        Long applicationId,
        CommunityRegistrationMaterialType materialType,
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
}
