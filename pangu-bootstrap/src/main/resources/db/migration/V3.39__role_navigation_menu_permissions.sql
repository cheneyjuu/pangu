-- 角色拥有的菜单权限由后端维护并下发；sys_permission 继续作为接口与按钮权限来源。

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL REFERENCES sys_role(role_id),
    menu_id BIGINT NOT NULL REFERENCES sys_menu(menu_id),
    granted_by BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, menu_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_menu_role ON sys_role_menu(role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_menu ON sys_role_menu(menu_id);

COMMENT ON TABLE sys_role_menu IS '角色与管理端导航菜单权限关系；菜单展示由后端按当前角色下发';
COMMENT ON COLUMN sys_role_menu.menu_id IS 'sys_menu.menu_id';
COMMENT ON COLUMN sys_role_menu.role_id IS 'sys_role.role_id';

WITH child_grants AS (
    SELECT DISTINCT r.role_id, m.menu_id, m.parent_id
    FROM sys_role r
    JOIN sys_menu m ON m.parent_id <> 0
                   AND m.visible = 1
                   AND m.status = '0'
                   AND m.route_id IS NOT NULL
    WHERE r.status = '0'
      AND (
        (
          m.required_permission IS NULL
          AND m.required_any_permissions IS NULL
          AND m.required_role_keys IS NULL
        )
        OR (
          m.required_permission IS NOT NULL
          AND EXISTS (
              SELECT 1
              FROM sys_role_permission rp
              WHERE rp.role_id = r.role_id
                AND rp.permission_key = m.required_permission
          )
        )
        OR (
          m.required_any_permissions IS NOT NULL
          AND EXISTS (
              SELECT 1
              FROM sys_role_permission rp
              WHERE rp.role_id = r.role_id
                AND rp.permission_key = ANY(regexp_split_to_array(m.required_any_permissions, '\\s*,\\s*'))
          )
        )
        OR (
          m.required_role_keys IS NOT NULL
          AND r.role_key = ANY(regexp_split_to_array(m.required_role_keys, '\\s*,\\s*'))
        )
      )
),
all_grants AS (
    SELECT role_id, menu_id FROM child_grants
    UNION
    SELECT DISTINCT child_grants.role_id, parent.menu_id
    FROM child_grants
    JOIN sys_menu parent ON parent.menu_id = child_grants.parent_id
    WHERE parent.visible = 1
      AND parent.status = '0'
      AND parent.route_id IS NOT NULL
)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM all_grants
ON CONFLICT (role_id, menu_id) DO NOTHING;
