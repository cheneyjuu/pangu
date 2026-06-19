-- ===================================================================
-- M1 RBAC 重构 — 自然人三层模型 + 三端归属 + Permission 模型
-- 详见：M1权限体系重构设计.md §4.4 / §7
-- ===================================================================

-- ===================================================================
-- 1. 自然人主体表 (t_account)
--    一手机号 = 一自然人；下挂 sys_user（管理端工作账号）/ c_user（C 端业主身份）
-- ===================================================================
CREATE TABLE t_account (
    account_id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) NOT NULL UNIQUE,
    real_name VARCHAR(128) NOT NULL,                       -- SM4 加密密文
    id_card_encrypted VARCHAR(128),                        -- SM4 加密密文
    real_name_verified SMALLINT NOT NULL DEFAULT 0,        -- 0=未实名, 1=已实名
    last_active_identity_id BIGINT,                        -- 切卡：上次活跃身份 ID（user_id 或 uid）
    last_active_identity_type VARCHAR(16),                 -- SYS_USER | C_USER
    status SMALLINT NOT NULL DEFAULT 1,                    -- 1=正常, 2=禁用, 3=注销
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_account_identity_type CHECK (
        last_active_identity_type IS NULL
     OR last_active_identity_type IN ('SYS_USER','C_USER')
    )
);
CREATE INDEX idx_account_phone ON t_account(phone);

COMMENT ON TABLE t_account IS '自然人主体表（手机号唯一，挂载 sys_user/c_user 多身份）';
COMMENT ON COLUMN t_account.account_id IS '自然人 ID';
COMMENT ON COLUMN t_account.phone IS '登录手机号（同一人多端通用）';
COMMENT ON COLUMN t_account.real_name IS '真实姓名 SM4 密文';
COMMENT ON COLUMN t_account.id_card_encrypted IS '身份证号 SM4 密文';
COMMENT ON COLUMN t_account.real_name_verified IS '实名状态：0=未实名, 1=已实名';
COMMENT ON COLUMN t_account.last_active_identity_id IS '上次活跃身份 ID（switch-identity 状态）';
COMMENT ON COLUMN t_account.last_active_identity_type IS 'SYS_USER 或 C_USER';
COMMENT ON COLUMN t_account.status IS '1=正常, 2=禁用, 3=注销';

-- ===================================================================
-- 2. 组织机构部门树 (sys_dept) — dept_category 三端归属 + tenant_id 三态
-- ===================================================================
CREATE TABLE sys_dept (
    dept_id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES sys_dept(dept_id),
    ancestors VARCHAR(500) NOT NULL DEFAULT '',            -- 物化路径 '1,105,101'
    dept_name VARCHAR(50) NOT NULL,
    dept_type SMALLINT NOT NULL,                           -- 1-11 见 §4.1
    dept_category CHAR(1) NOT NULL,                        -- G/B/S 三端归属
    tenant_id BIGINT,                                      -- 三态：NULL = 跨租户根（街道办 / 跨小区集团服务商）
    order_num INT NOT NULL DEFAULT 0,
    status CHAR(1) NOT NULL DEFAULT '0',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_dept_category_type CHECK (
        (dept_category = 'G' AND dept_type IN (1,2,5,6))
     OR (dept_category = 'B' AND dept_type IN (4,10,11))
     OR (dept_category = 'S' AND dept_type IN (3,7,8,9))
    ),
    CONSTRAINT chk_dept_tenant_required CHECK (
        (dept_type IN (1,3,7,8,9))                         -- 街道办 / 跨小区服务商：tenant_id 可 NULL
     OR (dept_type IN (2,4,5,6,10,11) AND tenant_id IS NOT NULL)  -- 单租户主体：必填
    )
);
CREATE INDEX idx_dept_parent ON sys_dept(parent_id);
CREATE INDEX idx_dept_ancestors ON sys_dept(ancestors);
CREATE INDEX idx_dept_tenant ON sys_dept(tenant_id);

