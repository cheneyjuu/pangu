package com.pangu.interfaces.web.controller;

import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.owner.RealNameAuthRequest;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import com.pangu.interfaces.web.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/me/auth")
@RequiredArgsConstructor
public class OwnerIdentityController extends BaseController {

    private final AuthService authService;

    @PostMapping("/l2")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> verifyRealName(@Valid @RequestBody RealNameAuthRequest request) {
        Long accountId = SecurityUtils.getAccountId();
        Long uid = SecurityUtils.getUid();
        if (uid == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "仅 C 端业主身份可提交实名认证");
        }
        return success("实名认证已通过", authService.verifyRealName(
                accountId, uid, request.realName(), request.idCardNumber()));
    }
}
