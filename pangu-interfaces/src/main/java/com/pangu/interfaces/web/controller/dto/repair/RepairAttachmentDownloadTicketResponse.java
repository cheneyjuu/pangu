package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairAttachmentDownloadTicket;

import java.time.Instant;

public record RepairAttachmentDownloadTicketResponse(
        Long attachmentId,
        String downloadUrl,
        Instant expiresAt
) {
    public static RepairAttachmentDownloadTicketResponse from(RepairAttachmentDownloadTicket ticket) {
        return new RepairAttachmentDownloadTicketResponse(
                ticket.attachmentId(), ticket.downloadUrl(), ticket.expiresAt());
    }
}
