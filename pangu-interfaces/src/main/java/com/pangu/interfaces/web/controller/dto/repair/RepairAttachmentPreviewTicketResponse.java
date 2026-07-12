package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairAttachmentPreviewTicket;

import java.time.Instant;
import java.util.List;

public record RepairAttachmentPreviewTicketResponse(
        Long attachmentId,
        String originalFileName,
        String contentType,
        long actualSize,
        String previewUrl,
        List<String> pagePreviewUrls,
        boolean converted,
        Instant expiresAt
) {
    public static RepairAttachmentPreviewTicketResponse from(RepairAttachmentPreviewTicket ticket) {
        return new RepairAttachmentPreviewTicketResponse(
                ticket.attachmentId(), ticket.originalFileName(), ticket.contentType(),
                ticket.actualSize(), ticket.previewUrl(), ticket.pagePreviewUrls(),
                ticket.converted(), ticket.expiresAt());
    }
}
