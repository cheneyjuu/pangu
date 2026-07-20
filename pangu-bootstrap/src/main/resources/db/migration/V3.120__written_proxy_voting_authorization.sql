-- 关联业务：书面委托代理必须绑定冻结表决范围、原件和异人核验结论，代理人不取得独立计票权。

CREATE TABLE t_voting_proxy_authorization (
    authorization_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    electorate_item_id BIGINT NOT NULL REFERENCES t_voting_electorate_item_snapshot(snapshot_item_id),
    tenant_id BIGINT NOT NULL,
    principal_opid BIGINT NOT NULL,
    principal_uid BIGINT NOT NULL,
    agent_name_cipher TEXT NOT NULL,
    agent_identity_document_type VARCHAR(32) NOT NULL,
    agent_identity_number_cipher TEXT NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    document_object_key VARCHAR(512) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    etag VARCHAR(255),
    content_sha256 CHAR(64) NOT NULL,
    authorization_hash CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW',
    registered_by_user_id BIGINT NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    reviewed_by_user_id BIGINT,
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(500),
    revoked_by_user_id BIGINT,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_voting_proxy_validity CHECK (valid_until >= valid_from),
    CONSTRAINT chk_voting_proxy_status CHECK (
        status IN ('PENDING_REVIEW', 'CONFIRMED', 'REJECTED', 'REVOKED')
    ),
    CONSTRAINT chk_voting_proxy_review CHECK (
        (status = 'PENDING_REVIEW' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR (status IN ('CONFIRMED', 'REJECTED') AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL)
        OR (status = 'REVOKED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND revoked_by_user_id IS NOT NULL AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT uk_voting_proxy_document UNIQUE (tenant_id, document_object_key),
    CONSTRAINT uk_voting_proxy_hash UNIQUE (tenant_id, package_id, content_sha256)
);

CREATE UNIQUE INDEX uk_voting_proxy_active_scope
    ON t_voting_proxy_authorization(package_id, electorate_item_id)
    WHERE status IN ('PENDING_REVIEW', 'CONFIRMED');

CREATE INDEX idx_voting_proxy_package
    ON t_voting_proxy_authorization(package_id, tenant_id, status, authorization_id);

ALTER TABLE t_paper_voting_delivery
    ADD COLUMN proxy_authorization_id BIGINT REFERENCES t_voting_proxy_authorization(authorization_id);

ALTER TABLE t_paper_ballot
    ADD COLUMN proxy_authorization_id BIGINT REFERENCES t_voting_proxy_authorization(authorization_id);

CREATE UNIQUE INDEX uk_paper_ballot_proxy_authorization
    ON t_paper_ballot(proxy_authorization_id)
    WHERE proxy_authorization_id IS NOT NULL;

COMMENT ON TABLE t_voting_proxy_authorization IS
    '正式表决书面委托原件、代理人身份、冻结专有部分和异人核验结论';
COMMENT ON COLUMN t_voting_proxy_authorization.principal_opid IS
    '冻结名册中的业主房屋身份；代理人不替换该计票归属';
COMMENT ON COLUMN t_paper_ballot.proxy_authorization_id IS
    '代理人提交纸票时引用的已核验书面委托；为空表示业主本人办理';
