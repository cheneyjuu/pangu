// 关联业务：实现可轮换登录刷新会话的持久化，数据库中不保存原始刷新凭证。
package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.RefreshSessionRepository;
import com.pangu.infrastructure.persistence.mapper.RefreshSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 刷新会话仓储的 MyBatis 实现。 */
@Repository
@RequiredArgsConstructor
public class RefreshSessionRepositoryImpl implements RefreshSessionRepository {

    private final RefreshSessionMapper refreshSessionMapper;

    @Override
    public void create(NewRefreshSession session) {
        RefreshSessionMapper.NewRefreshSessionRow row = new RefreshSessionMapper.NewRefreshSessionRow();
        row.setTokenHash(session.tokenHash());
        row.setAccountId(session.accountId());
        row.setIdentityType(session.identityType());
        row.setActiveIdentityId(session.activeIdentityId());
        row.setTenantId(session.tenantId());
        row.setExpiresInSeconds(session.expiresInSeconds());
        refreshSessionMapper.insert(row);
    }

    @Override
    public RefreshSession consume(String tokenHash) {
        RefreshSessionMapper.RefreshSessionRow row = refreshSessionMapper.consume(tokenHash);
        if (row == null) {
            return null;
        }
        return new RefreshSession(
                row.getAccountId(),
                row.getIdentityType(),
                row.getActiveIdentityId(),
                row.getTenantId());
    }
}
