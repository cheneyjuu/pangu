-- ===================================================================
-- V1.4 — Permission 模型（RBAC 之上的能力点层）
-- 详见：M1权限体系重构设计.md §5.5
-- 核心：@PreAuthorize 永远只看 permission_key；将来 SaaS 管理员新建角色，
-- 给它勾选已存在的 permission 即可，业务代码 0 改动。
-- ===================================================================

-- -------------------------------------------------------------------
-- 1. sys_permission（业务能力点目录 — 代码稳定，不可被管理员修改 key）
-- -------------------------------------------------------------------
CREATE TABLE sys_permission (
    permission_key VARCHAR(64) PRIMARY KEY,
    description VARCHAR(200) NOT NULL,
    permission_group VARCHAR(32) NOT NULL,                 -- WAIVER / VOTING / FUND / IDENTITY / ADMIN
    allowed_dept_categories VARCHAR(3) NOT NULL,           -- 'G' / 'B' / 'S' / 'GB' / 'GBS' 字符位组合
    is_legal_redline SMALLINT NOT NULL DEFAULT 0,          -- 1=法理红线，trigger 6 必校验
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_permission IS '业务能力点目录（代码稳定）';
COMMENT ON COLUMN sys_permission.permission_key IS 'feature:resource:action 形式，如 waiver:approve:committee';
COMMENT ON COLUMN sys_permission.allowed_dept_categories IS
    '字符位组合：G=政务、B=业主、S=服务商；GB=G或B；GBS=全端可挂';
COMMENT ON COLUMN sys_permission.is_legal_redline IS '1=法理红线，role.fixed_data_scope 必须 NOT NULL（trigger 6）';

-- -------------------------------------------------------------------
-- 2. sys_role_permission（角色 ↔ 能力点 多对多）
-- -------------------------------------------------------------------
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL REFERENCES sys_role(role_id) ON DELETE CASCADE,
    permission_key VARCHAR(64) NOT NULL REFERENCES sys_permission(permission_key),
    granted_by BIGINT,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_key)
);
CREATE INDEX idx_role_permission_role ON sys_role_permission(role_id);

COMMENT ON TABLE sys_role_permission IS '角色 ↔ 能力点（管理员可在后台动态调整，但不能删除 sys_role.is_system=1 的角色）';

-- -------------------------------------------------------------------
-- 3. M1 范围 19 项 permission 种子（详见 §5.5.5）
-- -------------------------------------------------------------------
INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('waiver:submit',              '发起党员比例放宽申请',     'WAIVER',   'G',   1),
    ('waiver:approve:committee',   '居委会初审 waiver',        'WAIVER',   'G',   1),
    ('waiver:approve:street',      '街道办终审 waiver',        'WAIVER',   'G',   1),
    ('waiver:revoke',              '撤销 waiver',              'WAIVER',   'G',   1),
    ('waiver:read',                '查看 waiver 列表 / 详情',  'WAIVER',   'GBS', 0),
    ('voting:subject:create',      '创建投票议题',             'VOTING',   'GB',  0),
    ('voting:subject:publish',     '公示候选人 / 议题',        'VOTING',   'GB',  0),
    ('voting:subject:settle',      '触发结算',                 'VOTING',   'G',   0),
    ('candidate:nominate',         '候选人提名',               'VOTING',   'GB',  0),
    ('candidate:approve',          '候选人资格初审',           'VOTING',   'G',   0),
    ('fund:account:read',          '维修资金账户查询',         'FUND',     'GBS', 0),
    ('fund:disclosure:publish',    '财务公示',                 'FUND',     'GB',  0),
    ('identity:switch',            '切换工作身份',             'IDENTITY', 'GBS', 0),
    ('admin:role:read',            '查看角色配置',             'ADMIN',    'G',   0),
    ('admin:role:write',           '编辑角色 / 角色-权限关系', 'ADMIN',    'G',   1),
    ('admin:user:assign-role',     '给用户分配角色',           'ADMIN',    'G',   1),
    ('admin:user:building:assign', '任命楼栋（业主代表/网格员）','ADMIN',   'G',   1);
