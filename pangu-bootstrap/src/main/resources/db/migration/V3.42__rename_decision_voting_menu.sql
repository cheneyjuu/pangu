-- 投票管理下的普通/重大议题执行看板，避免与换届选举看板混用。

UPDATE sys_menu
SET menu_name = '议题投票看板'
WHERE menu_id = 5020
  AND route_id = 'voting';
