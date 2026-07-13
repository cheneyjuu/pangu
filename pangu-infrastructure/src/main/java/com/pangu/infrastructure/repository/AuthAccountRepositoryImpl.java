// 关联业务：实现自然人账户及微信小程序主体绑定的持久化访问，保证授权手机号不会串绑到其他账号。
package com.pangu.infrastructure.repository;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.repository.AuthAccountRepository;
import com.pangu.infrastructure.persistence.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuthAccountRepositoryImpl implements AuthAccountRepository {

    private final AccountMapper accountMapper;

    @Override
    public AccountSnapshot findByPhone(String phone) {
        return toSnapshot(accountMapper.selectByPhone(phone));
    }

    @Override
    public AccountSnapshot findById(Long accountId) {
        return toSnapshot(accountMapper.selectById(accountId));
    }

    @Override
    public AccountSnapshot createColdStartOwnerAccount(String phone) {
        AccountMapper.AccountInsertRow accountRow = new AccountMapper.AccountInsertRow();
        accountRow.setPhone(phone);
        accountRow.setRealName("");
        accountRow.setRealNameVerified(0);
        accountMapper.insertAccount(accountRow);

        AccountMapper.CUserInsertRow cUserRow = new AccountMapper.CUserInsertRow();
        cUserRow.setAccountId(accountRow.getAccountId());
        cUserRow.setAuthLevel(1);
        accountMapper.insertCUser(cUserRow);

        accountMapper.updateLastActiveIdentity(
                accountRow.getAccountId(), cUserRow.getUid(), UserContext.IdentityType.C_USER.name());
        return findById(accountRow.getAccountId());
    }

    @Override
    public Long findCUserUidByAccountId(Long accountId) {
        return accountMapper.selectCUserUidByAccountId(accountId);
    }

    @Override
    public AccountIdentitySnapshot findIdentityByAccountId(Long accountId) {
        AccountMapper.AccountIdentityRow row = accountMapper.selectIdentityByAccountId(accountId);
        if (row == null) {
            return null;
        }
        return new AccountIdentitySnapshot(
                row.getAccountId(),
                row.getPhone(),
                row.getRealNameCipher(),
                row.getRealNameVerified(),
                row.getStatus());
    }

    @Override
    public int updateIdentity(Long accountId, String realName, String idCardNumber) {
        return accountMapper.updateIdentity(accountId, realName, idCardNumber);
    }

    @Override
    public int updateLastActiveIdentity(Long accountId, Long identityId, String identityType) {
        return accountMapper.updateLastActiveIdentity(accountId, identityId, identityType);
    }

    @Override
    public int updateCUserLastActiveTenant(Long uid, Long tenantId) {
        return accountMapper.updateCUserLastActiveTenant(uid, tenantId);
    }

    @Override
    public Long findAccountIdByWeChatSubjectHash(String miniProgramAppId, String subjectHash) {
        return accountMapper.selectAccountIdByWeChatSubjectHash(miniProgramAppId, subjectHash);
    }

    @Override
    public WeChatIdentitySnapshot findWeChatIdentity(Long accountId, String miniProgramAppId) {
        AccountMapper.WeChatIdentityRow row = accountMapper.selectWeChatIdentity(accountId, miniProgramAppId);
        if (row == null) {
            return null;
        }
        return new WeChatIdentitySnapshot(
                row.getAccountId(),
                row.getMiniProgramAppId(),
                row.getSubjectHash(),
                row.getNickname(),
                row.getAvatarUrl());
    }

    @Override
    public int bindWeChatIdentity(WeChatIdentityBinding binding) {
        AccountMapper.WeChatIdentityInsertRow row = new AccountMapper.WeChatIdentityInsertRow();
        row.setAccountId(binding.accountId());
        row.setMiniProgramAppId(binding.miniProgramAppId());
        row.setSubjectHash(binding.subjectHash());
        return accountMapper.insertWeChatIdentity(row);
    }

    @Override
    public int touchWeChatIdentityLogin(Long accountId, String miniProgramAppId) {
        return accountMapper.touchWeChatIdentityLogin(accountId, miniProgramAppId);
    }

    @Override
    public int updateWeChatProfile(Long accountId, String miniProgramAppId, String nickname, String avatarUrl) {
        return accountMapper.updateWeChatProfile(accountId, miniProgramAppId, nickname, avatarUrl);
    }

    private AccountSnapshot toSnapshot(AccountMapper.AccountRow row) {
        if (row == null) {
            return null;
        }
        return new AccountSnapshot(
                row.getAccountId(),
                row.getPhone(),
                row.getStatus(),
                row.getLastActiveIdentityId(),
                row.getLastActiveIdentityType());
    }
}
