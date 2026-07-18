-- 关联业务：分离维修表决结果核验与逐户选择审计，避免用宽泛治理权限暴露个人选择。

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES
    ('repair:decision:verify', '核验维修征询或业主大会维修事项结果', 'REPAIR', 'BS', 0),
    ('repair:decision:audit', '审计楼栋维修逐房屋在线表决选择', 'REPAIR', 'GB', 0)
ON CONFLICT (permission_key) DO UPDATE SET
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    allowed_dept_categories = EXCLUDED.allowed_dept_categories,
    is_legal_redline = EXCLUDED.is_legal_redline;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role.role_id, grant_row.permission_key
FROM sys_role role
JOIN (
    VALUES
        ('PROPERTY_MANAGER', 'repair:decision:verify'),
        ('PROPERTY_STAFF', 'repair:decision:verify'),
        ('COMMITTEE_DIRECTOR', 'repair:decision:verify'),
        ('COMMITTEE_MEMBER', 'repair:decision:verify'),
        ('GOV_SUPER_ADMIN', 'repair:decision:audit'),
        ('COMMUNITY_ADMIN', 'repair:decision:audit'),
        ('COMMITTEE_DIRECTOR', 'repair:decision:audit')
) AS grant_row(role_key, permission_key) ON grant_row.role_key = role.role_key
ON CONFLICT (role_id, permission_key) DO NOTHING;
