package com.pangu.application.repair;

import java.time.Instant;
import java.util.List;

public record RepairAttachmentPreviewTicket(
        Long attachmentId,
        String originalFileName,
        String contentType,
        long actualSize,
        String previewUrl,
        List<String> pagePreviewUrls,
        boolean converted,
        Instant expiresAt
) {
}
