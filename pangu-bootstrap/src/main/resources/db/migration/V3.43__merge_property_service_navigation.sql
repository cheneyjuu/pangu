-- 菜单语义收口：
-- 1) 维修/工程/资产归入“物业管理”。
-- 2) 公共收益与开支录入归入“财务监督”。
-- 3) 旧“资产与维修”一级菜单隐藏，避免与“物业管理”并列重复。

UPDATE sys_menu
SET parent_id = 2000,
    order_num = 10
WHERE menu_id = 7010
  AND route_id = 'assets';

UPDATE sys_menu
SET parent_id = 2000,
    order_num = 20
WHERE menu_id = 7020
  AND route_id = 'work-orders';

UPDATE sys_menu
SET parent_id = 2000,
    order_num = 30
WHERE menu_id = 7030
  AND route_id = 'engineering';

UPDATE sys_menu
SET parent_id = 6000,
    menu_name = '收益与开支录入',
    order_num = 20
WHERE menu_id = 2010
  AND route_id = 'property-mgmt';

UPDATE sys_menu
SET order_num = 30
WHERE menu_id = 6020
  AND route_id = 'dual-sign';

UPDATE sys_menu
SET order_num = 40
WHERE menu_id = 6030
  AND route_id = 'expense-approval';

UPDATE sys_menu
SET order_num = 50
WHERE menu_id = 6040
  AND route_id = 'fund-review';

UPDATE sys_menu
SET visible = 0,
    status = '1'
WHERE menu_id = 7000
  AND route_id = 'assets';

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 2000
FROM sys_role_menu
WHERE menu_id IN (7010, 7020, 7030)
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, 6000
FROM sys_role_menu
WHERE menu_id = 2010
ON CONFLICT (role_id, menu_id) DO NOTHING;

DELETE FROM sys_role_menu
WHERE menu_id = 7000;
