package com.pangu.interfaces.web.controller;

import com.pangu.interfaces.web.controller.dto.owner.FaceAuthContextResponse;
import com.pangu.interfaces.web.controller.dto.owner.FaceAuthRequest;
import com.pangu.interfaces.web.controller.dto.owner.FaceAuthResponse;
import com.pangu.interfaces.web.service.OwnerFaceAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/auth")
@RequiredArgsConstructor
public class OwnerAuthController extends BaseController {

    private final OwnerFaceAuthService ownerFaceAuthService;

    @PostMapping("/face/context")
    @PreAuthorize("isAuthenticated()")
    public Result<FaceAuthContextResponse> prepareFaceAuthContext() {
        return success(ownerFaceAuthService.prepareContext());
    }

    @PostMapping("/face")
    @PreAuthorize("isAuthenticated()")
    public Result<FaceAuthResponse> submitFaceAuth(@Valid @RequestBody FaceAuthRequest request) {
        return success(ownerFaceAuthService.submit(request));
    }
}
