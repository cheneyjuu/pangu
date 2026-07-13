// 关联业务：返回物业服务组织登记私有材料的短时预览票据。
package com.pangu.interfaces.web.controller.dto.propertyservice;

import com.pangu.application.propertyservice.PropertyServiceOrganizationMaterialPreviewTicket;

import java.time.Instant;

/**
 * 物业服务组织材料预览响应。
 */
public record PropertyServiceOrganizationMaterialPreviewResponse(
        Long materialId,
        String originalFileName,
        String contentType,
        long fileSize,
        String previewUrl,
        Instant expiresAt
) {
    public static PropertyServiceOrganizationMaterialPreviewResponse from(
            PropertyServiceOrganizationMaterialPreviewTicket ticket) {
        return new PropertyServiceOrganizationMaterialPreviewResponse(
                ticket.materialId(), ticket.originalFileName(), ticket.contentType(), ticket.fileSize(),
                ticket.previewUrl(), ticket.expiresAt());
    }
}
