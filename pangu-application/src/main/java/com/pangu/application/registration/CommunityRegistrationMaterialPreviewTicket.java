// 关联业务：向有权查看注册申请的用户签发短时材料预览地址。
package com.pangu.application.registration;

import java.time.Instant;

/**
 * 注册材料预览票据。
 */
public record CommunityRegistrationMaterialPreviewTicket(
        Long materialId,
        String originalFileName,
        String contentType,
        long fileSize,
        String previewUrl,
        Instant expiresAt
) {
}
