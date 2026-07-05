package com.pangu.domain.repository;

public interface AuthAccountRepository {

    AccountSnapshot findByPhone(String phone);

    AccountSnapshot findById(Long accountId);

    AccountSnapshot createColdStartOwnerAccount(String phone);

    Long findCUserUidByAccountId(Long accountId);

    AccountIdentitySnapshot findIdentityByAccountId(Long accountId);

    int updateIdentity(Long accountId, String realName, String idCardNumber);

    int updateLastActiveIdentity(Long accountId, Long identityId, String identityType);

    int updateCUserLastActiveTenant(Long uid, Long tenantId);

    record AccountSnapshot(
            Long accountId,
            String phone,
            Integer status,
            Long lastActiveIdentityId,
            String lastActiveIdentityType) {
    }

    record AccountIdentitySnapshot(
            Long accountId,
            String phone,
            String realNameCipher,
            Integer realNameVerified,
            Integer status) {
    }
}
