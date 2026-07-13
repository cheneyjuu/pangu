// 关联业务：向有权查看物业服务组织登记材料的管理身份签发短时私有预览地址。
package com.pangu.application.propertyservice;

import java.time.Instant;

/**
 * 物业服务组织材料预览票据。
 */
public record PropertyServiceOrganizationMaterialPreviewTicket(
        Long materialId,
        String originalFileName,
        String contentType,
        long fileSize,
        String previewUrl,
        Instant expiresAt
) {
}
