package com.pangu.domain.repository;

public interface OwnerIdentityVerificationRepository {

    FaceAuthIdentitySnapshot findFaceAuthIdentity(Long accountId);

    int upgradeCUserAuthLevel(Long uid, Long accountId, int authLevel);

    int markAccountRealNameVerified(Long accountId);

    int insertFaceAuthAttestation(Long uid,
                                  Long accountId,
                                  String provider,
                                  String providerRequestId,
                                  String providerResult,
                                  int verified,
                                  int authLevelAfter);

    record FaceAuthIdentitySnapshot(String realNameCipher, String idCardCipher) {
    }
}
