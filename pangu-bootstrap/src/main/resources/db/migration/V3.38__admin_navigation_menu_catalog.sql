-- 后端下发管理端导航菜单；前端只消费本表返回的菜单树，不再硬编码业务菜单。
-- 鉴权仍以 sys_permission / @PreAuthorize 为准，本表只控制导航可见性与排序。

ALTER TABLE sys_menu
    ADD COLUMN IF NOT EXISTS route_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS required_permission VARCHAR(64),
    ADD COLUMN IF NOT EXISTS required_any_permissions VARCHAR(512),
    ADD COLUMN IF NOT EXISTS required_role_keys VARCHAR(255);

COMMENT ON COLUMN sys_menu.route_id IS '前端页面或模块稳定 ID';
COMMENT ON COLUMN sys_menu.required_permission IS '显示该菜单所需的单个 permission_key';
COMMENT ON COLUMN sys_menu.required_any_permissions IS '显示该菜单所需任一 permission_key，逗号分隔';
COMMENT ON COLUMN sys_menu.required_role_keys IS '显示该菜单所需任一 role_key，逗号分隔';

INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES
    (1000, 0, 'dashboard', '工作台 / 概览', '/overview', 'LayoutDashboard', 10, 1, '0', NULL, NULL, NULL),
    (1010, 1000, 'overview', '角色工作台', '/overview', NULL, 10, 1, '0', NULL, NULL, NULL),

    (2000, 0, 'property', '物业管理', '/property', 'Building2', 20, 1, '0', NULL, NULL, NULL),
    (2010, 2000, 'property-mgmt', '公共收益 / 开支录入', '/property-mgmt', NULL, 10, 1, '0', 'fund:account:read', NULL, NULL),

    (3000, 0, 'committee', '委员会操作', '/committee', 'UsersRound', 30, 1, '0', NULL, NULL, NULL),
    (3010, 3000, 'committee-roster', '委员会名册', '/committee-roster', NULL, 10, 1, '0', NULL, 'voting:subject:audit,waiver:read', NULL),
    (3020, 3000, 'term-management', '换届管理', '/term-management', NULL, 20, 1, '0', NULL, 'voting:subject:audit,waiver:read', NULL),
    (3030, 3000, 'meeting-minutes', '会议纪要', '/meeting-minutes', NULL, 30, 1, '0', NULL, 'voting:subject:audit,waiver:read', NULL),
    (3040, 3000, 'duties', '职责分工', '/duties', NULL, 40, 1, '0', NULL, 'voting:subject:audit,waiver:read', NULL),

    (4000, 0, 'election', '选举管理', '/election', 'Vote', 40, 1, '0', NULL, NULL, NULL),
    (4010, 4000, 'election', '选举投票看板', '/election', NULL, 10, 1, '0', 'voting:subject:audit', NULL, NULL),

    (5000, 0, 'governance', '议题与表决', '/governance', 'Gavel', 50, 1, '0', NULL, NULL, NULL),
    (5010, 5000, 'subject-proposal', '议题筹备', '/subject-proposal', NULL, 10, 1, '0', NULL, 'voting:subject:create,voting:subject:create:election', NULL),
    (5020, 5000, 'voting', '议题表决看板', '/voting', NULL, 20, 1, '0', 'voting:subject:audit', NULL, NULL),
    (5030, 5000, 'disputes', '矛盾调解', '/disputes', NULL, 30, 1, '0', 'dispute:audit', NULL, NULL),

    (6000, 0, 'finance', '财务监督', '/finance', 'ShieldCheck', 60, 1, '0', NULL, NULL, NULL),
    (6010, 6000, 'finance', '公共收益公示', '/finance', NULL, 10, 1, '0', NULL, 'fund:account:read,disclosure:compose,disclosure:publish,disclosure:audit', NULL),
    (6020, 6000, 'dual-sign', '信托制双签核销台', '/dual-sign', NULL, 20, 1, '0', NULL, 'lock:unlock:committee,lock:unlock:street', NULL),
    (6030, 6000, 'expense-approval', '酬金制开支审批', '/expense-approval', NULL, 30, 1, '0', 'fund:account:read', NULL, NULL),
    (6040, 6000, 'fund-review', '大额资金前置审查', '/fund-review', NULL, 40, 1, '0', NULL, 'lock:unlock:street,disclosure:audit', NULL),

    (7000, 0, 'assets', '资产与维修', '/assets', 'Wrench', 70, 1, '0', NULL, NULL, NULL),
    (7010, 7000, 'assets', '资产台账', '/assets', NULL, 10, 1, '0', 'repair:workorder:read', NULL, NULL),
    (7020, 7000, 'work-orders', '维修工单', '/work-orders', NULL, 20, 1, '0', 'repair:workorder:read', NULL, NULL),
    (7030, 7000, 'engineering', '工程方案与验收', '/engineering', NULL, 30, 1, '0', NULL, 'repair:workorder:manage,repair:workorder:field,repair:workorder:governance', NULL),

    (8000, 0, 'comms', '沟通与报告', '/comms', 'Megaphone', 80, 1, '0', NULL, NULL, NULL),
    (8010, 8000, 'announcements', '通知公告', '/announcements', NULL, 10, 1, '0', NULL, 'voting:subject:audit,repair:workorder:read,owner:list', NULL),
    (8020, 8000, 'push-records', '定向推送记录', '/push-records', NULL, 20, 1, '0', 'voting:subject:audit', NULL, NULL),
    (8030, 8000, 'reports', '统计报表导出', '/reports', NULL, 30, 1, '0', NULL, 'voting:subject:audit,fund:account:read,owner:list', NULL),

    (9000, 0, 'users', '系统管理', '/users', 'Users', 900, 1, '0', NULL, NULL, NULL),
    (9010, 9000, 'owners', '业主名册', '/owners', NULL, 10, 1, '0', 'owner:list', NULL, NULL),
    (9020, 9000, 'topology', '楼栋 / 单元结构', '/topology', NULL, 20, 1, '0', 'owner:list', NULL, NULL),
    (9030, 9000, 'grid-management', '网格组织管理', '/grid-management', NULL, 30, 1, '0', 'admin:user:assign-role', NULL, NULL),
    (9040, 9000, 'rbac', '角色与数据范围', '/rbac', NULL, 40, 1, '0', 'admin:role:read', NULL, NULL),
    (9050, 9000, 'certification', '实名认证等级', '/certification', NULL, 50, 1, '0', 'owner:list', NULL, NULL)
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

SELECT setval('sys_menu_menu_id_seq', GREATEST((SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu), 9050), true);
