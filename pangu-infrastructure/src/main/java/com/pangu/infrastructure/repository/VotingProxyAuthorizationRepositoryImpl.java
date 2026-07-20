// 关联业务：以加密字段和乐观锁保存书面委托登记、核验、撤销及使用状态。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.VotingProxyAuthorization;
import com.pangu.domain.repository.VotingProxyAuthorizationRepository;
import com.pangu.infrastructure.persistence.entity.VotingProxyAuthorizationRow;
import com.pangu.infrastructure.persistence.mapper.VotingProxyAuthorizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VotingProxyAuthorizationRepositoryImpl implements VotingProxyAuthorizationRepository {

    private final VotingProxyAuthorizationMapper mapper;

    @Override
    public VotingProxyAuthorization insert(VotingProxyAuthorization authorization) {
        VotingProxyAuthorizationRow row = toRow(authorization);
        mapper.insert(row);
        return findById(row.getAuthorizationId(), authorization.packageId(), authorization.tenantId()).orElseThrow();
    }

    @Override
    public Optional<VotingProxyAuthorization> findById(Long authorizationId, Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.selectById(authorizationId, packageId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<VotingProxyAuthorization> findByIdForUpdate(Long authorizationId, Long packageId, Long tenantId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(authorizationId, packageId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public List<VotingProxyAuthorization> listByPackage(Long packageId, Long tenantId) {
        return mapper.selectByPackage(packageId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public int confirm(Long authorizationId, Long tenantId, Long reviewedByUserId, Instant reviewedAt,
                       String reviewNote, long expectedVersion) {
        return mapper.confirm(authorizationId, tenantId, reviewedByUserId, reviewedAt, reviewNote, expectedVersion);
    }

    @Override
    public int reject(Long authorizationId, Long tenantId, Long reviewedByUserId, Instant reviewedAt,
                      String reviewNote, long expectedVersion) {
        return mapper.reject(authorizationId, tenantId, reviewedByUserId, reviewedAt, reviewNote, expectedVersion);
    }

    @Override
    public int revoke(Long authorizationId, Long tenantId, Long revokedByUserId, Instant revokedAt,
                      String revokeReason, long expectedVersion) {
        return mapper.revoke(authorizationId, tenantId, revokedByUserId, revokedAt, revokeReason, expectedVersion);
    }

    @Override
    public boolean isUsedByPaperRecord(Long authorizationId, Long tenantId) {
        return mapper.isUsedByPaperRecord(authorizationId, tenantId);
    }

    private VotingProxyAuthorizationRow toRow(VotingProxyAuthorization domain) {
        VotingProxyAuthorizationRow row = new VotingProxyAuthorizationRow();
        row.setAuthorizationId(domain.authorizationId());
        row.setPackageId(domain.packageId());
        row.setElectorateItemId(domain.electorateItemId());
        row.setTenantId(domain.tenantId());
        row.setPrincipalOpid(domain.principalOpid());
        row.setPrincipalUid(domain.principalUid());
        row.setAgentName(domain.agentName());
        row.setAgentIdentityDocumentType(domain.agentIdentityDocumentType().name());
        row.setAgentIdentityNumber(domain.agentIdentityNumber());
        row.setValidFrom(domain.validFrom());
        row.setValidUntil(domain.validUntil());
        row.setDocumentObjectKey(domain.documentObjectKey());
        row.setOriginalFileName(domain.originalFileName());
        row.setContentType(domain.contentType());
        row.setFileSize(domain.fileSize());
        row.setEtag(domain.etag());
        row.setContentSha256(domain.contentSha256());
        row.setAuthorizationHash(domain.authorizationHash());
        row.setStatus(domain.status().name());
        row.setRegisteredByUserId(domain.registeredByUserId());
        row.setRegisteredAt(domain.registeredAt());
        return row;
    }

    private VotingProxyAuthorization toDomain(VotingProxyAuthorizationRow row) {
        return new VotingProxyAuthorization(
                row.getAuthorizationId(), row.getPackageId(), row.getElectorateItemId(), row.getTenantId(),
                row.getPrincipalOpid(), row.getPrincipalUid(), row.getAgentName(),
                VotingProxyAuthorization.IdentityDocumentType.valueOf(row.getAgentIdentityDocumentType()),
                row.getAgentIdentityNumber(), row.getValidFrom(), row.getValidUntil(), row.getDocumentObjectKey(),
                row.getOriginalFileName(), row.getContentType(), row.getFileSize(), row.getEtag(),
                row.getContentSha256(), row.getAuthorizationHash(),
                VotingProxyAuthorization.Status.valueOf(row.getStatus()), row.getRegisteredByUserId(),
                row.getRegisteredAt(), row.getReviewedByUserId(), row.getReviewedAt(), row.getReviewNote(),
                row.getRevokedByUserId(), row.getRevokedAt(), row.getRevokeReason(), row.getVersion());
    }
}
