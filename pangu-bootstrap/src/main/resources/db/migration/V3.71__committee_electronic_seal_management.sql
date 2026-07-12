-- 关联业务：业主自治组织电子印章台账、用印审计及维修报审盖章文件管理。

CREATE TABLE t_committee_electronic_seal (
    electronic_seal_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    seal_name VARCHAR(120) NOT NULL,
    seal_type VARCHAR(32) NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    provider_seal_id VARCHAR(128) NOT NULL,
    certificate_serial VARCHAR(128) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    custodian_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    committee_term_name VARCHAR(128) NOT NULL,
    simulated SMALLINT NOT NULL DEFAULT 0,
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_committee_electronic_seal_type CHECK (
        seal_type IN ('OWNERS_ASSEMBLY', 'OWNERS_COMMITTEE', 'FINANCIAL')
    ),
    CONSTRAINT chk_committee_electronic_seal_status CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'EXPIRED', 'REVOKED')
    ),
    CONSTRAINT chk_committee_electronic_seal_simulated CHECK (simulated IN (0, 1)),
    CONSTRAINT chk_committee_electronic_seal_validity CHECK (valid_until > valid_from)
);

CREATE UNIQUE INDEX uk_committee_electronic_seal_active_type
    ON t_committee_electronic_seal(tenant_id, seal_type)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_committee_electronic_seal_tenant
    ON t_committee_electronic_seal(tenant_id, status, create_time DESC);

COMMENT ON TABLE t_committee_electronic_seal IS
    '业主大会、业主委员会及共有资金专用电子印章台账；不保存印章私钥或可复用印模文件';
COMMENT ON COLUMN t_committee_electronic_seal.simulated IS
    '1=仅开发测试模拟印章，无法律效力，生产环境禁止使用';

CREATE TABLE t_committee_seal_usage (
    usage_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    electronic_seal_id BIGINT REFERENCES t_committee_electronic_seal(electronic_seal_id),
    seal_name VARCHAR(120) NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    sealing_method VARCHAR(32) NOT NULL,
    source_attachment_id BIGINT REFERENCES t_repair_attachment(attachment_id),
    sealed_attachment_id BIGINT NOT NULL REFERENCES t_repair_attachment(attachment_id),
    source_file_hash VARCHAR(128),
    sealed_file_hash VARCHAR(128) NOT NULL,
    provider_transaction_id VARCHAR(128),
    certificate_serial VARCHAR(128),
    verification_status VARCHAR(48) NOT NULL,
    simulated SMALLINT NOT NULL DEFAULT 0,
    operator_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    remark VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_committee_seal_usage_method CHECK (
        sealing_method IN ('UPLOADED_PHYSICAL', 'UPLOADED_EXTERNAL_ELECTRONIC', 'PLATFORM_ELECTRONIC')
    ),
    CONSTRAINT chk_committee_seal_usage_simulated CHECK (simulated IN (0, 1))
);

CREATE INDEX idx_committee_seal_usage_tenant
    ON t_committee_seal_usage(tenant_id, create_time DESC);
CREATE INDEX idx_committee_seal_usage_business
    ON t_committee_seal_usage(business_type, business_id, create_time DESC);

COMMENT ON TABLE t_committee_seal_usage IS
    '业主自治组织用印审计记录，固化签前签后文件、签章方式、证书和验签结果';

ALTER TABLE t_repair_governance_seal
    ADD COLUMN usage_id BIGINT REFERENCES t_committee_seal_usage(usage_id);

CREATE UNIQUE INDEX uk_repair_governance_seal_usage
    ON t_repair_governance_seal(usage_id)
    WHERE usage_id IS NOT NULL;

ALTER TABLE t_repair_attachment
    DROP CONSTRAINT IF EXISTS chk_repair_attachment_kind;

ALTER TABLE t_repair_attachment
    ADD CONSTRAINT chk_repair_attachment_kind CHECK (
        attachment_kind IN (
            'INTAKE_ATTACHMENT', 'LOCATION_IMAGE', 'SURVEY_IMAGE', 'SURVEY_VIDEO',
            'QUOTE_DOCUMENT', 'APPROVAL_DOCUMENT', 'SOLITAIRE_SCREENSHOT',
            'GOVERNANCE_SEALED_DOCUMENT'
        )
    );

COMMENT ON COLUMN t_repair_attachment.attachment_kind IS
    'INTAKE_ATTACHMENT=登记工单附件；LOCATION_IMAGE=位置证据；SURVEY_IMAGE/SURVEY_VIDEO=初勘证据；QUOTE_DOCUMENT=报价原件；APPROVAL_DOCUMENT=物业正式报审文件；SOLITAIRE_SCREENSHOT=微信接龙截图；GOVERNANCE_SEALED_DOCUMENT=业委会盖章结果文件';

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES
    ('committee:seal:read', '查看业主自治组织印章台账与用印记录', 'COMMITTEE', 'GB', 0),
    ('committee:seal:manage', '启用、停用业主自治组织电子印章', 'COMMITTEE', 'B', 1),
    ('committee:seal:use', '按审批结果使用业主自治组织印章', 'COMMITTEE', 'B', 1)
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role.role_id, grant_row.permission_key
FROM sys_role role
JOIN (
    VALUES
        ('GOV_SUPER_ADMIN', 'committee:seal:read'),
        ('COMMUNITY_ADMIN', 'committee:seal:read'),
        ('COMMITTEE_DIRECTOR', 'committee:seal:read'),
        ('COMMITTEE_DIRECTOR', 'committee:seal:manage'),
        ('COMMITTEE_DIRECTOR', 'committee:seal:use'),
        ('COMMITTEE_MEMBER', 'committee:seal:read')
) AS grant_row(role_key, permission_key) ON grant_row.role_key = role.role_key
ON CONFLICT (role_id, permission_key) DO NOTHING;

INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES
    (3050, 3000, 'committee-seals', '印章管理', '/committee-seals', NULL, 50, 1, '0',
     'committee:seal:read', NULL, NULL)
ON CONFLICT (menu_id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    route_id = EXCLUDED.route_id,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    order_num = EXCLUDED.order_num,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    required_permission = EXCLUDED.required_permission,
    required_any_permissions = EXCLUDED.required_any_permissions,
    required_role_keys = EXCLUDED.required_role_keys;
