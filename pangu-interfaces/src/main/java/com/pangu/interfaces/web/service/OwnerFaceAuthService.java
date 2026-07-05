package com.pangu.interfaces.web.service;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.repository.OwnerIdentityVerificationRepository;
import com.pangu.domain.security.NameDecryptor;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.owner.FaceAuthContextResponse;
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

    private final OwnerIdentityVerificationRepository ownerIdentityVerificationRepository;
    private final NameDecryptor nameDecryptor;

    public FaceAuthContextResponse prepareContext() {
        UserContext ctx = requireCUserContext();
        OwnerIdentityVerificationRepository.FaceAuthIdentitySnapshot identity =
                ownerIdentityVerificationRepository.findFaceAuthIdentity(ctx.accountId());
        if (identity == null) {
            return ineligible("当前账号未登记实名身份，请联系居委会补录后再刷脸");
        }

        String realName = trimToNull(nameDecryptor.safeDecrypt(identity.realNameCipher()));
        String idCardNumber = trimToNull(nameDecryptor.safeDecrypt(identity.idCardCipher()));
        if (realName == null || idCardNumber == null) {
            return ineligible("当前账号缺少已登记姓名或身份证号，请联系居委会补录后再刷脸");
        }
        if (!isFormalRegisteredIdentity(realName, idCardNumber)) {
            return ineligible("当前账号登记身份仍是测试或占位数据，不能发起微信实名刷脸");
        }

        return new FaceAuthContextResponse(
                true,
                realName,
                idCardNumber,
                maskName(realName),
                maskIdCard(idCardNumber),
                null);
    }

    @Transactional
    public FaceAuthResponse submit(FaceAuthRequest request) {
        UserContext ctx = requireCUserContext();

        String provider = normalizeProvider(request.provider());
        String providerRequestId = request.providerRequestId().trim();
        String providerResult = trimToNull(request.providerResult());

        ownerIdentityVerificationRepository.insertFaceAuthAttestation(
                ctx.uid(),
                ctx.accountId(),
                provider,
                providerRequestId,
                providerResult,
                1,
                FACE_AUTH_LEVEL);
        int updated = ownerIdentityVerificationRepository.upgradeCUserAuthLevel(
                ctx.uid(), ctx.accountId(), FACE_AUTH_LEVEL);
        if (updated != 1) {
            throw new AppException(CommonErrorCode.USER_NOT_REGISTERED, "当前业主身份不存在或已失效");
        }
        ownerIdentityVerificationRepository.markAccountRealNameVerified(ctx.accountId());

        return new FaceAuthResponse(true, provider + ":" + providerRequestId, FACE_AUTH_LEVEL);
    }

    private UserContext requireCUserContext() {
        UserContext ctx = SecurityUtils.getUserContext();
        if (ctx == null || ctx.identityType() != UserContext.IdentityType.C_USER || ctx.uid() == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "仅 C 端业主身份可提交刷脸认证");
        }
        return ctx;
    }

    private String normalizeProvider(String raw) {
        String provider = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (provider.isBlank()) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "provider 不能为空");
        }
        if (!"WECHAT".equals(provider)) {
            throw new AppException(CommonErrorCode.PARAM_ERROR,
                    "当前仅支持微信原生刷脸核身 provider=WECHAT");
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

    private FaceAuthContextResponse ineligible(String reason) {
        return new FaceAuthContextResponse(false, null, null, null, null, reason);
    }

    private boolean isFormalRegisteredIdentity(String realName, String idCardNumber) {
        return !realName.startsWith("MOCK_")
                && !idCardNumber.startsWith("MOCK_")
                && idCardNumber.matches("^\\d{17}[\\dXx]$");
    }

    private String maskName(String realName) {
        if (realName.length() <= 1) {
            return "*";
        }
        return realName.charAt(0) + "*".repeat(Math.max(1, realName.length() - 1));
    }

    private String maskIdCard(String idCardNumber) {
        if (idCardNumber.length() < 10) {
            return "********";
        }
        return idCardNumber.substring(0, 3) + "***********" + idCardNumber.substring(idCardNumber.length() - 4);
    }
}
