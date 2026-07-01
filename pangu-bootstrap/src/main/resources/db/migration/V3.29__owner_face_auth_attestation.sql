CREATE TABLE t_owner_face_auth_attestation (
    id BIGSERIAL PRIMARY KEY,
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    provider VARCHAR(32) NOT NULL,
    provider_request_id VARCHAR(128) NOT NULL,
    provider_result VARCHAR(512),
    verified SMALLINT NOT NULL,
    auth_level_after SMALLINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_owner_face_auth_verified CHECK (verified IN (0, 1)),
    CONSTRAINT chk_owner_face_auth_level_after CHECK (auth_level_after BETWEEN 1 AND 4)
);

CREATE INDEX idx_owner_face_auth_uid_created
    ON t_owner_face_auth_attestation(uid, created_at DESC);

CREATE UNIQUE INDEX uk_owner_face_auth_provider_request
    ON t_owner_face_auth_attestation(provider, provider_request_id);

COMMENT ON TABLE t_owner_face_auth_attestation IS '业主端 L3 刷脸核身凭证审计记录';
COMMENT ON COLUMN t_owner_face_auth_attestation.provider IS '核身来源：WECHAT/TENCENT/ALIYUN 等';
COMMENT ON COLUMN t_owner_face_auth_attestation.provider_request_id IS '小程序或云厂商返回的核身请求/流水 ID';
COMMENT ON COLUMN t_owner_face_auth_attestation.provider_result IS '供应商核身结果摘要，不存储人脸图像';
COMMENT ON COLUMN t_owner_face_auth_attestation.verified IS '是否核身通过：0-否，1-是';
