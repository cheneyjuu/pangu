package com.pangu.interfaces.web.controller.dto.owner;

import jakarta.validation.constraints.Size;

public record IdCardOcrRequest(
        @Size(max = 14_000_000, message = "imageBase64 is too large")
        String imageBase64,

        @Size(max = 2048, message = "imageUrl is too long")
        String imageUrl,

        @Size(max = 16, message = "cardSide is too long")
        String cardSide
) {
}
