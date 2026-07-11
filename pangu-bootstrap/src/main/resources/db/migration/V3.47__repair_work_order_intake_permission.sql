-- V3.47: 维修工单登记/受理权限收口。
-- 后台补录和受理是物业客服/物业经理的接报职责，不再复用 manage/field。

INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline)
VALUES ('repair:workorder:intake', '登记/受理维修报修工单', 'REPAIR', 'S', 0)
ON CONFLICT (permission_key) DO UPDATE SET
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    allowed_dept_categories = EXCLUDED.allowed_dept_categories,
    is_legal_redline = EXCLUDED.is_legal_redline;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT r.role_id, 'repair:workorder:intake'
FROM sys_role r
WHERE r.role_key IN ('PROPERTY_MANAGER', 'PROPERTY_STAFF')
ON CONFLICT (role_id, permission_key) DO NOTHING;
