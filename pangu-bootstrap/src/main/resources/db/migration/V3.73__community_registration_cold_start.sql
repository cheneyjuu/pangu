-- 关联业务：建立小区注册申请、属地/平台审核、事务性租户开通和冷启动工作区。

CREATE SEQUENCE IF NOT EXISTS community_tenant_id_seq START WITH 20000 INCREMENT BY 1;

SELECT setval(
    'community_tenant_id_seq',
    GREATEST((SELECT COALESCE(MAX(tenant_id) + 1, 20000) FROM t_tenant_community), 20000),
    false
);

ALTER TABLE t_tenant_community
    ADD COLUMN registration_fingerprint CHAR(64);

CREATE UNIQUE INDEX uk_tenant_community_registration_fingerprint
    ON t_tenant_community(registration_fingerprint)
    WHERE registration_fingerprint IS NOT NULL;

COMMENT ON COLUMN t_tenant_community.registration_fingerprint IS '经审核注册时写入的小区名称地址去重指纹；历史租户允许为空';

ALTER TABLE sys_dept DROP CONSTRAINT chk_dept_category_type;
ALTER TABLE sys_dept
    ADD CONSTRAINT chk_dept_category_type CHECK (
        (dept_category = 'G' AND dept_type IN (1, 2, 5, 6, 12))
        OR (dept_category = 'B' AND dept_type IN (4, 10, 11))
        OR (dept_category = 'S' AND dept_type IN (3, 7, 8, 9))
    );

ALTER TABLE sys_dept DROP CONSTRAINT chk_dept_tenant_required;
ALTER TABLE sys_dept
    ADD CONSTRAINT chk_dept_tenant_required CHECK (
        dept_type IN (1, 3, 7, 8, 9)
        OR (dept_type IN (2, 4, 5, 6, 10, 11, 12) AND tenant_id IS NOT NULL)
    );

COMMENT ON COLUMN sys_dept.dept_type IS
    '1=街道办,2=居委会,3=物业,4=业委会,5=网格,6=党组织,7=绿化,8=保洁,9=其他服务商,10=志愿队,11=业主代表团,12=小区初始化工作区';

CREATE TABLE t_community_registration_application (
    application_id BIGSERIAL PRIMARY KEY,
    application_no VARCHAR(32) NOT NULL UNIQUE,
    applicant_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    applicant_name VARCHAR(512) NOT NULL,
    applicant_phone VARCHAR(20) NOT NULL,
    claimed_identity VARCHAR(40) NOT NULL,
    province_code VARCHAR(6) NOT NULL,
    province_name VARCHAR(64) NOT NULL,
    city_code VARCHAR(6) NOT NULL,
    city_name VARCHAR(64) NOT NULL,
    district_code VARCHAR(6) NOT NULL,
    district_name VARCHAR(64) NOT NULL,
    community_name VARCHAR(128) NOT NULL,
    community_address VARCHAR(256) NOT NULL,
    declared_household_count INT NOT NULL,
    community_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    review_mode VARCHAR(32),
    reviewer_account_id BIGINT REFERENCES t_account(account_id),
    reviewer_user_id BIGINT REFERENCES sys_user(user_id),
    reviewer_dept_id BIGINT REFERENCES sys_dept(dept_id),
    review_comment VARCHAR(1000),
    fallback_reason VARCHAR(1000),
    provisioned_tenant_id BIGINT REFERENCES t_tenant_community(tenant_id),
    version INT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_community_registration_identity CHECK (
        claimed_identity IN (
            'COMMITTEE_DIRECTOR', 'COMMITTEE_VICE_DIRECTOR', 'COMMITTEE_MEMBER',
            'OWNER', 'COMMUNITY_STAFF'
        )
    ),
    CONSTRAINT chk_community_registration_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'APPROVED', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT chk_community_registration_review_mode CHECK (
        review_mode IS NULL OR review_mode IN ('STREET', 'PLATFORM_FALLBACK')
    ),
    CONSTRAINT chk_community_registration_households CHECK (declared_household_count > 0),
    CONSTRAINT chk_community_registration_version CHECK (version >= 0),
    CONSTRAINT chk_community_registration_fallback_reason CHECK (
        review_mode <> 'PLATFORM_FALLBACK'
        OR fallback_reason IS NOT NULL
    ),
    CONSTRAINT chk_community_registration_approved_tenant CHECK (
        status <> 'APPROVED'
        OR provisioned_tenant_id IS NOT NULL
    )
);

CREATE UNIQUE INDEX uk_community_registration_active_fingerprint
    ON t_community_registration_application(community_fingerprint)
    WHERE status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'APPROVED');

CREATE INDEX idx_community_registration_applicant
    ON t_community_registration_application(applicant_account_id, create_time DESC);

CREATE INDEX idx_community_registration_review_queue
    ON t_community_registration_application(status, district_code, submitted_at, application_id);

