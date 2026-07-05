package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.OwnerIdentityVerificationRepository;
import com.pangu.infrastructure.persistence.mapper.UserContextMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OwnerIdentityVerificationRepositoryImpl implements OwnerIdentityVerificationRepository {

    private final UserContextMapper userContextMapper;

    @Override
    public FaceAuthIdentitySnapshot findFaceAuthIdentity(Long accountId) {
        UserContextMapper.FaceAuthIdentityRow row = userContextMapper.loadFaceAuthIdentity(accountId);
        if (row == null) {
            return null;
        }
        return new FaceAuthIdentitySnapshot(row.getRealNameCipher(), row.getIdCardCipher());
    }

    @Override
    public int upgradeCUserAuthLevel(Long uid, Long accountId, int authLevel) {
        return userContextMapper.upgradeCUserAuthLevel(uid, accountId, authLevel);
    }

    @Override
    public int markAccountRealNameVerified(Long accountId) {
        return userContextMapper.markAccountRealNameVerified(accountId);
    }

    @Override
    public int insertFaceAuthAttestation(Long uid,
                                         Long accountId,
                                         String provider,
                                         String providerRequestId,
                                         String providerResult,
                                         int verified,
                                         int authLevelAfter) {
        return userContextMapper.insertFaceAuthAttestation(
                uid, accountId, provider, providerRequestId, providerResult, verified, authLevelAfter);
    }
}
