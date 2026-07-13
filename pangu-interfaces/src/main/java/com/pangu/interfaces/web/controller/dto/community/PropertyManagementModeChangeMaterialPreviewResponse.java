// 关联业务：返回物业管理模式变更私有材料的短时预览票据。
package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.PropertyManagementModeChangeMaterialPreviewTicket;

import java.time.Instant;

/**
 * 物业管理模式变更材料预览响应。
 */
public record PropertyManagementModeChangeMaterialPreviewResponse(
        Long materialId,
        String originalFileName,
        String contentType,
        long fileSize,
        String previewUrl,
        Instant expiresAt
) {
    public static PropertyManagementModeChangeMaterialPreviewResponse from(
            PropertyManagementModeChangeMaterialPreviewTicket ticket) {
        return new PropertyManagementModeChangeMaterialPreviewResponse(
                ticket.materialId(), ticket.originalFileName(), ticket.contentType(), ticket.fileSize(),
                ticket.previewUrl(), ticket.expiresAt());
    }
}
