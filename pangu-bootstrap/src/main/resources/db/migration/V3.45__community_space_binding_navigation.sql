-- 冷启动房产绑定归入“小区空间管理”，避免被埋在系统管理里。

INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES (
    9100, 0, 'community-space', '小区空间管理', '/community-space', 'Building2', 25, 1, '0',
    NULL, NULL, NULL
)
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

UPDATE sys_menu
SET parent_id = 9100,
    order_num = 10
WHERE menu_id = 9070
  AND route_id = 'property-roster-import';

UPDATE sys_menu
SET parent_id = 9100,
    order_num = 20
WHERE menu_id = 9080
  AND route_id = 'property-binding-review';

SELECT setval('sys_menu_menu_id_seq',
              GREATEST((SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu), 9100),
              true);
