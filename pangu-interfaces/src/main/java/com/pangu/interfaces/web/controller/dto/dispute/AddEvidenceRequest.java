package com.pangu.interfaces.web.controller.dto.dispute;

import com.pangu.domain.model.dispute.EvidenceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 业主补充证据请求体（C 端）。 */
public record AddEvidenceRequest(
        @NotNull EvidenceKind evidenceKind,
        @NotBlank @Size(max = 500) String contentUrl,
        @Size(max = 500) String description
) {
}
