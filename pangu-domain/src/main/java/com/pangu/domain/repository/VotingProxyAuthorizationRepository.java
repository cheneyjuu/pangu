// 关联业务：定义书面委托授权登记、异人核验、撤销和纸票使用校验的持久化端口。
package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VotingProxyAuthorization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VotingProxyAuthorizationRepository {

    VotingProxyAuthorization insert(VotingProxyAuthorization authorization);

    Optional<VotingProxyAuthorization> findById(Long authorizationId, Long packageId, Long tenantId);

    Optional<VotingProxyAuthorization> findByIdForUpdate(Long authorizationId, Long packageId, Long tenantId);

    List<VotingProxyAuthorization> listByPackage(Long packageId, Long tenantId);

    int confirm(Long authorizationId,
                Long tenantId,
                Long reviewedByUserId,
                Instant reviewedAt,
                String reviewNote,
                long expectedVersion);

    int reject(Long authorizationId,
               Long tenantId,
               Long reviewedByUserId,
               Instant reviewedAt,
               String reviewNote,
               long expectedVersion);

    int revoke(Long authorizationId,
               Long tenantId,
               Long revokedByUserId,
               Instant revokedAt,
               String revokeReason,
               long expectedVersion);

    boolean isUsedByPaperRecord(Long authorizationId, Long tenantId);
}
