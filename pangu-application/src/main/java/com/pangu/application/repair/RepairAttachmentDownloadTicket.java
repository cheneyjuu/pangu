package com.pangu.application.repair;

import java.time.Instant;

public record RepairAttachmentDownloadTicket(
        Long attachmentId,
        String downloadUrl,
        Instant expiresAt
) {
}
