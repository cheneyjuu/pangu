-- 关联业务：微信小程序手机号授权与展示资料绑定；数据库不持久化原始 openid，仅保存不可逆主体散列。
CREATE TABLE t_account_wechat_identity (
    account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    mini_program_app_id VARCHAR(64) NOT NULL,
    subject_hash CHAR(64) NOT NULL,
    nickname VARCHAR(64),
    avatar_url VARCHAR(512),
    last_login_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_account_wechat_identity PRIMARY KEY (account_id, mini_program_app_id),
    CONSTRAINT uk_account_wechat_identity_subject UNIQUE (mini_program_app_id, subject_hash)
);

COMMENT ON TABLE t_account_wechat_identity IS '微信小程序授权账号绑定；openid 仅在交换时使用，不落库';
COMMENT ON COLUMN t_account_wechat_identity.subject_hash IS 'appid 与 openid 组合后的不可逆 SHA-256 散列';
COMMENT ON COLUMN t_account_wechat_identity.nickname IS '用户单独授权的微信昵称，仅作资料展示';
COMMENT ON COLUMN t_account_wechat_identity.avatar_url IS '用户单独授权的微信头像，仅作资料展示';
