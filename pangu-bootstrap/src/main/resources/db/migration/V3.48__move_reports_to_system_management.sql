-- 统计报表导出归入系统管理，避免公告管理承载非公告职责。

UPDATE sys_menu
SET parent_id = 9000,
    order_num = 80
WHERE menu_id = 8030
  AND route_id = 'reports';

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 9000
FROM sys_role_menu
WHERE menu_id = 8030
ON CONFLICT (role_id, menu_id) DO NOTHING;
