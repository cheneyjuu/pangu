-- ===================================================================
-- V1.3 — sys_menu / sys_role_menu （前端导航占位，权限拉取走 V1.4 sys_permission）
-- 详见：M1权限体系重构设计.md §5.5 + §10.1 — Permission 模型完全替代旧的
-- "perms 字符串放在 menu 表里" 模式；本文件保留 sys_menu 仅作前端导航树占位，
-- 不再用于鉴权决策；@PreAuthorize 一律走 hasAuthority(permission_key)。
-- ===================================================================

CREATE TABLE sys_menu (
    menu_id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_name VARCHAR(50) NOT NULL,
    path VARCHAR(200),
    component VARCHAR(255),
    icon VARCHAR(100),
    order_num INT NOT NULL DEFAULT 0,
    visible SMALLINT NOT NULL DEFAULT 1,                   -- 1=显示, 0=隐藏
    status CHAR(1) NOT NULL DEFAULT '0',                   -- 0=正常, 1=停用
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sys_menu_parent ON sys_menu(parent_id);

COMMENT ON TABLE sys_menu IS '前端导航菜单树（不参与鉴权；鉴权走 sys_permission/sys_role_permission）';
COMMENT ON COLUMN sys_menu.menu_id IS '菜单 ID';
COMMENT ON COLUMN sys_menu.parent_id IS '父菜单 ID，0=根';
COMMENT ON COLUMN sys_menu.path IS '路由 path';
COMMENT ON COLUMN sys_menu.visible IS '1=显示, 0=隐藏';

-- 简单种子（仅用于前端导航，实际权限由 V1.4 sys_permission 决定）
INSERT INTO sys_menu (menu_id, parent_id, menu_name, path, order_num) VALUES
    (1, 0, '工作台',     '/dashboard', 10),
    (2, 0, '议题中心',   '/voting',    20),
    (3, 0, '资金账户',   '/fund',      30),
    (4, 0, '审批中心',   '/waiver',    40),
    (5, 0, '系统管理',   '/admin',     90);
SELECT setval('sys_menu_menu_id_seq', 100, false);
