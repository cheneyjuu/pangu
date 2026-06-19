-- ===================================================================
-- V2.9 — SaaS 管理员动态角色管理 permission 种子
-- 详见：M2路线图.md §5 M2-4
--
-- 背景：M1 V1.4 的 17 个 permission 中已有 admin:role:read（查角色）
-- 与 admin:role:write（编辑角色 / 角色-权限关系，redline=1），但
-- M2-1 GovernanceLockController.lock() 和本期 RoleAdminController
-- 都用 admin:role:manage 这条 placeholder permission（"动态新建 / 删除
-- 角色 + 角色-权限分配"），作为 SaaS 管理员触发回归测试 / SaaS 后台
-- 用动态角色管理的统一入口。本期补齐 schema 种子，并挂到 GOV_SUPER_ADMIN
-- (role_id=1) 作为唯一持有者；business 端不可挂（allowed_dept_categories='G'）。
--
-- 红线选择：is_legal_redline=0。原因：admin:role:write 已经是 redline=1
-- 锁定到 fixed_data_scope NOT NULL 的角色，足以兜底"红线 admin 不能挂
-- 跨端"，admin:role:manage 仅作为 SaaS 平台管理员的回归 / 调试入口，
-- 不再重复施加 trigger 6 redline 校验。
-- ===================================================================

INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('admin:role:manage', 'SaaS 管理员动态新建 / 删除角色 + 角色-权限分配', 'ADMIN', 'G', 0);

-- 仅授予 GOV_SUPER_ADMIN（role_id=1, fixed_data_scope=ALL_COMMUNITY），
-- 满足 trigger 6（role.allowed_dept_category='G' 出现在 permission.allowed_dept_categories='G'）。
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (1, 'admin:role:manage');