-- 注：voting:cast / identity:tenant:switch 是 C 端权限，走 OwnerSelfFilter，不进 sys_permission。

-- -------------------------------------------------------------------
-- 4. Trigger 6: sys_role_permission BEFORE INSERT/UPDATE
--    role.allowed_dept_category 必须出现在 permission.allowed_dept_categories；
--    is_legal_redline=1 时要求 role.fixed_data_scope NOT NULL。
-- 详见：§5.5.9 + §8 trigger 6
-- -------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_srp_check_consistency() RETURNS TRIGGER AS $$
DECLARE
    v_role_cat CHAR(1);
    v_role_fixed VARCHAR(16);
    v_role_key VARCHAR(50);
    v_perm_cats VARCHAR(3);
    v_perm_redline SMALLINT;
BEGIN
    SELECT allowed_dept_category, fixed_data_scope, role_key
      INTO v_role_cat, v_role_fixed, v_role_key
      FROM sys_role WHERE role_id = NEW.role_id;
    SELECT allowed_dept_categories, is_legal_redline
      INTO v_perm_cats, v_perm_redline
      FROM sys_permission WHERE permission_key = NEW.permission_key;

    IF position(v_role_cat IN v_perm_cats) = 0 THEN
        RAISE EXCEPTION
            '[trigger 6] role % (cat=%) 与 permission % (allowed=%) 端归属不一致',
            v_role_key, v_role_cat, NEW.permission_key, v_perm_cats;
    END IF;
    IF v_perm_redline = 1 AND v_role_fixed IS NULL THEN
        RAISE EXCEPTION
            '[trigger 6] permission % 是法理红线，但 role % 未锁定 fixed_data_scope',
            NEW.permission_key, v_role_key;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_srp_check_consistency
    BEFORE INSERT OR UPDATE ON sys_role_permission
    FOR EACH ROW EXECUTE FUNCTION fn_srp_check_consistency();

-- -------------------------------------------------------------------
-- 5. Trigger 7: sys_role BEFORE DELETE — is_system=1 拒删
-- 详见：§8 trigger 7
-- -------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_role_check_is_system() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.is_system = 1 THEN
        RAISE EXCEPTION
            '[trigger 7] 预置系统角色 role_key=% 不可删除',
            OLD.role_key;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_role_check_is_system
    BEFORE DELETE ON sys_role
    FOR EACH ROW EXECUTE FUNCTION fn_role_check_is_system();

-- -------------------------------------------------------------------
-- 6. 13 个预置 system role 的初始 permission 映射（详见 §5.5.6）
--    为防止 trigger 6 因 redline 校验失败，COMMITTEE_DIRECTOR / OWNER_REPRESENTATIVE /
--    PROPERTY_MANAGER / PROPERTY_STAFF（fixed 锁死）只挂自身 cat 内的非红线 permission；
--    红线 admin:* / waiver:* 全部挂在 G 端 fixed 已锁死的 GOV_SUPER_ADMIN /
--    PARTY_SECRETARY / COMMUNITY_ADMIN / GRID_OPERATOR 上（COMMUNITY_ADMIN 虽 fixed=NULL
--    但其默认 ALL_COMMUNITY 等价；按设计文档 §5.5.6 的映射执行）。
--    注：COMMUNITY_ADMIN（fixed=NULL）拥有 waiver:submit/waiver:approve:committee/
--    waiver:revoke 这三个 redline=1 permission，需要先设置 fixed_data_scope=ALL_COMMUNITY，
--    否则 trigger 6 会拒绝。详细处理：本期 COMMUNITY_ADMIN 提升为 fixed_data_scope 锁死。
-- -------------------------------------------------------------------

-- ★ 修订：COMMUNITY_ADMIN / GRID_OPERATOR / PARTY_SECRETARY 都需要承接 redline=1 的
--   waiver / admin permission。GRID_OPERATOR / PARTY_SECRETARY fixed 已 NOT NULL；
--   COMMUNITY_ADMIN 在 V1 schema 设为 fixed=NULL，这里需要补一刀：把它的 fixed
--   提升到 ALL_COMMUNITY 与 default 一致，trigger 6 通过。
UPDATE sys_role
   SET fixed_data_scope = 'ALL_COMMUNITY'
 WHERE role_key = 'COMMUNITY_ADMIN';

