-- 关联业务：将维修供应商登记、企业核验和账号激活从单个工单中独立为物业经理维护的供应商库。

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES (
    'repair:supplier:manage', '登记维修供应商并发送供应商账号激活邀请', 'REPAIR', 'S', 0
) ON CONFLICT (permission_key) DO UPDATE SET
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    allowed_dept_categories = EXCLUDED.allowed_dept_categories,
    is_legal_redline = EXCLUDED.is_legal_redline;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role.role_id, 'repair:supplier:manage'
FROM sys_role role
WHERE role.role_key = 'PROPERTY_MANAGER'
ON CONFLICT (role_id, permission_key) DO NOTHING;

UPDATE sys_menu
SET order_num = 40
WHERE menu_id = 7030
  AND route_id = 'engineering';

INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES (
    7040, 2000, 'repair-suppliers', '维修供应商库', '/repair-suppliers', NULL,
    30, 1, '0', 'repair:supplier:manage', NULL, NULL
) ON CONFLICT (menu_id) DO UPDATE SET
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

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, menu.menu_id
FROM sys_role role
CROSS JOIN (VALUES (2000::BIGINT), (7040::BIGINT)) menu(menu_id)
WHERE role.role_key = 'PROPERTY_MANAGER'
ON CONFLICT (role_id, menu_id) DO NOTHING;

SELECT setval(
    'sys_menu_menu_id_seq',
    GREATEST((SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu), 7040),
    true
);
