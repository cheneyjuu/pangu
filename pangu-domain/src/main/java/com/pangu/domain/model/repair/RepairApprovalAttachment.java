package com.pangu.domain.model.repair;

/** 送审包内一份不可变附件。 */
public record RepairApprovalAttachment(
        String attachmentType,
        String attachmentHash,
        String originalFileName,
        int sortOrder
) {
}
