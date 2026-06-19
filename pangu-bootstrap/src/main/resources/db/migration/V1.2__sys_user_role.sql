-- ===================================================================
-- V1.2 — 用户-角色绑定 + 楼栋反查权威表 + Trigger 兜底矩阵 1-5
-- 详见：M1权限体系重构设计.md §7.7 / §7.8 / §8
-- ===================================================================

-- -------------------------------------------------------------------
-- 1. sys_user_role（一人一角色 — 单列 PK）
--    effective_data_scope：管理员可在 default_data_scope 基础上降级，但
--    若 sys_role.fixed_data_scope NOT NULL 则被 trigger 3 锁死。
-- -------------------------------------------------------------------
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL REFERENCES sys_role(role_id),
    effective_data_scope VARCHAR(16) NOT NULL
        CHECK (effective_data_scope IN ('ALL_COMMUNITY','OWNER_GROUP','ORG_ONLY')),
    granted_by BIGINT,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sys_user_role PRIMARY KEY (user_id),  -- ★ 单列 PK：一人一角色
    CONSTRAINT fk_sur_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE CASCADE
);

COMMENT ON TABLE sys_user_role IS '用户-角色绑定（一人一角色）';
COMMENT ON COLUMN sys_user_role.effective_data_scope IS '生效 data_scope；fixed_data_scope NOT NULL 时由 trigger 3 锁死';
COMMENT ON COLUMN sys_user_role.granted_by IS '授权操作人 user_id';

-- -------------------------------------------------------------------
-- 2. sys_user_building（楼栋反查权威）
--    业主代表 / 网格员 / 志愿者 OWNER_GROUP scope 的真实管辖楼栋来源；
--    OwnerSelfFilter 也通过此表反查 c_user.uid → 实际有权限的 building_id。
-- -------------------------------------------------------------------
CREATE TABLE sys_user_building (
    assignment_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(user_id) ON DELETE CASCADE,
    building_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    assigned_by BIGINT,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    status SMALLINT NOT NULL DEFAULT 1,                    -- 1=生效, 2=已撤销
    revoke_reason VARCHAR(200)
);
CREATE INDEX idx_user_building_user ON sys_user_building(user_id) WHERE status = 1;
CREATE INDEX idx_user_building_building ON sys_user_building(building_id) WHERE status = 1;
CREATE INDEX idx_user_building_tenant ON sys_user_building(tenant_id) WHERE status = 1;

COMMENT ON TABLE sys_user_building IS '楼栋反查权威表（OWNER_GROUP scope 数据范围来源）';
COMMENT ON COLUMN sys_user_building.status IS '1=生效, 2=已撤销';

-- ===================================================================
-- Trigger 1: sys_user_role BEFORE INSERT/UPDATE — 角色 ↔ 部门 端归属一致性
--   role.allowed_dept_category 必须 = user.dept.dept_category；
--   特殊角色对 dept_type 的硬性要求（OWNER_REPRESENTATIVE / VOLUNTEER / GRID_OPERATOR）。
-- 详见：§8 trigger 1
-- ===================================================================
CREATE OR REPLACE FUNCTION fn_sur_check_category() RETURNS TRIGGER AS $$
DECLARE
    v_role_key VARCHAR(50);
    v_allowed_cat CHAR(1);
    v_dept_cat CHAR(1);
    v_dept_type SMALLINT;
