// 关联业务：持久化可撤销、可轮换的长期会话凭证，避免短期 JWT 过期后用户已登录状态直接失效。
package com.pangu.domain.repository;

/**
 * 刷新会话仓储。
 *
 * <p>数据库只保存刷新凭证的不可逆摘要。消费操作必须是原子性的：同一个刷新凭证只能成功
 * 使用一次，服务端随后为当前身份上下文签发新的访问令牌和新的刷新凭证。</p>
 */
public interface RefreshSessionRepository {

    void create(NewRefreshSession session);

    /**
     * 原子消费未撤销且未过期的刷新凭证。
     *
     * @param tokenHash 原始刷新凭证的 SHA-256 摘要
     * @return 会话身份上下文；凭证不存在、过期或已被消费时返回 {@code null}
     */
    RefreshSession consume(String tokenHash);

    record NewRefreshSession(
            String tokenHash,
            Long accountId,
            String identityType,
            Long activeIdentityId,
            Long tenantId,
            long expiresInSeconds) {
    }

    record RefreshSession(
            Long accountId,
            String identityType,
            Long activeIdentityId,
            Long tenantId) {
    }
}
