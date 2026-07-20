// 关联业务：向办理人员展示书面委托的房屋归属、代理人、有效期和核验状态，不暴露完整证件号码。
package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.VotingProxyAuthorization;

import java.time.Instant;

public record VotingProxyAuthorizationResponse(
        Long authorizationId,
        Long packageId,
        Long electorateItemId,
        Long principalOpid,
        String agentName,
        String agentIdentityDocumentType,
        String agentIdentityNumberMasked,
        Instant validFrom,
        Instant validUntil,
        String originalFileName,
        String status,
        Long registeredByUserId,
        Instant registeredAt,
        Long reviewedByUserId,
        Instant reviewedAt,
        String reviewNote,
        Long revokedByUserId,
        Instant revokedAt,
        String revokeReason
) {
    public static VotingProxyAuthorizationResponse from(VotingProxyAuthorization authorization) {
        return new VotingProxyAuthorizationResponse(
                authorization.authorizationId(), authorization.packageId(), authorization.electorateItemId(),
                authorization.principalOpid(), authorization.agentName(),
                authorization.agentIdentityDocumentType().name(), mask(authorization.agentIdentityNumber()),
                authorization.validFrom(), authorization.validUntil(), authorization.originalFileName(),
                authorization.status().name(), authorization.registeredByUserId(), authorization.registeredAt(),
                authorization.reviewedByUserId(), authorization.reviewedAt(), authorization.reviewNote(),
                authorization.revokedByUserId(), authorization.revokedAt(), authorization.revokeReason());
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