COMMENT ON TABLE t_community_registration_application IS '小区注册申请；短信账号、小区真实性和业务身份分别审核';
COMMENT ON COLUMN t_community_registration_application.applicant_name IS '注册人申报姓名，使用 SM4 密文落盘';
COMMENT ON COLUMN t_community_registration_application.community_fingerprint IS '区县代码、规范化小区名称和地址的 SHA-256 去重指纹';
COMMENT ON COLUMN t_community_registration_application.review_mode IS 'STREET=属地街镇审核，PLATFORM_FALLBACK=街镇未接入时平台代审';

CREATE TABLE t_community_registration_housing_tag (
    application_id BIGINT NOT NULL REFERENCES t_community_registration_application(application_id),
    housing_tag VARCHAR(40) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_community_registration_housing_tag PRIMARY KEY (application_id, housing_tag),
    CONSTRAINT chk_community_registration_housing_tag CHECK (
        housing_tag IN ('SHOP', 'RELOCATION_HOUSING', 'COMMERCIAL_HOUSING', 'VILLA')
    )
);

COMMENT ON TABLE t_community_registration_housing_tag IS '注册阶段小区房屋概况标签，不作为逐套房屋法定分类';

CREATE TABLE t_community_registration_material (
    material_id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES t_community_registration_application(application_id),
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
    CONSTRAINT chk_community_registration_material_type CHECK (
        material_type IN (
            'COMMUNITY_EXISTENCE_PROOF', 'COMMITTEE_FILING', 'POSITION_PROOF',
            'OWNER_IDENTITY_PROOF', 'COMMUNITY_STAFF_PROOF', 'OTHER'
        )
    ),
    CONSTRAINT chk_community_registration_material_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT chk_community_registration_material_size CHECK (file_size > 0)
);

CREATE INDEX idx_community_registration_material_application
    ON t_community_registration_material(application_id, status, create_time DESC);

COMMENT ON TABLE t_community_registration_material IS '小区真实性与注册人申报身份的私有审核材料';
COMMENT ON COLUMN t_community_registration_material.object_key IS '私有 OSS 对象键，不保存永久公开 URL';

CREATE TABLE t_community_registration_review (
    review_id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES t_community_registration_application(application_id),
    decision VARCHAR(16) NOT NULL,
    review_mode VARCHAR(32) NOT NULL,
    reviewer_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    reviewer_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    reviewer_dept_id BIGINT NOT NULL REFERENCES sys_dept(dept_id),
    review_comment VARCHAR(1000),
    fallback_reason VARCHAR(1000),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_community_registration_review_decision CHECK (
        decision IN ('RETURN', 'APPROVE', 'REJECT')
    ),
    CONSTRAINT chk_community_registration_review_mode CHECK (
        review_mode IN ('STREET', 'PLATFORM_FALLBACK')
    ),
    CONSTRAINT chk_community_registration_review_fallback CHECK (
        review_mode <> 'PLATFORM_FALLBACK'
        OR fallback_reason IS NOT NULL
    )
);

CREATE INDEX idx_community_registration_review_application
    ON t_community_registration_review(application_id, create_time DESC, review_id DESC);

COMMENT ON TABLE t_community_registration_review IS '每轮注册审核决定、审核路径和平台代审依据';

CREATE TABLE t_community_onboarding_workspace (
    onboarding_id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL UNIQUE REFERENCES t_community_registration_application(application_id),
    tenant_id BIGINT NOT NULL UNIQUE REFERENCES t_tenant_community(tenant_id),
    status VARCHAR(32) NOT NULL DEFAULT 'FOUNDATION_PENDING',
    official_affiliation_status VARCHAR(64) NOT NULL,
    space_ledger_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    property_roster_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    denominator_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    owner_access_qr_status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    initialization_dept_id BIGINT NOT NULL REFERENCES sys_dept(dept_id),
    committee_dept_id BIGINT REFERENCES sys_dept(dept_id),
    applicant_work_user_id BIGINT REFERENCES sys_user(user_id),
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_community_onboarding_status CHECK (
        status IN ('FOUNDATION_PENDING', 'SPACE_LEDGER_PENDING', 'PROPERTY_ROSTER_PENDING',
                   'OWNER_ACCESS_READY', 'COMPLETED')
    ),
    CONSTRAINT chk_community_onboarding_affiliation CHECK (
        official_affiliation_status IN (
            'STREET_CONFIRMED', 'PLATFORM_REVIEWED_PENDING_STREET_CONFIRMATION'
        )
    ),
    CONSTRAINT chk_community_onboarding_sub_status CHECK (
        space_ledger_status IN ('PENDING', 'ACTIVE')
        AND property_roster_status IN ('PENDING', 'ACTIVE')
        AND denominator_status IN ('PENDING', 'PUBLISHED')
        AND owner_access_qr_status IN ('DISABLED', 'ACTIVE', 'REVOKED')
    )
);

