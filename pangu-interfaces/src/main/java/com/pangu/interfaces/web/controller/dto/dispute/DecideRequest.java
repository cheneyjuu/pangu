package com.pangu.interfaces.web.controller.dto.dispute;

import com.pangu.domain.model.dispute.DecisionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 行政机关出具决议请求体（G 端）。 */
public record DecideRequest(
        @NotNull DecisionKind decisionKind,
        @NotBlank @Size(max = 2000) String content,
        @Size(max = 500) String docUrl
) {
}
