package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitRepairApprovalPackageRequest(
        @NotNull @Positive Long officialDocumentAttachmentId,
        List<@NotNull @Positive Long> solitaireScreenshotAttachmentIds,
        @Size(max = 500) String remark
) {
}
