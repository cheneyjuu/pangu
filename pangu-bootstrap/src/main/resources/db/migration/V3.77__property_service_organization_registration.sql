-- 关联业务：登记、核验并启用新小区的物业服务企业和本小区项目部，作为物业角色授权的前置条件。

CREATE TABLE t_property_service_enterprise (
    enterprise_id BIGSERIAL PRIMARY KEY,
    enterprise_dept_id BIGINT UNIQUE REFERENCES sys_dept(dept_id),
    legal_name VARCHAR(120) NOT NULL,
    unified_social_credit_code VARCHAR(18) NOT NULL UNIQUE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_property_service_enterprise_uscc CHECK (
        unified_social_credit_code ~ '^[0-9A-HJ-NPQRTUWXY]{18}$'
    )
);

COMMENT ON TABLE t_property_service_enterprise IS
    '物业服务企业主体；企业根组织可跨小区复用，但不得直接承接本小区物业工作身份';
COMMENT ON COLUMN t_property_service_enterprise.enterprise_dept_id IS
    '跨租户企业根部门；本小区物业角色只能挂在其下属 tenant_id 项目部';

CREATE TABLE t_property_service_organization (
    organization_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    enterprise_id BIGINT NOT NULL REFERENCES t_property_service_enterprise(enterprise_id),
    project_dept_id BIGINT UNIQUE REFERENCES sys_dept(dept_id),
    project_dept_name VARCHAR(50) NOT NULL,
    service_contact_name VARCHAR(512) NOT NULL,
    service_contact_phone VARCHAR(512) NOT NULL,
    service_basis VARCHAR(40) NOT NULL,
    service_start_date DATE NOT NULL,
    service_end_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_by_account_id BIGINT REFERENCES t_account(account_id),
    submitted_by_user_id BIGINT REFERENCES sys_user(user_id),
    submitted_at TIMESTAMP,
    verified_by_account_id BIGINT REFERENCES t_account(account_id),
    verified_by_user_id BIGINT REFERENCES sys_user(user_id),
    verified_at TIMESTAMP,
    rejection_reason VARCHAR(500),
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_property_service_organization_basis CHECK (
        service_basis IN ('PRELIMINARY_PROPERTY_SERVICE', 'OWNERS_ASSEMBLY_SELECTED')
    ),
    CONSTRAINT chk_property_service_organization_status CHECK (
        status IN ('DRAFT', 'PENDING_VERIFICATION', 'ACTIVE', 'REJECTED')
    ),
    CONSTRAINT chk_property_service_organization_dates CHECK (
        service_end_date IS NULL OR service_end_date >= service_start_date
    ),
    CONSTRAINT chk_property_service_organization_active_project CHECK (
        status <> 'ACTIVE' OR project_dept_id IS NOT NULL
    )
);

