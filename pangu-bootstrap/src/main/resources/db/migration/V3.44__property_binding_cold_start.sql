-- 冷启动房产绑定：物业基础名册底座 + 业主自主申报 + 居委会/业委会人工核销。

ALTER TABLE c_owner_property
    ADD COLUMN IF NOT EXISTS verify_type VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS verify_status VARCHAR(16) NOT NULL DEFAULT 'VERIFIED',
    ADD COLUMN IF NOT EXISTS verify_source VARCHAR(64),
    ADD COLUMN IF NOT EXISTS verified_by BIGINT REFERENCES sys_user(user_id),
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS superseded_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE c_owner_property
    DROP CONSTRAINT IF EXISTS chk_owner_property_verify_type,
    ADD CONSTRAINT chk_owner_property_verify_type CHECK (
        verify_type IN ('LEGACY', 'ROSTER_AUTO', 'MANUAL', 'GOV_API')
    );

ALTER TABLE c_owner_property
    DROP CONSTRAINT IF EXISTS chk_owner_property_verify_status,
    ADD CONSTRAINT chk_owner_property_verify_status CHECK (
        verify_status IN ('VERIFIED', 'SUPERSEDED', 'INVALIDATED')
    );

COMMENT ON COLUMN c_owner_property.verify_type IS '产权核验来源：LEGACY=历史数据，ROSTER_AUTO=导入名册自动吻合，MANUAL=人工核销，GOV_API=官方不动产接口';
COMMENT ON COLUMN c_owner_property.verify_status IS '产权核验状态；未来官方数据不一致时可强制置 INVALIDATED 并解绑';
COMMENT ON COLUMN c_owner_property.superseded_reason IS '人工/官方数据覆盖旧产权关系的留痕原因';