COMMENT ON TABLE sys_dept IS '组织机构部门树（G/B/S 三端归属 + 物化路径）';
COMMENT ON COLUMN sys_dept.dept_type IS
    '1=街道办,2=居委会,3=物业,4=业委会,5=网格,6=党组织,7=绿化,8=保洁,9=其他服务商,10=志愿队,11=业主代表团';
COMMENT ON COLUMN sys_dept.dept_category IS 'G=政务监管 / B=业主自治 / S=服务供应';
COMMENT ON COLUMN sys_dept.tenant_id IS 'SaaS 租户 ID；NULL=跨租户根（街道办 / 跨小区服务商）';
COMMENT ON COLUMN sys_dept.ancestors IS '物化路径，逗号分隔从根到父，便于 LIKE 反查';

-- ===================================================================
-- 3. 管理端工作账号 / 影子分身 (sys_user)
--    一自然人 + 一部门 = 一个工作身份；同一 account_id 可挂多个 dept 的多个 sys_user
-- ===================================================================
CREATE TABLE sys_user (
    user_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    dept_id BIGINT NOT NULL REFERENCES sys_dept(dept_id),
    user_name VARCHAR(50) NOT NULL,
    nick_name VARCHAR(50),
    avatar VARCHAR(255),
    status CHAR(1) NOT NULL DEFAULT '0',                   -- 0=正常, 1=停用
    last_login_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_account_dept UNIQUE (account_id, dept_id)
);
CREATE INDEX idx_sys_user_account ON sys_user(account_id);
CREATE INDEX idx_sys_user_dept ON sys_user(dept_id);

COMMENT ON TABLE sys_user IS '管理端工作账号（同一自然人在不同 dept 下可有多个分身）';
COMMENT ON COLUMN sys_user.account_id IS '关联 t_account.account_id';
COMMENT ON COLUMN sys_user.dept_id IS '所属部门';
COMMENT ON COLUMN sys_user.status IS '0=正常, 1=停用';

