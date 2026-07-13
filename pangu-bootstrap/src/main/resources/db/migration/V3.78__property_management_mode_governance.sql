-- 关联业务：将小区物业管理模式从前端展示项提升为经审核生效、可追溯的租户运行事实。

ALTER TABLE t_community_registration_application
    ADD COLUMN declared_property_mode VARCHAR(32);

ALTER TABLE t_community_registration_application
    ADD CONSTRAINT chk_community_registration_declared_property_mode CHECK (
        declared_property_mode IS NULL
        OR declared_property_mode IN ('LUMP_SUM', 'FUND_RAISING', 'TRUST')
    );

COMMENT ON COLUMN t_community_registration_application.declared_property_mode IS
    '注册人申报的互斥物业管理模式；历史申请允许为空，新的提交申请必须声明';

ALTER TABLE t_tenant_community
    ADD COLUMN property_mode VARCHAR(32),
    ADD COLUMN property_mode_history JSONB NOT NULL DEFAULT '[]'::JSONB;

ALTER TABLE t_tenant_community
    ADD CONSTRAINT chk_tenant_community_property_mode CHECK (
        property_mode IS NULL OR property_mode IN ('LUMP_SUM', 'FUND_RAISING', 'TRUST')
    ),
    ADD CONSTRAINT chk_tenant_community_property_mode_history CHECK (
        jsonb_typeof(property_mode_history) = 'array'
    );

COMMENT ON COLUMN t_tenant_community.property_mode IS
    '当前已生效的互斥物业管理模式；历史租户未配置时为空，禁止以信托制等前端默认值替代';
COMMENT ON COLUMN t_tenant_community.property_mode_history IS
    '物业管理模式生效与变更的不可变审计摘要；完整材料和审核过程由模式变更申请记录保存';

CREATE TABLE t_property_management_mode_change_request (
    request_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    current_property_mode VARCHAR(32),
    requested_property_mode VARCHAR(32) NOT NULL,
    owners_assembly_resolution_reference VARCHAR(128) NOT NULL,
    change_reason VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    applicant_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    applicant_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    applicant_dept_id BIGINT REFERENCES sys_dept(dept_id),
    submitted_at TIMESTAMP,
    reviewer_account_id BIGINT REFERENCES t_account(account_id),
    reviewer_user_id BIGINT REFERENCES sys_user(user_id),
    reviewer_dept_id BIGINT REFERENCES sys_dept(dept_id),
    review_comment VARCHAR(1000),
    reviewed_at TIMESTAMP,
    executed_at TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_property_management_mode_change_current CHECK (
        current_property_mode IS NULL
        OR current_property_mode IN ('LUMP_SUM', 'FUND_RAISING', 'TRUST')
    ),
    CONSTRAINT chk_property_management_mode_change_requested CHECK (
        requested_property_mode IN ('LUMP_SUM', 'FUND_RAISING', 'TRUST')
    ),
    CONSTRAINT chk_property_management_mode_change_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'REJECTED', 'EXECUTED')
    ),
    CONSTRAINT chk_property_management_mode_change_difference CHECK (
        current_property_mode IS NULL OR current_property_mode <> requested_property_mode
    )
);

CREATE UNIQUE INDEX uk_property_management_mode_change_active_tenant
    ON t_property_management_mode_change_request(tenant_id)
    WHERE status IN ('DRAFT', 'SUBMITTED', 'RETURNED');
CREATE INDEX idx_property_management_mode_change_tenant
    ON t_property_management_mode_change_request(tenant_id, create_time DESC, request_id DESC);

COMMENT ON TABLE t_property_management_mode_change_request IS
    '小区物业管理模式变更申请；业委会主任须提交业主大会决议依据，由街道办审核并执行';
COMMENT ON COLUMN t_property_management_mode_change_request.current_property_mode IS
    '申请创建时锁定的当前生效模式；为空表示历史小区尚未配置模式';
COMMENT ON COLUMN t_property_management_mode_change_request.owners_assembly_resolution_reference IS
    '业主大会决议编号、存证号或不可变文件标识；材料须另行上传并留存';

CREATE TABLE t_property_management_mode_change_material (
    material_id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES t_property_management_mode_change_request(request_id),
    material_type VARCHAR(40) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    etag VARCHAR(128) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    uploaded_by_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_property_management_mode_change_material_type CHECK (
        material_type IN ('OWNERS_ASSEMBLY_RESOLUTION', 'SUPPORTING_EVIDENCE')
    ),
    CONSTRAINT chk_property_management_mode_change_material_status CHECK (
        status IN ('ACTIVE', 'REMOVED')
    ),
    CONSTRAINT chk_property_management_mode_change_material_size CHECK (file_size > 0)
);

CREATE INDEX idx_property_management_mode_change_material
    ON t_property_management_mode_change_material(request_id, status, create_time ASC, material_id ASC);

COMMENT ON TABLE t_property_management_mode_change_material IS
    '物业管理模式变更的私有证据材料；至少应留存业主大会决议文件，禁止保存永久公开 URL';

CREATE TABLE t_property_management_mode_change_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES t_property_management_mode_change_request(request_id),
    actor_account_id BIGINT REFERENCES t_account(account_id),
    actor_user_id BIGINT REFERENCES sys_user(user_id),
    actor_dept_id BIGINT REFERENCES sys_dept(dept_id),
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24),
    payload_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_property_management_mode_change_audit
    ON t_property_management_mode_change_audit(request_id, create_time DESC, audit_id DESC);

COMMENT ON TABLE t_property_management_mode_change_audit IS
    '物业管理模式申请、材料、审核和属地执行的不可变业务审计流水';

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES
    ('property:management-mode:read', '查看小区物业管理模式及变更记录', 'PROPERTY', 'BG', 0),
    ('property:management-mode:submit', '发起和提交小区物业管理模式变更申请', 'PROPERTY', 'B', 1),
    ('property:management-mode:review', '审核小区物业管理模式变更申请', 'PROPERTY', 'G', 1),
    ('property:management-mode:execute', '执行已审核的小区物业管理模式变更', 'PROPERTY', 'G', 1)
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
        ('GOV_SUPER_ADMIN', 'property:management-mode:read'),
        ('GOV_SUPER_ADMIN', 'property:management-mode:review'),
        ('GOV_SUPER_ADMIN', 'property:management-mode:execute'),
        ('COMMITTEE_DIRECTOR', 'property:management-mode:read'),
        ('COMMITTEE_DIRECTOR', 'property:management-mode:submit'),
        ('COMMITTEE_MEMBER', 'property:management-mode:read'),
        ('COMMUNITY_ADMIN', 'property:management-mode:read')
) AS grant_row(role_key, permission_key) ON grant_row.role_key = role.role_key
ON CONFLICT (role_id, permission_key) DO NOTHING;
