-- 不修改已发布的 V3.33；用新迁移放宽网格楼栋范围撤销校验。
-- status=2 表示撤销历史范围，允许更新为非活跃态，不再要求楼栋仍在当前管辖范围内。

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
