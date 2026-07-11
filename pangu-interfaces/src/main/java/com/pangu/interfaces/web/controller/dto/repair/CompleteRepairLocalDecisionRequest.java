package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompleteRepairLocalDecisionRequest(
        @NotEmpty List<@Valid Entry> entries,
        @NotBlank @Size(max = 128) String evidenceAttachmentHash,
        @Size(max = 500) String remark
) {
    public record Entry(
            @NotNull Long roomId,
            Long ownerUid,
            @NotBlank @Size(max = 24) String choice,
            @Size(max = 1000) String originalText
    ) {
    }
}
