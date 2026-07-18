-- 关联业务：保存业主大会议事规则原件、可执行配置和主任/副主任确认记录；不提供平台默认规则。

CREATE TABLE t_owners_assembly_rule (
    rule_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    rule_name VARCHAR(200) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    effective_date DATE NOT NULL,
    change_reason VARCHAR(1000) NOT NULL,
    configuration_json JSONB NOT NULL,
    configuration_sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    etag VARCHAR(255) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    drafted_by_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    drafted_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    submitted_by_account_id BIGINT REFERENCES t_account(account_id),
    submitted_by_user_id BIGINT REFERENCES sys_user(user_id),
    submitted_at TIMESTAMP,
    activated_by_account_id BIGINT REFERENCES t_account(account_id),
    activated_by_user_id BIGINT REFERENCES sys_user(user_id),
    activated_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_owners_assembly_rule_version UNIQUE (tenant_id, rule_name, rule_version),
    CONSTRAINT chk_owners_assembly_rule_status CHECK (
        status IN ('DRAFT', 'PENDING_CONFIRMATION', 'ACTIVE', 'SUPERSEDED')
    ),
    CONSTRAINT chk_owners_assembly_rule_file_size CHECK (file_size > 0)
);

CREATE UNIQUE INDEX uk_owners_assembly_rule_active
    ON t_owners_assembly_rule(tenant_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_owners_assembly_rule_history
    ON t_owners_assembly_rule(tenant_id, effective_date DESC, rule_id DESC);

COMMENT ON TABLE t_owners_assembly_rule IS
    '业主大会实际备案议事规则版本：原件、结构化配置和确认生命周期共同构成生效依据';
COMMENT ON COLUMN t_owners_assembly_rule.configuration_json IS
    '从原件逐项核对录入的结构化规则配置；草稿可不完整，启用前必须完整';
COMMENT ON COLUMN t_owners_assembly_rule.configuration_sha256 IS
    '结构化配置规范化序列化后的 SHA-256，用于确认和后续会议快照的可回查性';

CREATE TABLE t_owners_assembly_rule_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES t_owners_assembly_rule(rule_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    event_type VARCHAR(40) NOT NULL,
    configuration_sha256 CHAR(64) NOT NULL,
    change_reason VARCHAR(1000) NOT NULL,
    actor_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    actor_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    actor_role_key VARCHAR(64) NOT NULL,
    actor_committee_position VARCHAR(32),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_owners_assembly_rule_audit_event CHECK (
        event_type IN ('DRAFT_CREATED', 'DRAFT_UPDATED', 'SUBMITTED_FOR_CONFIRMATION', 'ACTIVATED', 'SUPERSEDED')
    ),
    CONSTRAINT chk_owners_assembly_rule_audit_position CHECK (
        actor_committee_position IS NULL OR actor_committee_position IN ('DIRECTOR', 'VICE_DIRECTOR')
    )
);

CREATE INDEX idx_owners_assembly_rule_audit_rule
    ON t_owners_assembly_rule_audit(rule_id, tenant_id, audit_id ASC);

COMMENT ON TABLE t_owners_assembly_rule_audit IS
    '议事规则草稿、提交确认、启用和替代动作的不可抵赖审计记录';

CREATE TABLE t_owners_assembly_rule_field_confirmation (
    confirmation_id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES t_owners_assembly_rule(rule_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    configuration_sha256 CHAR(64) NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    source_page_number INTEGER NOT NULL,
    source_clause VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    confirmed_by_account_id BIGINT REFERENCES t_account(account_id),
    confirmed_by_user_id BIGINT REFERENCES sys_user(user_id),
    confirmed_by_committee_position VARCHAR(32),
    confirmed_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_owners_assembly_rule_field_confirmation
        UNIQUE (rule_id, configuration_sha256, field_key),
    CONSTRAINT chk_owners_assembly_rule_field_confirmation_status CHECK (
        status IN ('PENDING', 'CONFIRMED')
    ),
    CONSTRAINT chk_owners_assembly_rule_field_confirmation_position CHECK (
        confirmed_by_committee_position IS NULL
        OR confirmed_by_committee_position IN ('DIRECTOR', 'VICE_DIRECTOR')
    ),
    CONSTRAINT chk_owners_assembly_rule_field_confirmation_source_page CHECK (
        source_page_number > 0
    )
);

CREATE INDEX idx_owners_assembly_rule_field_confirmation_rule
    ON t_owners_assembly_rule_field_confirmation(rule_id, tenant_id, configuration_sha256, field_key);

COMMENT ON TABLE t_owners_assembly_rule_field_confirmation IS
    '主任或副主任对每个结构化议事规则字段及其原件条款的逐项核对记录；全部确认后才可启用规则版本';

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES
    ('owners-assembly:rule:read', '查看本小区业主大会议事规则及其审计记录', 'VOTING', 'GBS', 0),
    ('owners-assembly:rule:draft', '上传原件并维护业主大会议事规则草稿', 'VOTING', 'BS', 0),
    ('owners-assembly:rule:activate', '以主任或副主任身份确认启用业主大会议事规则', 'VOTING', 'B', 1)
ON CONFLICT (permission_key) DO UPDATE SET
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    allowed_dept_categories = EXCLUDED.allowed_dept_categories,
    is_legal_redline = EXCLUDED.is_legal_redline;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role.role_id, grant_row.permission_key
FROM sys_role role
JOIN (
    VALUES
        ('GOV_SUPER_ADMIN', 'owners-assembly:rule:read'),
        ('COMMUNITY_ADMIN', 'owners-assembly:rule:read'),
        ('COMMITTEE_DIRECTOR', 'owners-assembly:rule:read'),
        ('COMMITTEE_MEMBER', 'owners-assembly:rule:read'),
        ('COMMITTEE_SECRETARY', 'owners-assembly:rule:read'),
        ('PROPERTY_MANAGER', 'owners-assembly:rule:read'),
        ('PROPERTY_STAFF', 'owners-assembly:rule:read'),
        ('COMMITTEE_DIRECTOR', 'owners-assembly:rule:draft'),
        ('COMMITTEE_MEMBER', 'owners-assembly:rule:draft'),
        ('COMMITTEE_SECRETARY', 'owners-assembly:rule:draft'),
        ('PROPERTY_MANAGER', 'owners-assembly:rule:draft'),
        ('PROPERTY_STAFF', 'owners-assembly:rule:draft'),
        ('COMMITTEE_DIRECTOR', 'owners-assembly:rule:activate'),
        ('COMMITTEE_MEMBER', 'owners-assembly:rule:activate')
) AS grant_row(role_key, permission_key) ON grant_row.role_key = role.role_key
ON CONFLICT (role_id, permission_key) DO NOTHING;
