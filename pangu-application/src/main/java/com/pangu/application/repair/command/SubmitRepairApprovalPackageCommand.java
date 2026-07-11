package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairApprovalAttachment;

import java.util.List;

public record SubmitRepairApprovalPackageCommand(
        String officialDocumentHash,
        String mergedPackageHash,
        boolean printedAndAttached,
        List<RepairApprovalAttachment> attachments,
        String remark
) {
}
