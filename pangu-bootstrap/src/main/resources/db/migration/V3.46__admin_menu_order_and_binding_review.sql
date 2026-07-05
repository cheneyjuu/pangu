-- 管理端一级菜单排序与房产绑定审核归位。

UPDATE sys_menu
SET menu_name = '工作台',
    order_num = 10
WHERE menu_id = 1000
  AND route_id = 'dashboard';

UPDATE sys_menu
SET menu_name = '物业管理',
    order_num = 20
WHERE menu_id = 2000
  AND route_id = 'property';

UPDATE sys_menu
SET menu_name = '投票管理',
    order_num = 30
WHERE menu_id = 5000
  AND route_id = 'governance';

UPDATE sys_menu
SET menu_name = '公告管理',
    order_num = 40
WHERE menu_id = 8000
  AND route_id = 'comms';

UPDATE sys_menu
SET menu_name = '选举管理',
    order_num = 50
WHERE menu_id = 4000
  AND route_id = 'election';

UPDATE sys_menu
SET menu_name = '委员会操作',
    order_num = 60
WHERE menu_id = 3000
  AND route_id = 'committee';

UPDATE sys_menu
SET menu_name = '财务监督',
    order_num = 70
WHERE menu_id = 6000
  AND route_id = 'finance';

UPDATE sys_menu
SET menu_name = '系统管理',
    order_num = 80
WHERE menu_id = 9000
  AND route_id = 'users';

UPDATE sys_menu
SET parent_id = 9000,
    order_num = 60
WHERE menu_id = 9070
  AND route_id = 'property-roster-import';

UPDATE sys_menu
SET parent_id = 9000,
    order_num = 70
WHERE menu_id = 9080
  AND route_id = 'property-binding-review';

UPDATE sys_menu
SET visible = 0,
    order_num = 999
WHERE menu_id = 9100
  AND route_id = 'community-space';

DELETE FROM sys_role_menu
WHERE menu_id = 9100;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 9000
FROM sys_role_menu
WHERE menu_id IN (9070, 9080)
ON CONFLICT (role_id, menu_id) DO NOTHING;
