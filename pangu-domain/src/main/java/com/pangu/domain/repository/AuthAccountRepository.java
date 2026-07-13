// 关联业务：统一账户与微信小程序主体绑定，确保微信手机号授权可追溯到唯一自然人账号。
package com.pangu.domain.repository;

public interface AuthAccountRepository {

    AccountSnapshot findByPhone(String phone);

    AccountSnapshot findById(Long accountId);

    AccountSnapshot createColdStartOwnerAccount(String phone);

    /**
     * 确保自然人账号具备 C 端基础身份。
     *
     * <p>微信手机号授权可以创建基础账户，但不得据此推定产权、租户归属或表决资格。</p>
     */
    Long ensureCUserIdentity(Long accountId);

    Long findCUserUidByAccountId(Long accountId);

    AccountIdentitySnapshot findIdentityByAccountId(Long accountId);

    int updateIdentity(Long accountId, String realName, String idCardNumber);

    int updateLastActiveIdentity(Long accountId, Long identityId, String identityType);

    int updateCUserLastActiveTenant(Long uid, Long tenantId);

    Long findAccountIdByWeChatSubjectHash(String miniProgramAppId, String subjectHash);

    WeChatIdentitySnapshot findWeChatIdentity(Long accountId, String miniProgramAppId);

    int bindWeChatIdentity(WeChatIdentityBinding binding);

    int touchWeChatIdentityLogin(Long accountId, String miniProgramAppId);

    int updateWeChatProfile(Long accountId, String miniProgramAppId, String nickname, String avatarUrl);

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

    /**
     * 微信主体仅保存不可逆散列，昵称和头像仅供本人资料页展示，不具有身份或产权证明效力。
     */
    record WeChatIdentitySnapshot(
            Long accountId,
            String miniProgramAppId,
            String subjectHash,
            String nickname,
            String avatarUrl) {
    }

    record WeChatIdentityBinding(
            Long accountId,
            String miniProgramAppId,
            String subjectHash) {
    }
}
