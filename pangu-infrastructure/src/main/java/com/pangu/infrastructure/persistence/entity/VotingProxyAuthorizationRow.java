// 关联业务：映射正式表决书面委托原件、代理人身份和异人核验状态。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class VotingProxyAuthorizationRow {
    private Long authorizationId;
    private Long packageId;
    private Long electorateItemId;
    private Long tenantId;
    private Long principalOpid;
    private Long principalUid;
    private String agentName;
    private String agentIdentityDocumentType;
    private String agentIdentityNumber;
    private Instant validFrom;
    private Instant validUntil;
    private String documentObjectKey;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String etag;
    private String contentSha256;
    private String authorizationHash;
    private String status;
    private Long registeredByUserId;
    private Instant registeredAt;
    private Long reviewedByUserId;
    private Instant reviewedAt;
    private String reviewNote;
    private Long revokedByUserId;
    private Instant revokedAt;
    private String revokeReason;
    private Long version;
}
