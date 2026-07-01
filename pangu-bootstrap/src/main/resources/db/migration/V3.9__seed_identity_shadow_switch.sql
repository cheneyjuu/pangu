-- ===================================================================
-- V3.9 — D-mini 多分身切卡 seed
-- 给刘主任（account_id=999803）增加一个网格员工作分身，用于验证
-- t_account -> sys_user 1:N 与 active_user_id 切换。
-- ===================================================================

INSERT INTO sys_user (user_id, account_id, dept_id, user_name, nick_name, status)
VALUES (800006, 999803, 104, 'liu_grid', '刘主任(网格)', '0')
ON CONFLICT (account_id, dept_id) DO NOTHING;

INSERT INTO sys_user_building (user_id, building_id, tenant_id, assigned_by, status)
VALUES
    (800006, 30001, 10001, 800003, 1),
    (800006, 30002, 10001, 800003, 1)
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id, effective_data_scope, granted_by)
VALUES (800006, 4, 'OWNER_GROUP', 800003)
ON CONFLICT (user_id) DO NOTHING;