BEGIN
    SELECT role_key, allowed_dept_category
      INTO v_role_key, v_allowed_cat
      FROM sys_role WHERE role_id = NEW.role_id;
    SELECT d.dept_category, d.dept_type
      INTO v_dept_cat, v_dept_type
      FROM sys_user u JOIN sys_dept d ON d.dept_id = u.dept_id
     WHERE u.user_id = NEW.user_id;

    IF v_allowed_cat <> v_dept_cat THEN
        RAISE EXCEPTION
            '[trigger 1] role % 限定 % 端，但用户所在部门类别为 %',
            v_role_key, v_allowed_cat, v_dept_cat;
    END IF;
    IF v_role_key = 'OWNER_REPRESENTATIVE' AND v_dept_type <> 11 THEN
        RAISE EXCEPTION
            '[trigger 1] OWNER_REPRESENTATIVE 必须挂在 dept_type=11（业主代表团），实际=%',
            v_dept_type;
    END IF;
    IF v_role_key = 'VOLUNTEER' AND v_dept_type <> 10 THEN
        RAISE EXCEPTION
            '[trigger 1] VOLUNTEER 必须挂在 dept_type=10（志愿队），实际=%', v_dept_type;
    END IF;
    IF v_role_key = 'GRID_OPERATOR' AND v_dept_type <> 5 THEN
        RAISE EXCEPTION
            '[trigger 1] GRID_OPERATOR 必须挂在 dept_type=5（网格），实际=%', v_dept_type;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sur_check_category
    BEFORE INSERT OR UPDATE ON sys_user_role
    FOR EACH ROW EXECUTE FUNCTION fn_sur_check_category();

-- ===================================================================
-- Trigger 2: sys_user_role AFTER INSERT/UPDATE — DEFERRED
--   OWNER_REPRESENTATIVE / GRID_OPERATOR / VOLUNTEER 必须有至少 1 条
--   生效 sys_user_building，否则事务结束时报错。
-- 详见：§8 trigger 2 + §8.1 DEFERRED 必要性
-- ===================================================================
CREATE OR REPLACE FUNCTION fn_sur_require_building() RETURNS TRIGGER AS $$
DECLARE
    v_role_key VARCHAR(50);
    v_active_count BIGINT;
BEGIN
    SELECT role_key INTO v_role_key FROM sys_role WHERE role_id = NEW.role_id;
    IF v_role_key NOT IN ('OWNER_REPRESENTATIVE','GRID_OPERATOR','VOLUNTEER') THEN
        RETURN NEW;
    END IF;
    SELECT COUNT(*) INTO v_active_count
      FROM sys_user_building
     WHERE user_id = NEW.user_id AND status = 1;
    IF v_active_count = 0 THEN
        RAISE EXCEPTION
            '[trigger 2] role % 必须至少绑定 1 个生效楼栋，user_id=%',
            v_role_key, NEW.user_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_sur_require_building
    AFTER INSERT OR UPDATE ON sys_user_role
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_sur_require_building();

-- ===================================================================
-- Trigger 3: sys_user_role BEFORE INSERT/UPDATE — fixed_data_scope 锁死
-- 详见：§8 trigger 3
-- ===================================================================
CREATE OR REPLACE FUNCTION fn_sur_check_fixed_scope() RETURNS TRIGGER AS $$
DECLARE
    v_fixed VARCHAR(16);
BEGIN
    SELECT fixed_data_scope INTO v_fixed FROM sys_role WHERE role_id = NEW.role_id;
    IF v_fixed IS NOT NULL AND NEW.effective_data_scope <> v_fixed THEN
        RAISE EXCEPTION
            '[trigger 3] role 法理红线锁死 fixed_data_scope=%，effective_data_scope=% 不允许',
            v_fixed, NEW.effective_data_scope;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sur_check_fixed_scope
    BEFORE INSERT OR UPDATE ON sys_user_role
    FOR EACH ROW EXECUTE FUNCTION fn_sur_check_fixed_scope();

-- ===================================================================
-- Trigger 4: sys_dept BEFORE INSERT/UPDATE — parent.tenant_id 一致性
--   parent_id 的 tenant_id 必须为 NULL（跨租户根）或与自身相等。
-- 详见：§8 trigger 4
-- ===================================================================
CREATE OR REPLACE FUNCTION fn_dept_check_tenant_consistency() RETURNS TRIGGER AS $$
DECLARE
    v_parent_tenant BIGINT;
BEGIN
    IF NEW.parent_id IS NULL THEN
        RETURN NEW;  -- 顶层节点
    END IF;
    SELECT tenant_id INTO v_parent_tenant FROM sys_dept WHERE dept_id = NEW.parent_id;
    IF v_parent_tenant IS NULL THEN
        RETURN NEW;  -- 父级是跨租户根（街道办），任何子租户都允许
    END IF;
    IF NEW.tenant_id IS NULL OR NEW.tenant_id <> v_parent_tenant THEN
        RAISE EXCEPTION
            '[trigger 4] dept_id=% tenant_id=% 与 parent tenant_id=% 不一致',
            NEW.dept_id, NEW.tenant_id, v_parent_tenant;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dept_check_tenant_consistency
    BEFORE INSERT OR UPDATE ON sys_dept
    FOR EACH ROW EXECUTE FUNCTION fn_dept_check_tenant_consistency();