-- ===================================================================
-- 4. C 端业主身份 (c_user)
-- ===================================================================
CREATE TABLE c_user (
    uid BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE REFERENCES t_account(account_id),
    auth_level SMALLINT NOT NULL DEFAULT 1,                -- L1/L2/L3 业主语义专属
    last_active_tenant_id BIGINT,                          -- switch-tenant 状态
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE c_user IS 'C 端业主身份（一自然人至多一条；手机号 / 实名等公共字段反查 t_account）';
COMMENT ON COLUMN c_user.auth_level IS 'L1=基础绑定, L2=身份证, L3=人脸活体';
COMMENT ON COLUMN c_user.last_active_tenant_id IS '上次活跃小区 tenant_id（switch-tenant 状态）';

-- ===================================================================
-- 5. C 端业主房产映射关系 (c_owner_property)
-- ===================================================================
CREATE TABLE c_owner_property (
    opid BIGSERIAL PRIMARY KEY,
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    tenant_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    build_area DECIMAL(10,2) NOT NULL,
    is_joint_ownership SMALLINT NOT NULL DEFAULT 0,
    is_voting_delegate SMALLINT NOT NULL DEFAULT 1,
    account_status SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_c_owner_property_uid ON c_owner_property(uid);
CREATE INDEX idx_c_owner_property_tenant ON c_owner_property(tenant_id);
CREATE INDEX idx_c_owner_property_building ON c_owner_property(building_id);
CREATE UNIQUE INDEX uidx_tenant_room_owner ON c_owner_property(tenant_id, room_id, uid);

COMMENT ON TABLE c_owner_property IS 'C 端业主房产绑定关系（共有产权一房多 uid，分母解析按 room_id 去重）';
COMMENT ON COLUMN c_owner_property.is_joint_ownership IS '0=独立产权, 1=共有产权';
COMMENT ON COLUMN c_owner_property.is_voting_delegate IS '0=非代表, 1=指定的投票代表';
COMMENT ON COLUMN c_owner_property.account_status IS '1=正常, 2=欠费挂起, 3=冻结';

-- ===================================================================
-- 6. 角色表 (sys_role) — allowed_dept_category + fixed/default_data_scope + is_system
--    详见：§7.6 + §5.5.4
-- ===================================================================
CREATE TABLE sys_role (
    role_id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    role_key VARCHAR(50) NOT NULL UNIQUE,
    allowed_dept_category CHAR(1) NOT NULL
        CHECK (allowed_dept_category IN ('G','B','S')),
    fixed_data_scope VARCHAR(16)
        CHECK (fixed_data_scope IS NULL
            OR fixed_data_scope IN ('ALL_COMMUNITY','OWNER_GROUP','ORG_ONLY')),
    default_data_scope VARCHAR(16) NOT NULL
        CHECK (default_data_scope IN ('ALL_COMMUNITY','OWNER_GROUP','ORG_ONLY')),
    is_system SMALLINT NOT NULL DEFAULT 0,                 -- 1=预置系统角色，trigger 7 拒绝删除
    status CHAR(1) NOT NULL DEFAULT '0',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_role_scope_consistency CHECK (
        fixed_data_scope IS NULL OR fixed_data_scope = default_data_scope
    )
);

COMMENT ON TABLE sys_role IS '角色表（allowed_dept_category 限定可挂端，fixed_data_scope 法理红线锁死）';
COMMENT ON COLUMN sys_role.allowed_dept_category IS '只能挂在该端的 dept 上：G/B/S';
COMMENT ON COLUMN sys_role.fixed_data_scope IS 'NOT NULL 时锁死 effective_data_scope，无法降级（法理红线）';
COMMENT ON COLUMN sys_role.default_data_scope IS '初始 effective_data_scope；管理员未指定时使用此值';
COMMENT ON COLUMN sys_role.is_system IS '1=预置 system role 不可删除，0=管理员自建';

-- ===================================================================
-- 7. 13 个预置 system role 种子（is_system=1）
-- ===================================================================
INSERT INTO sys_role (role_id, role_name, role_key, allowed_dept_category,
                      fixed_data_scope, default_data_scope, is_system) VALUES
    (1,  '街道办超管',           'GOV_SUPER_ADMIN',           'G', 'ALL_COMMUNITY', 'ALL_COMMUNITY', 1),
    (2,  '居委会管理员',          'COMMUNITY_ADMIN',           'G', NULL,            'ALL_COMMUNITY', 1),
    (3,  '党组织书记',            'PARTY_SECRETARY',           'G', 'ALL_COMMUNITY', 'ALL_COMMUNITY', 1),
    (4,  '网格员',                'GRID_OPERATOR',             'G', 'OWNER_GROUP',   'OWNER_GROUP',   1),
    (5,  '业委会主任',            'COMMITTEE_DIRECTOR',        'B', 'ALL_COMMUNITY', 'ALL_COMMUNITY', 1),
    (6,  '业委会委员',            'COMMITTEE_MEMBER',          'B', NULL,            'ALL_COMMUNITY', 1),
    (7,  '业委会秘书',            'COMMITTEE_SECRETARY',       'B', NULL,            'ALL_COMMUNITY', 1),
    (8,  '业主代表',              'OWNER_REPRESENTATIVE',      'B', 'OWNER_GROUP',   'OWNER_GROUP',   1),
    (9,  '志愿者',                'VOLUNTEER',                 'B', NULL,            'OWNER_GROUP',   1),
    (10, '物业经理',              'PROPERTY_MANAGER',          'S', 'ORG_ONLY',      'ORG_ONLY',      1),
    (11, '物业员工',              'PROPERTY_STAFF',            'S', 'ORG_ONLY',      'ORG_ONLY',      1),
    (12, '服务商负责人',          'SERVICE_PROVIDER_MANAGER',  'S', 'ORG_ONLY',      'ORG_ONLY',      1),
    (13, '服务商员工',            'SERVICE_PROVIDER_STAFF',    'S', 'ORG_ONLY',      'ORG_ONLY',      1);

-- 重置序列到 100，留 1-99 给后续预置角色扩展
SELECT setval('sys_role_role_id_seq', 100, false);
