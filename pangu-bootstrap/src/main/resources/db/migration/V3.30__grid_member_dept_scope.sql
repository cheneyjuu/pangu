-- V3.30: 网格员角色静态化为 GRID_MEMBER，并把网格数据范围从自然人分身
-- 解耦到 dept_type=5 的网格组织节点。

UPDATE sys_role
SET role_key = 'GRID_MEMBER',
    role_name = '网格员'
WHERE role_id = 4
  AND role_key = 'GRID_OPERATOR';

UPDATE t_voting_mobilization_permission
SET role_key = 'GRID_MEMBER'
WHERE role_key = 'GRID_OPERATOR';

CREATE TABLE sys_dept_building_scope (
    scope_id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL REFERENCES sys_dept(dept_id),
    tenant_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL REFERENCES sys_user(user_id),
    status SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dept_building_scope_status CHECK (status IN (1, 2)),
    CONSTRAINT uk_dept_building_scope UNIQUE (dept_id, tenant_id, building_id)
);

CREATE INDEX idx_dept_building_scope_dept
    ON sys_dept_building_scope(dept_id) WHERE status = 1;
CREATE INDEX idx_dept_building_scope_building
    ON sys_dept_building_scope(building_id) WHERE status = 1;
CREATE INDEX idx_dept_building_scope_tenant
    ON sys_dept_building_scope(tenant_id) WHERE status = 1;

COMMENT ON TABLE sys_dept_building_scope IS '网格组织节点楼栋范围，GRID_MEMBER OWNER_GROUP 数据范围来源';
COMMENT ON COLUMN sys_dept_building_scope.dept_id IS 'dept_type=5 的网格组织节点';
COMMENT ON COLUMN sys_dept_building_scope.status IS '1=生效, 2=已撤销';

INSERT INTO sys_dept_building_scope (dept_id, tenant_id, building_id, assigned_by, status)
SELECT DISTINCT u.dept_id, ub.tenant_id, ub.building_id, ub.assigned_by, 1
FROM sys_user_building ub
JOIN sys_user u ON u.user_id = ub.user_id
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
WHERE ub.status = 1
  AND r.role_key = 'GRID_MEMBER'
ON CONFLICT (dept_id, tenant_id, building_id)
DO UPDATE SET
    assigned_by = EXCLUDED.assigned_by,
    status = 1,
    updated_at = now();

CREATE OR REPLACE FUNCTION fn_dbs_check_grid_scope() RETURNS TRIGGER AS $$
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

    IF v_dept_cat <> 'G' OR v_dept_type <> 5 THEN
        RAISE EXCEPTION
            '[trigger grid scope] 只有 G 端 dept_type=5 网格节点允许配置楼栋范围，dept_id=%',
            NEW.dept_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM c_owner_property
         WHERE tenant_id = NEW.tenant_id
           AND building_id = NEW.building_id
    ) THEN
        RAISE EXCEPTION
            '[trigger grid scope] 楼栋不在指定租户下，tenant_id=%, building_id=%',
            NEW.tenant_id, NEW.building_id;
    END IF;

    SELECT r.role_key, d.dept_category, d.dept_type
      INTO v_operator_role, v_operator_dept_cat, v_operator_dept_type
      FROM sys_user u
      JOIN sys_user_role ur ON ur.user_id = u.user_id
      JOIN sys_role r ON r.role_id = ur.role_id
      JOIN sys_dept d ON d.dept_id = u.dept_id
     WHERE u.user_id = NEW.assigned_by;

    IF v_operator_role <> 'COMMUNITY_ADMIN'
            OR v_operator_dept_cat <> 'G'
            OR v_operator_dept_type <> 2 THEN
        RAISE EXCEPTION
            '[trigger grid scope] 网格楼栋范围只能由居委会管理身份分配，assigned_by=%',
            NEW.assigned_by;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dbs_check_grid_scope
    BEFORE INSERT OR UPDATE ON sys_dept_building_scope
    FOR EACH ROW EXECUTE FUNCTION fn_dbs_check_grid_scope();

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
    IF v_role_key = 'GRID_MEMBER' AND v_dept_type <> 5 THEN
        RAISE EXCEPTION
            '[trigger 1] GRID_MEMBER 必须挂在 dept_type=5（网格），实际=%', v_dept_type;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_sur_require_building() RETURNS TRIGGER AS $$
DECLARE
    v_role_key VARCHAR(50);
    v_active_count BIGINT;
BEGIN
    SELECT role_key INTO v_role_key FROM sys_role WHERE role_id = NEW.role_id;
    IF v_role_key = 'GRID_MEMBER' THEN
        SELECT COUNT(*) INTO v_active_count
        FROM (
            SELECT ub.building_id
              FROM sys_user_building ub
             WHERE ub.user_id = NEW.user_id
               AND ub.status = 1
            UNION
            SELECT dbs.building_id
              FROM sys_dept_building_scope dbs
              JOIN sys_user u ON u.dept_id = dbs.dept_id
             WHERE u.user_id = NEW.user_id
               AND dbs.status = 1
        ) active_buildings;
    ELSIF v_role_key IN ('OWNER_REPRESENTATIVE','VOLUNTEER') THEN
        SELECT COUNT(*) INTO v_active_count
          FROM sys_user_building
         WHERE user_id = NEW.user_id AND status = 1;
    ELSE
        RETURN NEW;
    END IF;

    IF v_active_count = 0 THEN
        RAISE EXCEPTION
            '[trigger 2] role % 必须至少绑定 1 个生效楼栋，user_id=%',
            v_role_key, NEW.user_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (2, 'admin:user:assign-role'),
    (2, 'admin:user:building:assign')
ON CONFLICT (role_id, permission_key) DO NOTHING;
