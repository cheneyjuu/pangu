-- 关联业务：允许业委会委员按已完成的验收审批结果办理全小区公共维修用印。

-- 用印是法理红线权限；委员默认就是全小区治理范围，授予前将该范围锁定，禁止被降级。
UPDATE sys_role
SET fixed_data_scope = 'ALL_COMMUNITY'
WHERE role_key = 'COMMITTEE_MEMBER'
  AND fixed_data_scope IS NULL;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role.role_id, 'committee:seal:use'
FROM sys_role role
WHERE role.role_key = 'COMMITTEE_MEMBER'
ON CONFLICT (role_id, permission_key) DO NOTHING;