-- GOV_SUPER_ADMIN: waiver:* / voting:subject:settle / candidate:approve / fund:* / admin:* / identity:switch
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (1, 'waiver:submit'), (1, 'waiver:approve:committee'), (1, 'waiver:approve:street'),
    (1, 'waiver:revoke'), (1, 'waiver:read'),
    (1, 'voting:subject:settle'), (1, 'candidate:approve'),
    (1, 'fund:account:read'), (1, 'fund:disclosure:publish'),
    (1, 'admin:role:read'), (1, 'admin:role:write'),
    (1, 'admin:user:assign-role'), (1, 'admin:user:building:assign'),
    (1, 'identity:switch');

-- COMMUNITY_ADMIN: waiver:submit / waiver:approve:committee / waiver:revoke / waiver:read /
--                  voting:subject:create / voting:subject:publish / candidate:approve / identity:switch
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (2, 'waiver:submit'), (2, 'waiver:approve:committee'),
    (2, 'waiver:revoke'), (2, 'waiver:read'),
    (2, 'voting:subject:create'), (2, 'voting:subject:publish'),
    (2, 'candidate:approve'), (2, 'identity:switch');

-- PARTY_SECRETARY: waiver:read / voting:* / candidate:* / fund:account:read / fund:disclosure:publish / identity:switch
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (3, 'waiver:read'),
    (3, 'voting:subject:create'), (3, 'voting:subject:publish'), (3, 'voting:subject:settle'),
    (3, 'candidate:nominate'), (3, 'candidate:approve'),
    (3, 'fund:account:read'), (3, 'fund:disclosure:publish'),
    (3, 'identity:switch');

-- GRID_OPERATOR: waiver:read / voting:subject:publish / candidate:nominate / fund:account:read / identity:switch
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (4, 'waiver:read'),
    (4, 'voting:subject:publish'),
    (4, 'candidate:nominate'),
    (4, 'fund:account:read'),
    (4, 'identity:switch');

-- COMMITTEE_DIRECTOR (B, fixed=ALL_COMMUNITY ★)
--   B 端不能挂 waiver:* / candidate:approve / voting:subject:settle / admin:*（这些是 G 端权限）
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (5, 'voting:subject:create'), (5, 'voting:subject:publish'),
    (5, 'candidate:nominate'),
    (5, 'fund:disclosure:publish'), (5, 'fund:account:read'),
    (5, 'waiver:read'),
    (5, 'identity:switch');

-- COMMITTEE_MEMBER (B)
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (6, 'voting:subject:create'), (6, 'candidate:nominate'),
    (6, 'fund:account:read'), (6, 'waiver:read'),
    (6, 'identity:switch');

-- COMMITTEE_SECRETARY (B)
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (7, 'voting:subject:create'),
    (7, 'fund:account:read'), (7, 'waiver:read'),
    (7, 'identity:switch');

-- OWNER_REPRESENTATIVE (B, fixed=OWNER_GROUP ★)
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (8, 'candidate:nominate'),
    (8, 'fund:account:read'), (8, 'waiver:read'),
    (8, 'identity:switch');

-- VOLUNTEER (B)
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (9, 'fund:account:read'), (9, 'waiver:read'),
    (9, 'identity:switch');

-- PROPERTY_MANAGER (S, fixed=ORG_ONLY ★)
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (10, 'fund:account:read'), (10, 'waiver:read'),
    (10, 'identity:switch');

-- PROPERTY_STAFF (S, fixed=ORG_ONLY ★)
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (11, 'fund:account:read'), (11, 'waiver:read'),
    (11, 'identity:switch');

-- SERVICE_PROVIDER_MANAGER (S, fixed=ORG_ONLY ★)
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (12, 'fund:account:read'), (12, 'waiver:read'),
    (12, 'identity:switch');

-- SERVICE_PROVIDER_STAFF (S, fixed=ORG_ONLY ★)
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (13, 'fund:account:read'), (13, 'waiver:read'),
    (13, 'identity:switch');
