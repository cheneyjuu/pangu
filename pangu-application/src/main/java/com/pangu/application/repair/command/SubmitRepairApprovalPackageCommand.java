package com.pangu.application.repair.command;

import java.util.List;

public record SubmitRepairApprovalPackageCommand(
        Long officialDocumentAttachmentId,
        List<Long> solitaireScreenshotAttachmentIds,
        String remark
) {
}
