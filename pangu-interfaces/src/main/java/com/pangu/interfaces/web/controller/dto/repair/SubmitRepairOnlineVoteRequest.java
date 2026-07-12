package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitRepairOnlineVoteRequest(
        @NotNull Long opid,
        @NotBlank @Size(max = 16) String choice
) {
}
