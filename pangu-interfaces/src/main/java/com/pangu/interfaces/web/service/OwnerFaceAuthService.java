package com.pangu.interfaces.web.service;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.gateway.identity.FaceAuthGateway;
import com.pangu.domain.model.identity.ChineseResidentId;
import com.pangu.domain.repository.OwnerIdentityVerificationRepository;
import com.pangu.domain.security.NameDecryptor;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.owner.FaceAuthContextResponse;
import com.pangu.interfaces.web.controller.dto.owner.FaceAuthRequest;
import com.pangu.interfaces.web.controller.dto.owner.FaceAuthResponse;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OwnerFaceAuthService {

    private static final int FACE_AUTH_LEVEL = 3;

    private final OwnerIdentityVerificationRepository ownerIdentityVerificationRepository;
    private final NameDecryptor nameDecryptor;
    private final FaceAuthGateway faceAuthGateway;
    private final TransactionTemplate transactionTemplate;

    public FaceAuthContextResponse prepareContext() {
        UserContext ctx = requireCUserContext();
        RegisteredIdentity identity = loadRegisteredIdentity(ctx);
        if (!identity.eligible()) {
            return ineligible(identity.reason());
        }
        try {
            FaceAuthGateway.FaceAuthSession session = faceAuthGateway.createSession(
                    new FaceAuthGateway.FaceAuthSessionRequest(
                            identity.realName(),
                            identity.idCardNumber(),
                            "accountId=" + ctx.accountId() + ",uid=" + ctx.uid()));
            return new FaceAuthContextResponse(
                    true,
                    null,
                    null,
                    maskName(identity.realName()),
                    ChineseResidentId.mask(identity.idCardNumber()),
                    null,
                    session.provider(),
                    session.bizToken(),
                    session.url(),
                    session.expiresInSeconds());
        } catch (RuntimeException e) {
            throw new AppException(CommonErrorCode.SYSTEM_ERROR, "人脸核身服务暂不可用", e);
        }
    }

    public FaceAuthResponse submit(FaceAuthRequest request) {
        UserContext ctx = requireCUserContext();
        if (request == null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "刷脸核身请求不能为空");
        }

        String provider = normalizeProvider(request.provider());
        String bizToken = firstText(request.bizToken(), request.providerRequestId());
        if (!StringUtils.hasText(bizToken)) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "bizToken 不能为空");
        }
        RegisteredIdentity identity = loadRegisteredIdentity(ctx);
        if (!identity.eligible()) {
            throw new AppException(CommonErrorCode.FORBIDDEN, identity.reason());
        }

        FaceAuthGateway.FaceAuthVerificationResult verification;
        try {
            verification = faceAuthGateway.verify(new FaceAuthGateway.FaceAuthVerificationRequest(
                    provider, bizToken, identity.realName(), identity.idCardNumber()));
        } catch (RuntimeException e) {
            throw new AppException(CommonErrorCode.SYSTEM_ERROR, "人脸核身结果查询失败", e);
        }
        if (!verification.verified()) {
            throw new AppException(CommonErrorCode.BAD_REQUEST,
                    firstText(verification.failureReason(), "人脸核身未通过"));
        }

        String attestationProvider = firstText(verification.provider(), provider);
        String providerRequestId = firstText(verification.providerRequestId(), bizToken);
        return transactionTemplate.execute(status -> faceAuthGateway.isTestOnly()
                ? persistTestCapture(ctx, attestationProvider, providerRequestId)
                : persistVerifiedFaceAuth(
                        ctx,
                        attestationProvider,
                        providerRequestId,
                        trimToNull(verification.providerResult())));
    }

    /**
     * 体验环境只记录客户端摄像头采集链路的测试回执，不升级认证等级，不能取得 L3 权益。
     */
    private FaceAuthResponse persistTestCapture(UserContext ctx,
                                                 String attestationProvider,
                                                 String providerRequestId) {
        int currentAuthLevel = ctx.authLevel().getValue();
        try {
            ownerIdentityVerificationRepository.insertFaceAuthAttestation(
                    ctx.uid(),
                    ctx.accountId(),
                    attestationProvider,
                    providerRequestId,
                    "{\"testOnly\":true,\"purpose\":\"client_camera_capture\"}",
                    0,
                    currentAuthLevel);
        } catch (DuplicateKeyException e) {
            throw new AppException(CommonErrorCode.BAD_REQUEST, "该测试摄像头采集流水已提交", e);
        }
        return new FaceAuthResponse(
                false,
                attestationProvider + ":" + providerRequestId,
                currentAuthLevel,
                true,
                "已记录测试摄像头采集，未升级 L3 认证");
    }

    private FaceAuthResponse persistVerifiedFaceAuth(UserContext ctx,
                                                     String attestationProvider,
                                                     String providerRequestId,
                                                     String providerResult) {
        try {
            ownerIdentityVerificationRepository.insertFaceAuthAttestation(
                    ctx.uid(),
                    ctx.accountId(),
                    attestationProvider,
                    providerRequestId,
                    providerResult,
                    1,
                    FACE_AUTH_LEVEL);
        } catch (DuplicateKeyException e) {
            throw new AppException(CommonErrorCode.BAD_REQUEST, "该刷脸核身流水已提交", e);
        }
        int updated = ownerIdentityVerificationRepository.upgradeCUserAuthLevel(
                ctx.uid(), ctx.accountId(), FACE_AUTH_LEVEL);
        if (updated != 1) {
            throw new AppException(CommonErrorCode.USER_NOT_REGISTERED, "当前业主身份不存在或已失效");
        }
        ownerIdentityVerificationRepository.markAccountRealNameVerified(ctx.accountId());

        return new FaceAuthResponse(
                true,
                attestationProvider + ":" + providerRequestId,
                FACE_AUTH_LEVEL,
                false,
                "人脸核身已通过");
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
        if ("WECHAT".equals(provider) || "TENCENT".equals(provider)) {
            return FaceAuthGateway.PROVIDER_TENCENT_FACEID;
        }
        if (!FaceAuthGateway.PROVIDER_TENCENT_FACEID.equals(provider)) {
            throw new AppException(CommonErrorCode.PARAM_ERROR,
                    "当前仅支持腾讯云实名核身 provider=TENCENT_FACEID");
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
        return new FaceAuthContextResponse(false, null, null, null, null, reason, null, null, null, null);
    }

    private RegisteredIdentity loadRegisteredIdentity(UserContext ctx) {
        OwnerIdentityVerificationRepository.FaceAuthIdentitySnapshot identity =
                ownerIdentityVerificationRepository.findFaceAuthIdentity(ctx.accountId());
        if (identity == null) {
            return RegisteredIdentity.ineligible("当前账号未登记实名身份，请联系居委会补录后再刷脸");
        }

        String realName = trimToNull(nameDecryptor.safeDecrypt(identity.realNameCipher()));
        String idCardNumber = ChineseResidentId.normalize(nameDecryptor.safeDecrypt(identity.idCardCipher()));
        if (realName == null || idCardNumber == null) {
            return RegisteredIdentity.ineligible("当前账号缺少已登记姓名或身份证号，请联系居委会补录后再刷脸");
        }
        if (ChineseResidentId.isPlaceholder(realName, idCardNumber)) {
            return RegisteredIdentity.ineligible("当前账号登记身份仍是测试或占位数据，不能发起实名刷脸");
        }
        if (!ChineseResidentId.isValid(idCardNumber)) {
            return RegisteredIdentity.ineligible("当前账号登记身份证号格式或校验码不正确，不能发起实名刷脸");
        }
        return RegisteredIdentity.eligible(realName, idCardNumber);
    }

    private String maskName(String realName) {
        if (realName.length() <= 1) {
            return "*";
        }
        return realName.charAt(0) + "*".repeat(Math.max(1, realName.length() - 1));
    }

    private String firstText(String first, String fallback) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(fallback);
    }

    private record RegisteredIdentity(boolean eligible, String realName, String idCardNumber, String reason) {

        static RegisteredIdentity eligible(String realName, String idCardNumber) {
            return new RegisteredIdentity(true, realName, idCardNumber, null);
        }

        static RegisteredIdentity ineligible(String reason) {
            return new RegisteredIdentity(false, null, null, reason);
        }
    }
}
