-- 关联业务：保存刷新凭证摘要并支持原子轮换，避免短期 JWT 到期后用户会话直接失效。
CREATE TABLE t_auth_refresh_session (
    session_id BIGSERIAL PRIMARY KEY,
    token_hash CHAR(64) NOT NULL,
    account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    identity_type VARCHAR(32) NOT NULL,
    active_identity_id BIGINT NOT NULL,
    tenant_id BIGINT,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_refresh_session_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_auth_refresh_session_active
    ON t_auth_refresh_session (account_id, expires_at)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE t_auth_refresh_session IS '登录刷新会话，仅保存刷新凭证 SHA-256 摘要，消费后立即撤销';
COMMENT ON COLUMN t_auth_refresh_session.token_hash IS '客户端刷新凭证的 SHA-256 摘要，原始凭证不落库';
