package com.pangu.interfaces.web.service;

import com.pangu.domain.context.UserContext;
import com.pangu.infrastructure.persistence.mapper.UserContextMapper;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.owner.FaceAuthRequest;
import com.pangu.interfaces.web.controller.dto.owner.FaceAuthResponse;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OwnerFaceAuthService {

    private static final int FACE_AUTH_LEVEL = 3;

    private final UserContextMapper userContextMapper;

    @Transactional
    public FaceAuthResponse submit(FaceAuthRequest request) {
        UserContext ctx = SecurityUtils.getUserContext();
        if (ctx == null || ctx.identityType() != UserContext.IdentityType.C_USER || ctx.uid() == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "仅 C 端业主身份可提交刷脸认证");
        }

        String provider = normalizeProvider(request.provider());
        String providerRequestId = request.providerRequestId().trim();
        String providerResult = trimToNull(request.providerResult());

        userContextMapper.insertFaceAuthAttestation(
                ctx.uid(),
                ctx.accountId(),
                provider,
                providerRequestId,
                providerResult,
                1,
                FACE_AUTH_LEVEL);
        int updated = userContextMapper.upgradeCUserAuthLevel(ctx.uid(), ctx.accountId(), FACE_AUTH_LEVEL);
        if (updated != 1) {
            throw new AppException(CommonErrorCode.USER_NOT_REGISTERED, "当前业主身份不存在或已失效");
        }
        userContextMapper.markAccountRealNameVerified(ctx.accountId());

        return new FaceAuthResponse(true, provider + ":" + providerRequestId, FACE_AUTH_LEVEL);
    }

    private String normalizeProvider(String raw) {
        String provider = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (provider.isBlank()) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "provider 不能为空");
        }
        return provider;
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }
}