CREATE TABLE IF NOT EXISTS c_property_roster (
    roster_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    community_name VARCHAR(128) NOT NULL,
    building_id BIGINT NOT NULL,
    building_name VARCHAR(64) NOT NULL,
    unit_name VARCHAR(64) NOT NULL,
    room_id BIGINT NOT NULL,
    room_name VARCHAR(64) NOT NULL,
    build_area NUMERIC(10, 2) NOT NULL DEFAULT 0,
    registered_owner_name VARCHAR(64) NOT NULL,
    registered_owner_phone VARCHAR(20) NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'PROPERTY_BASE_ROSTER',
    import_batch_no VARCHAR(64),
    imported_by BIGINT REFERENCES sys_user(user_id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_property_roster_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_property_roster_area CHECK (build_area >= 0),
    CONSTRAINT uk_property_roster_room UNIQUE (tenant_id, building_name, unit_name, room_name)
);

CREATE INDEX IF NOT EXISTS idx_property_roster_tenant
    ON c_property_roster(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_property_roster_room_id
    ON c_property_roster(tenant_id, room_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_property_roster_owner_phone
    ON c_property_roster(registered_owner_phone) WHERE status = 'ACTIVE';

COMMENT ON TABLE c_property_roster IS '冷启动阶段物业/居委会导入的房屋及业主基础信息分母名册底座';
COMMENT ON COLUMN c_property_roster.registered_owner_name IS '物业基础名册登记业主姓名，冷启动对账暗号之一';
COMMENT ON COLUMN c_property_roster.registered_owner_phone IS '物业基础名册登记手机号，冷启动对账暗号之一';

CREATE TABLE IF NOT EXISTS c_owner_property_claim (
    claim_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    roster_id BIGINT REFERENCES c_property_roster(roster_id),
    building_id BIGINT,
    building_name VARCHAR(64) NOT NULL,
    unit_name VARCHAR(64) NOT NULL,
    room_id BIGINT,
    room_name VARCHAR(64) NOT NULL,
    applicant_real_name VARCHAR(64) NOT NULL,
    applicant_phone VARCHAR(20) NOT NULL,
    roster_owner_name VARCHAR(64),
    roster_owner_phone VARCHAR(20),
    match_result VARCHAR(16) NOT NULL,
    claim_status VARCHAR(24) NOT NULL,
    is_joint_ownership SMALLINT NOT NULL DEFAULT 0,
    is_voting_delegate SMALLINT NOT NULL DEFAULT 1,
    proof_type VARCHAR(32),
    proof_material_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    reject_reason_code VARCHAR(64),
    reject_reason VARCHAR(500),
    reviewed_by BIGINT REFERENCES sys_user(user_id),
    reviewed_at TIMESTAMP,
    bound_opid BIGINT REFERENCES c_owner_property(opid),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_property_claim_match CHECK (match_result IN ('EXACT', 'MISMATCH', 'MISSING')),
    CONSTRAINT chk_property_claim_status CHECK (
        claim_status IN ('AUTO_APPROVED', 'PENDING_VERIFY', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT chk_property_claim_flags CHECK (
        is_joint_ownership IN (0, 1) AND is_voting_delegate IN (0, 1)
    ),
    CONSTRAINT chk_property_claim_proof_type CHECK (
        proof_type IS NULL OR proof_type IN ('PROPERTY_CERT', 'SALE_CONTRACT', 'TAX_OR_UTILITY_INVOICE')
    )
);

CREATE INDEX IF NOT EXISTS idx_property_claim_uid
    ON c_owner_property_claim(uid, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_property_claim_admin_queue
    ON c_owner_property_claim(tenant_id, claim_status, create_time DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uidx_property_claim_pending
    ON c_owner_property_claim(uid, tenant_id, room_id)
    WHERE claim_status = 'PENDING_VERIFY' AND room_id IS NOT NULL;

COMMENT ON TABLE c_owner_property_claim IS '业主自主申报房产绑定单据；自动吻合直接绑定，不吻合进入人工核销挂起仓';
COMMENT ON COLUMN c_owner_property_claim.proof_material_json IS 'C端提交的物权证明照片/文件证据留痕 JSON';
COMMENT ON COLUMN c_owner_property_claim.match_result IS '实名姓名+手机号与基础名册暗号对账结果';

INSERT INTO c_property_roster (
    tenant_id, community_name, building_id, building_name, unit_name, room_id, room_name,
    build_area, registered_owner_name, registered_owner_phone, source_type, import_batch_no, status
)
SELECT op.tenant_id,
       COALESCE(tc.tenant_name, '小区 ' || op.tenant_id::TEXT),
       op.building_id,
       op.building_id::TEXT || ' 号楼',
       '默认单元',
       op.room_id,
       op.room_id::TEXT || ' 室',
       op.build_area,
       REGEXP_REPLACE(a.real_name, '^MOCK_', ''),
       a.phone,
       'LEGACY_OWNER_PROPERTY',
       'LEGACY-SEED',
       'ACTIVE'
FROM c_owner_property op
JOIN c_user cu ON cu.uid = op.uid
JOIN t_account a ON a.account_id = cu.account_id
LEFT JOIN t_tenant_community tc ON tc.tenant_id = op.tenant_id
ON CONFLICT (tenant_id, building_name, unit_name, room_name) DO NOTHING;

INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline)
VALUES
    ('property:roster:import', '导入物业基础房屋业主名册', 'OWNER', 'GS', 1),
    ('property:binding:review', '人工核销业主房产绑定申报', 'OWNER', 'GB', 0)
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT r.role_id, p.permission_key
FROM sys_role r
JOIN (
    VALUES
        ('GOV_SUPER_ADMIN', 'property:roster:import'),
        ('COMMUNITY_ADMIN', 'property:roster:import'),
        ('PARTY_SECRETARY', 'property:roster:import'),
        ('PROPERTY_MANAGER', 'property:roster:import'),
        ('PROPERTY_STAFF', 'property:roster:import'),
        ('GOV_SUPER_ADMIN', 'property:binding:review'),
        ('COMMUNITY_ADMIN', 'property:binding:review'),
        ('PARTY_SECRETARY', 'property:binding:review'),
        ('COMMITTEE_DIRECTOR', 'property:binding:review'),
        ('COMMITTEE_SECRETARY', 'property:binding:review')
) AS p(role_key, permission_key) ON p.role_key = r.role_key
ON CONFLICT (role_id, permission_key) DO NOTHING;

INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES
    (9070, 9000, 'property-roster-import', '小区空间名册导入', '/property-roster-import', NULL, 55, 1, '0',
     'property:roster:import', NULL, NULL),
    (9080, 9000, 'property-binding-review', '房产绑定审核', '/property-binding-review', NULL, 60, 1, '0',
     'property:binding:review', NULL, NULL)
ON CONFLICT (menu_id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    route_id = EXCLUDED.route_id,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    icon = EXCLUDED.icon,
    order_num = EXCLUDED.order_num,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    required_permission = EXCLUDED.required_permission,
    required_any_permissions = EXCLUDED.required_any_permissions,
    required_role_keys = EXCLUDED.required_role_keys;

WITH child_grants AS (
    SELECT DISTINCT r.role_id, m.menu_id, m.parent_id
    FROM sys_role r
    JOIN sys_menu m ON m.menu_id IN (9070, 9080)
    WHERE r.status = '0'
      AND (
          (m.required_permission IS NOT NULL AND EXISTS (
              SELECT 1
              FROM sys_role_permission rp
              WHERE rp.role_id = r.role_id
                AND rp.permission_key = m.required_permission
          ))
      )
),
all_grants AS (
    SELECT role_id, menu_id FROM child_grants
    UNION
    SELECT child_grants.role_id, parent.menu_id
    FROM child_grants
    JOIN sys_menu parent ON parent.menu_id = child_grants.parent_id
    WHERE parent.visible = 1
      AND parent.status = '0'
)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM all_grants
ON CONFLICT (role_id, menu_id) DO NOTHING;

SELECT setval('sys_menu_menu_id_seq',
              GREATEST((SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu), 9080),
              true);
