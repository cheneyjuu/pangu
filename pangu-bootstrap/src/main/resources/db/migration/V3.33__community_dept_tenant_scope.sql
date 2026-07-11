-- V3.33: 居委会可管辖多个小区；网格楼栋范围必须落在上级居委会的小区管辖范围内。

CREATE TABLE sys_dept_tenant_scope (
    scope_id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL REFERENCES sys_dept(dept_id),
    tenant_id BIGINT NOT NULL,
    assigned_by BIGINT REFERENCES sys_user(user_id),
    status SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_dept_tenant_scope_status CHECK (status IN (1, 2)),
    CONSTRAINT uk_dept_tenant_scope UNIQUE (dept_id, tenant_id)
);

CREATE INDEX idx_dept_tenant_scope_dept
    ON sys_dept_tenant_scope(dept_id) WHERE status = 1;
CREATE INDEX idx_dept_tenant_scope_tenant
    ON sys_dept_tenant_scope(tenant_id) WHERE status = 1;

COMMENT ON TABLE sys_dept_tenant_scope IS '居委会管辖小区范围，网格节点只能在上级居委会管辖 tenant 集合内选择楼栋';
COMMENT ON COLUMN sys_dept_tenant_scope.dept_id IS 'G 端 dept_type=2 居委会组织节点';
COMMENT ON COLUMN sys_dept_tenant_scope.assigned_by IS '授权操作人；历史种子数据允许为空';
COMMENT ON COLUMN sys_dept_tenant_scope.status IS '1=生效, 2=已撤销';

INSERT INTO sys_dept_tenant_scope (dept_id, tenant_id, assigned_by, status)
SELECT dept_id, tenant_id, NULL, 1
FROM sys_dept
WHERE dept_category = 'G'
  AND dept_type = 2
  AND tenant_id IS NOT NULL
ON CONFLICT (dept_id, tenant_id)
DO UPDATE SET
    status = 1,
    updated_at = now();

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

        IF v_operator_role <> 'COMMUNITY_ADMIN'
                OR v_operator_dept_cat <> 'G'
                OR v_operator_dept_type <> 2 THEN
            RAISE EXCEPTION
                '[trigger community tenant scope] 居委会管辖小区只能由居委会管理身份分配，assigned_by=%',
                NEW.assigned_by;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dts_check_community_scope
    BEFORE INSERT OR UPDATE ON sys_dept_tenant_scope
    FOR EACH ROW EXECUTE FUNCTION fn_dts_check_community_scope();

CREATE OR REPLACE FUNCTION fn_dbs_check_grid_scope() RETURNS TRIGGER AS $$
DECLARE
    v_dept_cat CHAR(1);
    v_dept_type SMALLINT;
    v_parent_dept_id BIGINT;
    v_operator_role VARCHAR(50);
    v_operator_dept_cat CHAR(1);
    v_operator_dept_type SMALLINT;
BEGIN
    IF NEW.status <> 1 THEN
        RETURN NEW;
    END IF;

    SELECT dept_category, dept_type, parent_id
      INTO v_dept_cat, v_dept_type, v_parent_dept_id
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

    IF NOT EXISTS (
        SELECT 1
          FROM sys_dept_tenant_scope dts
         WHERE dts.dept_id = v_parent_dept_id
           AND dts.tenant_id = NEW.tenant_id
           AND dts.status = 1
    ) THEN
        RAISE EXCEPTION
            '[trigger grid scope] 楼栋不在上级居委会管辖小区范围内，dept_id=%, tenant_id=%, building_id=%',
            NEW.dept_id, NEW.tenant_id, NEW.building_id;
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