-- ===================================================================
-- Trigger 5: sys_user_building BEFORE INSERT/UPDATE — tenant_id 必须 = user.dept.tenant_id
--   街道办用户（dept.tenant_id IS NULL）禁止任命楼栋。
-- 详见：§8 trigger 5
-- ===================================================================
CREATE OR REPLACE FUNCTION fn_sub_check_tenant() RETURNS TRIGGER AS $$
DECLARE
    v_user_tenant BIGINT;
BEGIN
    SELECT d.tenant_id INTO v_user_tenant
      FROM sys_user u JOIN sys_dept d ON d.dept_id = u.dept_id
     WHERE u.user_id = NEW.user_id;
    IF v_user_tenant IS NULL THEN
        RAISE EXCEPTION
            '[trigger 5] 街道办用户（dept.tenant_id NULL）禁止任命楼栋，user_id=%',
            NEW.user_id;
    END IF;
    IF v_user_tenant <> NEW.tenant_id THEN
        RAISE EXCEPTION
            '[trigger 5] sys_user_building.tenant_id=% 与 user.dept.tenant_id=% 不一致',
            NEW.tenant_id, v_user_tenant;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sub_check_tenant
    BEFORE INSERT OR UPDATE ON sys_user_building
    FOR EACH ROW EXECUTE FUNCTION fn_sub_check_tenant();

-- -------------------------------------------------------------------
-- Mock seed: 求是小区用户-角色绑定（DEFERRED trigger 2 在 COMMIT 时触发；
-- 同事务内先 INSERT user_building 再 INSERT user_role 不会引发顺序问题，
-- 但稳妥起见这里使用 DEFERRABLE INITIALLY DEFERRED + 同 batch INSERT。）
-- -------------------------------------------------------------------
-- 先 INSERT building 再 INSERT role 以满足 trigger 2 的 COMMIT 时校验。
INSERT INTO sys_user_building (user_id, building_id, tenant_id, assigned_by, status) VALUES
    -- 陈网格员（800004）— 求是第一网格管 1/2/3 栋
    (800004, 30001, 10001, 800003, 1),
    (800004, 30002, 10001, 800003, 1),
    (800004, 30003, 10001, 800003, 1),
    -- 张三（800102）— 业主代表，仅管 3 栋
    (800102, 30001, 10001, 800101, 1),
    -- 孙志愿者（800104）— 业主自治志愿队，分配求是 5 栋
    (800104, 30005, 10001, 800101, 1);

INSERT INTO sys_user_role (user_id, role_id, effective_data_scope, granted_by) VALUES
    (800001, 1,  'ALL_COMMUNITY', NULL),     -- 王街道  → GOV_SUPER_ADMIN
    (800002, 3,  'ALL_COMMUNITY', 800001),   -- 李书记  → PARTY_SECRETARY
    (800003, 2,  'ALL_COMMUNITY', 800001),   -- 刘主任  → COMMUNITY_ADMIN
    (800004, 4,  'OWNER_GROUP',   800003),   -- 陈网格员 → GRID_OPERATOR
    (800101, 5,  'ALL_COMMUNITY', 800003),   -- 周主任  → COMMITTEE_DIRECTOR ★锁死
    (800103, 6,  'ALL_COMMUNITY', 800101),   -- 钱委员  → COMMITTEE_MEMBER
    (800102, 8,  'OWNER_GROUP',   800101),   -- 张三    → OWNER_REPRESENTATIVE ★锁死
    (800104, 9,  'OWNER_GROUP',   800101),   -- 孙志愿者 → VOLUNTEER
    (800201, 10, 'ORG_ONLY',      800003),   -- 赵经理  → PROPERTY_MANAGER ★锁死
    (800202, 11, 'ORG_ONLY',      800201);   -- 朱员工  → PROPERTY_STAFF ★锁死
