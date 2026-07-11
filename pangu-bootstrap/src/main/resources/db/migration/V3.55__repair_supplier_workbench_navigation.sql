-- 供应商账号只能进入独立供应商工作台，不能继承物业、业委会或系统管理菜单。
INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES
    (10000, 0, 'supplier-service', '供应商工作台', '/supplier-workbench', 'BriefcaseBusiness',
     10, 1, '0', NULL, NULL, 'SERVICE_PROVIDER_MANAGER,SERVICE_PROVIDER_STAFF'),
    (10010, 10000, 'supplier-workbench', '待报价与报价', '/supplier-workbench', NULL,
     10, 1, '0', 'repair:workorder:supplier', NULL, NULL)
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

DELETE FROM sys_role_menu role_menu
USING sys_role role
WHERE role_menu.role_id = role.role_id
  AND role.role_key IN ('SERVICE_PROVIDER_MANAGER', 'SERVICE_PROVIDER_STAFF');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.role_id, menu.menu_id
FROM sys_role role
CROSS JOIN (VALUES (10000), (10010)) menu(menu_id)
WHERE role.role_key IN ('SERVICE_PROVIDER_MANAGER', 'SERVICE_PROVIDER_STAFF');

SELECT setval('sys_menu_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM sys_menu), 10010), true);
