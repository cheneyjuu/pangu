package com.pangu.application.repair.command;

public record UploadRepairAttachmentCommand(
        String attachmentKind,
        String originalFileName,
        String contentType,
        byte[] content
) {
}