CREATE UNIQUE INDEX uk_property_service_organization_active_tenant
    ON t_property_service_organization(tenant_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_property_service_organization_tenant
    ON t_property_service_organization(tenant_id, create_time DESC, organization_id DESC);

COMMENT ON TABLE t_property_service_organization IS
    '小区与物业服务企业的登记关系；核验通过后才创建并启用本小区项目部';
COMMENT ON COLUMN t_property_service_organization.service_contact_name IS
    '物业服务联系人姓名，SM4 加密保存';
COMMENT ON COLUMN t_property_service_organization.service_contact_phone IS
    '物业服务联系人手机号，SM4 加密保存';
COMMENT ON COLUMN t_property_service_organization.project_dept_id IS
    '启用后创建的本小区物业项目部，物业经理和物业员工角色只能绑定此类部门';

CREATE TABLE t_property_service_organization_material (
    material_id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES t_property_service_organization(organization_id),
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
    CONSTRAINT chk_property_service_organization_material_type CHECK (
        material_type IN (
            'BUSINESS_LICENSE', 'PROPERTY_SERVICE_CONTRACT',
            'OWNERS_ASSEMBLY_DECISION', 'OTHER'
        )
    ),
    CONSTRAINT chk_property_service_organization_material_status CHECK (
        status IN ('ACTIVE', 'REMOVED')
    ),
    CONSTRAINT chk_property_service_organization_material_size CHECK (file_size > 0)
);

CREATE INDEX idx_property_service_organization_material
    ON t_property_service_organization_material(organization_id, status, create_time ASC, material_id ASC);

COMMENT ON TABLE t_property_service_organization_material IS
    '物业服务组织登记的私有材料，包括营业执照、物业服务合同和业主大会选聘决定';
COMMENT ON COLUMN t_property_service_organization_material.object_key IS
    '私有 OSS 对象键，不保存永久公开 URL';

CREATE TABLE t_property_service_organization_verification (
    verification_id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES t_property_service_organization(organization_id),
    legal_name_snapshot VARCHAR(120) NOT NULL,
    unified_social_credit_code_snapshot VARCHAR(18) NOT NULL,
    verification_method VARCHAR(32) NOT NULL,
    provider_code VARCHAR(64),
    source_code VARCHAR(64),
    provider_request_id VARCHAR(128),
    provider_result_code VARCHAR(64),
    verification_result VARCHAR(24) NOT NULL,
    business_status VARCHAR(64),
    result_message VARCHAR(500),
    inconsistent_fields_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    evidence_reference VARCHAR(500),
    remark VARCHAR(500),
    operator_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    operator_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    operator_role_key VARCHAR(50) NOT NULL,
    simulated SMALLINT NOT NULL DEFAULT 0,
    verified_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_property_service_organization_verification_method CHECK (
        verification_method IN ('PROPERTY_MANUAL', 'PLATFORM_API')
    ),
    CONSTRAINT chk_property_service_organization_verification_result CHECK (
        verification_result IN ('PASSED', 'REJECTED', 'ERROR')
    ),
    CONSTRAINT chk_property_service_organization_verification_channel CHECK (
        (verification_method = 'PROPERTY_MANUAL' AND source_code IS NOT NULL AND provider_code IS NULL)
        OR (verification_method = 'PLATFORM_API' AND provider_code IS NOT NULL AND source_code IS NULL)
    ),
    CONSTRAINT chk_property_service_organization_verification_simulated CHECK (simulated IN (0, 1)),
    CONSTRAINT chk_property_service_organization_verification_fields CHECK (
        jsonb_typeof(inconsistent_fields_json) = 'array'
    )
);

CREATE INDEX idx_property_service_organization_verification
    ON t_property_service_organization_verification(organization_id, verified_at DESC, verification_id DESC);

COMMENT ON TABLE t_property_service_organization_verification IS
    '物业服务企业主体核验不可变记录；模拟结果必须显式标记，且仅开发测试环境可用于演示状态流转，生产环境必须接入真实核验服务';

CREATE TABLE t_property_service_organization_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES t_property_service_organization(organization_id),
    actor_account_id BIGINT REFERENCES t_account(account_id),
    actor_user_id BIGINT REFERENCES sys_user(user_id),
    actor_dept_id BIGINT REFERENCES sys_dept(dept_id),
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    payload_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_property_service_organization_audit
    ON t_property_service_organization_audit(organization_id, create_time DESC, audit_id DESC);

COMMENT ON TABLE t_property_service_organization_audit IS
    '物业服务组织登记、材料、核验、项目部启用的业务审计流水';

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES
    ('property:service-organization:read', '查看本小区物业服务组织登记和核验记录', 'PROPERTY', 'GBS', 0),
    ('property:service-organization:submit', '登记和提交本小区物业服务组织核验材料', 'PROPERTY', 'GB', 1),
    ('property:service-organization:verify', '核验物业服务企业主体并启用本小区项目部', 'PROPERTY', 'G', 1)
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
        ('GOV_SUPER_ADMIN', 'property:service-organization:read'),
        ('GOV_SUPER_ADMIN', 'property:service-organization:submit'),
        ('GOV_SUPER_ADMIN', 'property:service-organization:verify'),
        ('COMMUNITY_ADMIN', 'property:service-organization:read'),
        ('COMMUNITY_ADMIN', 'property:service-organization:submit'),
        ('COMMUNITY_ADMIN', 'property:service-organization:verify'),
        ('COMMITTEE_DIRECTOR', 'property:service-organization:read'),
        ('COMMITTEE_DIRECTOR', 'property:service-organization:submit'),
        ('PROPERTY_MANAGER', 'property:service-organization:read'),
        ('PROPERTY_STAFF', 'property:service-organization:read')
) AS grant_row(role_key, permission_key) ON grant_row.role_key = role.role_key
ON CONFLICT (role_id, permission_key) DO NOTHING;

INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES (
    9100, 9000, 'property-service-organization', '物业服务组织', '/property-service-organization',
    NULL, 60, 1, '0', 'property:service-organization:read', NULL, NULL
) ON CONFLICT (menu_id) DO UPDATE SET
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

WITH property_organization_roles AS (
    SELECT role_id
    FROM sys_role
    WHERE role_key IN (
        'GOV_SUPER_ADMIN', 'COMMUNITY_ADMIN', 'COMMITTEE_DIRECTOR',
        'PROPERTY_MANAGER', 'PROPERTY_STAFF'
    )
)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM property_organization_roles
CROSS JOIN (VALUES (9000::BIGINT), (9100::BIGINT)) AS menu(menu_id)
ON CONFLICT (role_id, menu_id) DO NOTHING;

SELECT setval(
    'sys_menu_menu_id_seq',
    GREATEST((SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu), 9100),
    true
);