COMMENT ON TABLE t_community_onboarding_workspace IS '小区冷启动工作区；租户技术启用不等于治理数据就绪';
COMMENT ON COLUMN t_community_onboarding_workspace.owner_access_qr_status IS '产权名册激活前保持 DISABLED，本迁移不生成二维码';

CREATE TABLE t_community_registration_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES t_community_registration_application(application_id),
    actor_account_id BIGINT REFERENCES t_account(account_id),
    actor_user_id BIGINT REFERENCES sys_user(user_id),
    actor_dept_id BIGINT REFERENCES sys_dept(dept_id),
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    payload_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_community_registration_audit_application
    ON t_community_registration_audit(application_id, create_time DESC, audit_id DESC);

COMMENT ON TABLE t_community_registration_audit IS '注册申请、材料、身份授权、代审与租户开通审计流水';

INSERT INTO sys_role (
    role_id, role_name, role_key, allowed_dept_category,
    fixed_data_scope, default_data_scope, is_system
) VALUES (
    15, '平台运营审核员', 'PLATFORM_OPERATOR', 'G',
    'ALL_COMMUNITY', 'ALL_COMMUNITY', 1
) ON CONFLICT (role_id) DO NOTHING;

SELECT setval(
    'sys_role_role_id_seq',
    GREATEST((SELECT COALESCE(MAX(role_id), 1) FROM sys_role), 100),
    true
);

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES
    ('community:registration:review', '属地街镇审核小区注册申请', 'COMMUNITY_REGISTRATION', 'G', 1),
    ('community:registration:platform-review', '街镇未接入时平台运营代审小区注册申请', 'COMMUNITY_REGISTRATION', 'G', 1)
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role.role_id, permission.permission_key
FROM sys_role role
JOIN (
    VALUES
        ('GOV_SUPER_ADMIN', 'community:registration:review'),
        ('PLATFORM_OPERATOR', 'community:registration:platform-review'),
        ('PLATFORM_OPERATOR', 'identity:switch')
) AS grant_row(role_key, permission_key) ON grant_row.role_key = role.role_key
JOIN sys_permission permission ON permission.permission_key = grant_row.permission_key
ON CONFLICT (role_id, permission_key) DO NOTHING;

INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES (
    9090, 9000, 'community-registration-review', '小区注册审核', '/community-registration-review',
    NULL, 5, 1, '0', NULL,
    'community:registration:review,community:registration:platform-review', NULL
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

WITH reviewer_roles AS (
    SELECT role_id
    FROM sys_role
    WHERE role_key IN ('GOV_SUPER_ADMIN', 'PLATFORM_OPERATOR')
)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM reviewer_roles
CROSS JOIN (VALUES (9000::BIGINT), (9090::BIGINT)) AS menu(menu_id)
ON CONFLICT (role_id, menu_id) DO NOTHING;

SELECT setval(
    'sys_menu_menu_id_seq',
    GREATEST((SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu), 9090),
    true
);

-- 街镇和平台受控审核人都可以在开通事务内给新居委会建立初始小区范围；
-- 居委会后续自行调整范围仍沿用 COMMUNITY_ADMIN 约束。
CREATE OR REPLACE FUNCTION fn_dts_check_community_scope() RETURNS TRIGGER AS $$
DECLARE
    v_dept_cat CHAR(1);
    v_dept_type SMALLINT;
    v_operator_role VARCHAR(50);
    v_operator_dept_cat CHAR(1);
    v_operator_dept_type SMALLINT;
BEGIN
    SELECT dept_category, dept_type
      INTO v_dept_cat, v_dept_type
      FROM sys_dept
     WHERE dept_id = NEW.dept_id;

    IF v_dept_cat <> 'G' OR v_dept_type <> 2 THEN
        RAISE EXCEPTION
            '[trigger community tenant scope] 只有 G 端 dept_type=2 居委会节点允许配置管辖小区，dept_id=%',
            NEW.dept_id;
    END IF;

    IF NEW.assigned_by IS NOT NULL THEN
        SELECT r.role_key, d.dept_category, d.dept_type
          INTO v_operator_role, v_operator_dept_cat, v_operator_dept_type
          FROM sys_user u
          JOIN sys_user_role ur ON ur.user_id = u.user_id
          JOIN sys_role r ON r.role_id = ur.role_id
          JOIN sys_dept d ON d.dept_id = u.dept_id
         WHERE u.user_id = NEW.assigned_by;

        IF v_operator_dept_cat <> 'G'
                OR NOT (
                    (v_operator_role = 'COMMUNITY_ADMIN' AND v_operator_dept_type = 2)
                    OR (v_operator_role IN ('GOV_SUPER_ADMIN', 'PLATFORM_OPERATOR')
                        AND v_operator_dept_type = 1)
                ) THEN
            RAISE EXCEPTION
                '[trigger community tenant scope] 小区范围只能由居委会、街镇或平台受控审核身份分配，assigned_by=%',
                NEW.assigned_by;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
