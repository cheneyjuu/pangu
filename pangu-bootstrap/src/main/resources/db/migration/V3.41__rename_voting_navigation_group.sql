-- 管理端导航文案收口：议题立项、普通议题投票、选举投票都归入投票管理。

UPDATE sys_menu
SET menu_name = '投票管理'
WHERE menu_id = 5000
  AND route_id = 'governance';
