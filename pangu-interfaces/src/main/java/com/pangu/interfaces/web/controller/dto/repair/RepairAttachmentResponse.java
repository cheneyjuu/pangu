package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairAttachment;

public record RepairAttachmentResponse(
        Long attachmentId,
        String attachmentKind,
        String originalFileName,
        String contentType,
        Long actualSize,
        String etag,
        String status
) {
    public static RepairAttachmentResponse from(RepairAttachment attachment) {
        return new RepairAttachmentResponse(
                attachment.attachmentId(), attachment.kind().name(), attachment.originalFileName(),
                attachment.contentType(), attachment.actualSize(), attachment.etag(), attachment.status().name());
    }
}
