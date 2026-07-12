// 关联业务：返回小区注册材料短时预览票据。
package com.pangu.interfaces.web.controller.dto.registration;

import com.pangu.application.registration.CommunityRegistrationMaterialPreviewTicket;

import java.time.Instant;

/**
 * 注册材料预览响应。
 */
public record CommunityRegistrationMaterialPreviewResponse(
        Long materialId,
        String originalFileName,
        String contentType,
        long fileSize,
        String previewUrl,
        Instant expiresAt
) {
    public static CommunityRegistrationMaterialPreviewResponse from(
            CommunityRegistrationMaterialPreviewTicket ticket) {
        return new CommunityRegistrationMaterialPreviewResponse(
                ticket.materialId(), ticket.originalFileName(), ticket.contentType(), ticket.fileSize(),
                ticket.previewUrl(), ticket.expiresAt());
    }
}
