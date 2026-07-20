// 关联业务：保存一次正式表决中业主书面委托他人代为办理纸质投票的原件、范围和核验结论。
package com.pangu.domain.model.voting;

import java.time.Instant;

/**
 * 书面委托授权只绑定一个冻结表决包和一个专有部分。
 *
 * <p>代理人不是新的表决权人；最终选票仍以 {@code electorateItemId} 对应的业主及专有部分计票。
 */
public record VotingProxyAuthorization(
        Long authorizationId,
        Long packageId,
        Long electorateItemId,
        Long tenantId,
        Long principalOpid,
        Long principalUid,
        String agentName,
        IdentityDocumentType agentIdentityDocumentType,
        String agentIdentityNumber,
        Instant validFrom,
        Instant validUntil,
        String documentObjectKey,
        String originalFileName,
        String contentType,
        Long fileSize,
        String etag,
        String contentSha256,
        String authorizationHash,
        Status status,
        Long registeredByUserId,
        Instant registeredAt,
        Long reviewedByUserId,
        Instant reviewedAt,
        String reviewNote,
        Long revokedByUserId,
        Instant revokedAt,
        String revokeReason,
        long version
) {

    public enum IdentityDocumentType {
        CHINESE_RESIDENT_ID,
        PASSPORT,
        OTHER
    }

    public enum Status {
        PENDING_REVIEW,
        CONFIRMED,
        REJECTED,
        REVOKED
    }

    public boolean usableAt(Instant time) {
        return status == Status.CONFIRMED
                && time != null
                && !time.isBefore(validFrom)
                && !time.isAfter(validUntil);
    }
}
