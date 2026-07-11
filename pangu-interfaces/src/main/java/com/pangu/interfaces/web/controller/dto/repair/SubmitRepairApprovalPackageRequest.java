package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitRepairApprovalPackageRequest(
        @NotBlank @Size(max = 128) String officialDocumentHash,
        @NotBlank @Size(max = 128) String mergedPackageHash,
        boolean printedAndAttached,
        @NotEmpty List<@Valid Attachment> attachments,
        @Size(max = 500) String remark
) {
    public record Attachment(
            @NotBlank @Size(max = 40) String attachmentType,
            @NotBlank @Size(max = 128) String attachmentHash,
            @Size(max = 255) String originalFileName,
            @Min(1) int sortOrder
    ) {
    }
}
