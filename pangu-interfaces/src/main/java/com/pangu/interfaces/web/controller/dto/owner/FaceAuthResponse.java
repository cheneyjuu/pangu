package com.pangu.interfaces.web.controller.dto.owner;

public record FaceAuthResponse(
        boolean verified,
        String attestationId,
        int newAuthLevel,
        boolean testOnly,
        String message
) {
}
