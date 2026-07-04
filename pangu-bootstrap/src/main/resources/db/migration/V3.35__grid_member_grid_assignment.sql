-- V3.35: 网格员可分配到一个或多个网格节点，AllowedBuildingIds 由所选网格聚合。

CREATE TABLE sys_user_grid_dept_scope (
    scope_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    grid_dept_id BIGINT NOT NULL REFERENCES sys_dept(dept_id),
    assigned_by BIGINT REFERENCES sys_user(user_id),
    status SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_grid_dept_scope_status CHECK (status IN (1, 2)),
    CONSTRAINT uk_user_grid_dept_scope UNIQUE (user_id, grid_dept_id)
);

CREATE INDEX idx_user_grid_dept_scope_user
    ON sys_user_grid_dept_scope(user_id) WHERE status = 1;
CREATE INDEX idx_user_grid_dept_scope_grid
    ON sys_user_grid_dept_scope(grid_dept_id) WHERE status = 1;

COMMENT ON TABLE sys_user_grid_dept_scope IS '网格员与网格组织节点的分配关系，GRID_MEMBER OWNER_GROUP 数据范围优先来源';
COMMENT ON COLUMN sys_user_grid_dept_scope.grid_dept_id IS 'G 端 dept_type=5 网格组织节点';
COMMENT ON COLUMN sys_user_grid_dept_scope.status IS '1=生效, 2=已撤销';

INSERT INTO sys_user_grid_dept_scope (user_id, grid_dept_id, assigned_by, status)
SELECT DISTINCT u.user_id, u.dept_id, NULL::BIGINT, 1
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
JOIN sys_dept d ON d.dept_id = u.dept_id
WHERE u.status = '0'
  AND r.role_key = 'GRID_MEMBER'
  AND d.dept_category = 'G'
  AND d.dept_type = 5
  AND EXISTS (
      SELECT 1
      FROM sys_dept_building_scope dbs
      WHERE dbs.dept_id = u.dept_id
        AND dbs.status = 1
  )
ON CONFLICT (user_id, grid_dept_id)
DO UPDATE SET
    status = 1,
    updated_at = now();

CREATE OR REPLACE FUNCTION fn_ugds_check_grid_assignment() RETURNS TRIGGER AS $$
DECLARE
    v_role_key VARCHAR(50);
    v_user_status CHAR(1);
    v_user_dept_cat CHAR(1);
    v_user_dept_type SMALLINT;
    v_grid_dept_cat CHAR(1);
    v_grid_dept_type SMALLINT;
    v_grid_status CHAR(1);
    v_operator_role VARCHAR(50);
    v_operator_dept_cat CHAR(1);
    v_operator_dept_type SMALLINT;
BEGIN
    IF NEW.status <> 1 THEN
        RETURN NEW;
    END IF;

    SELECT r.role_key, u.status, d.dept_category, d.dept_type
      INTO v_role_key, v_user_status, v_user_dept_cat, v_user_dept_type
      FROM sys_user u
      JOIN sys_user_role ur ON ur.user_id = u.user_id
      JOIN sys_role r ON r.role_id = ur.role_id
      JOIN sys_dept d ON d.dept_id = u.dept_id
     WHERE u.user_id = NEW.user_id;

    IF v_user_status <> '0'
            OR v_role_key <> 'GRID_MEMBER'
            OR v_user_dept_cat <> 'G'
            OR v_user_dept_type <> 5 THEN
        RAISE EXCEPTION
            '[trigger user grid scope] 只有有效 GRID_MEMBER 工作身份可分配网格，user_id=%',
            NEW.user_id;
    END IF;

    SELECT dept_category, dept_type, status
      INTO v_grid_dept_cat, v_grid_dept_type, v_grid_status
      FROM sys_dept
     WHERE dept_id = NEW.grid_dept_id;

    IF v_grid_status <> '0'
            OR v_grid_dept_cat <> 'G'
            OR v_grid_dept_type <> 5 THEN
        RAISE EXCEPTION
            '[trigger user grid scope] 只能分配有效 G 端 dept_type=5 网格节点，grid_dept_id=%',
            NEW.grid_dept_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM sys_dept_building_scope dbs
         WHERE dbs.dept_id = NEW.grid_dept_id
           AND dbs.status = 1
    ) THEN
        RAISE EXCEPTION
            '[trigger user grid scope] 网格节点必须先配置生效楼栋范围，grid_dept_id=%',
            NEW.grid_dept_id;
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
                '[trigger user grid scope] 网格员网格分配只能由居委会管理身份操作，assigned_by=%',
                NEW.assigned_by;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ugds_check_grid_assignment
    BEFORE INSERT OR UPDATE ON sys_user_grid_dept_scope
    FOR EACH ROW EXECUTE FUNCTION fn_ugds_check_grid_assignment();

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
              JOIN (
                  SELECT ugds.grid_dept_id AS dept_id
                    FROM sys_user_grid_dept_scope ugds
                   WHERE ugds.user_id = NEW.user_id
                     AND ugds.status = 1
                  UNION
                  SELECT u.dept_id
                    FROM sys_user u
                   WHERE u.user_id = NEW.user_id
                     AND NOT EXISTS (
                         SELECT 1
                           FROM sys_user_grid_dept_scope ugds
                          WHERE ugds.user_id = u.user_id
                            AND ugds.status = 1
                     )
              ) grid_dept ON grid_dept.dept_id = dbs.dept_id
             WHERE dbs.status = 1
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
