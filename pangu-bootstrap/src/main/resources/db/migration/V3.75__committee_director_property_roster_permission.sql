-- 关联业务：允许经审核开通的业委会主任在本小区管理端录入房屋及产权基础名册。
-- 普通业主不授予此权限；名册导入仍不等同于法定计票基数发布。

-- 权限目录需与新增的 B 端角色授权一致，避免角色配置页面错误地拒绝该授权。
UPDATE sys_permission
SET allowed_dept_categories = 'GBS'
WHERE permission_key = 'property:roster:import'
  AND allowed_dept_categories = 'GS';

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role.role_id, 'property:roster:import'
FROM sys_role role
WHERE role.role_key = 'COMMITTEE_DIRECTOR'
  AND role.status = '0'
ON CONFLICT (role_id, permission_key) DO NOTHING;
