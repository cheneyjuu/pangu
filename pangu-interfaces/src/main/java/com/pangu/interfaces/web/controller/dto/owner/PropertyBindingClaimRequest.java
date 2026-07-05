package com.pangu.interfaces.web.controller.dto.owner;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PropertyBindingClaimRequest(
        @NotNull(message = "rosterId must not be null")
        Long rosterId,
        Boolean jointOwnership,
        Boolean votingDelegate,
        String proofType,
        @Size(max = 3, message = "proofImagesBase64 size must be <= 3")
        List<@Size(max = 2_800_000, message = "single proof image is too large") String> proofImagesBase64
) {
}
