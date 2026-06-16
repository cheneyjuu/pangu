-- ===================================================================
-- 8. 系统菜单与权限标识表 (sys_menu)
-- ===================================================================
CREATE TABLE sys_menu (
    menu_id BIGSERIAL PRIMARY KEY,
    menu_name VARCHAR(50) NOT NULL,
    perms VARCHAR(100) DEFAULT NULL,
    status CHAR(1) DEFAULT '0'
);

COMMENT ON TABLE sys_menu IS '系统权限与菜单资源表';
COMMENT ON COLUMN sys_menu.menu_id IS '菜单/权限ID';
COMMENT ON COLUMN sys_menu.menu_name IS '权限或菜单名称';
COMMENT ON COLUMN sys_menu.perms IS '权限字符标识 (如 election:vote)';
COMMENT ON COLUMN sys_menu.status IS '资源状态：0-正常, 1-停用';

-- ===================================================================
-- 9. 角色与权限菜单关联表 (sys_role_menu)
-- ===================================================================
CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL REFERENCES sys_role(role_id) ON DELETE CASCADE,
    menu_id BIGINT NOT NULL REFERENCES sys_menu(menu_id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, menu_id)
);

COMMENT ON TABLE sys_role_menu IS '角色与权限菜单多对多关联映射表';
COMMENT ON COLUMN sys_role_menu.role_id IS '角色ID';
COMMENT ON COLUMN sys_role_menu.menu_id IS '菜单资源ID';

-- ===================================================================
-- 导入基础权限配置与关联关系
-- ===================================================================
INSERT INTO sys_menu (menu_id, menu_name, perms, status)
VALUES
(1, '业主表决投票', 'election:vote', '0'),
(2, '居民工单查看', 'repair:view', '0'),
(3, '平台管理员超级权限', '*:*', '0');

-- 重置主键序列，防止自增冲突
ALTER SEQUENCE sys_menu_menu_id_seq RESTART WITH 4;

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES
(1, 1), -- 超级管理员拥有投票权
(1, 3), -- 超级管理员拥有管理员权限
(2, 1), -- 求是小区网格员拥有投票权
(2, 2); -- 求是小区网格员拥有工单查看权
