// 关联业务：为物业管理模式变更的私有证据材料生成限时预览凭证。
package com.pangu.application.community;

import java.time.Instant;

/**
 * 私有材料预览凭证。
 */
public record PropertyManagementModeChangeMaterialPreviewTicket(
        Long materialId,
        String originalFileName,
        String contentType,
        long fileSize,
        String previewUrl,
        Instant expiresAt
) {
}
