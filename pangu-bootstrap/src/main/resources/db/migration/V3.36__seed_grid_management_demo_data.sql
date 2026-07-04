-- V3.36: 网格组织管理演示数据。
-- 覆盖真实交互所需的网格列表、跨小区楼栋范围、负责网格员，不引入前端 mock。

INSERT INTO c_owner_property (uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status) VALUES
    (70002, 10001, 30003, 30003101, 88.00, 1, 1),
    (70002, 10001, 30004, 30004101, 92.00, 1, 1),
    (70002, 10002, 40001, 40001101, 81.00, 1, 1),
    (70002, 10002, 40002, 40002101, 86.00, 1, 1),
    (70002, 10002, 40003, 40003101, 91.00, 1, 1)
ON CONFLICT (tenant_id, room_id, uid) DO UPDATE SET
    building_id = EXCLUDED.building_id,
    build_area = EXCLUDED.build_area,
    is_voting_delegate = EXCLUDED.is_voting_delegate,
    account_status = EXCLUDED.account_status;

INSERT INTO sys_dept_tenant_scope (dept_id, tenant_id, assigned_by, status)
VALUES
    (101, 10001, 800003, 1),
    (101, 10002, 800003, 1)
ON CONFLICT (dept_id, tenant_id) DO UPDATE SET
    assigned_by = EXCLUDED.assigned_by,
    status = 1,
    updated_at = now();

INSERT INTO sys_dept (dept_id, parent_id, ancestors, dept_name, dept_type, dept_category, tenant_id, order_num, status) VALUES
    (111, 101, '1,105,101', '求是第二网格', 5, 'G', 10001, 32, '0'),
    (112, 101, '1,105,101', '跨小区联动网格', 5, 'G', 10001, 33, '0')
ON CONFLICT (dept_id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    ancestors = EXCLUDED.ancestors,
    dept_name = EXCLUDED.dept_name,
    dept_type = EXCLUDED.dept_type,
    dept_category = EXCLUDED.dept_category,
    tenant_id = EXCLUDED.tenant_id,
    order_num = EXCLUDED.order_num,
    status = '0',
    update_time = CURRENT_TIMESTAMP;

INSERT INTO sys_dept_building_scope (dept_id, tenant_id, building_id, assigned_by, status) VALUES
    (104, 10001, 30001, 800003, 1),
    (104, 10001, 30002, 800003, 1),
    (111, 10001, 30003, 800003, 1),
    (111, 10001, 30005, 800003, 1),
    (112, 10001, 30004, 800003, 1),
    (112, 10002, 40001, 800003, 1),
    (112, 10002, 40002, 800003, 1)
ON CONFLICT (dept_id, tenant_id, building_id) DO UPDATE SET
    assigned_by = EXCLUDED.assigned_by,
    status = 1,
    updated_at = now();

INSERT INTO t_account (account_id, phone, real_name, id_card_encrypted, real_name_verified, status) VALUES
    (999806, '13800000006', 'MOCK_何网格员', 'MOCK_ID_999806', 1, 1),
    (999807, '13800000007', 'MOCK_范网格员', 'MOCK_ID_999807', 1, 1)
ON CONFLICT (account_id) DO UPDATE SET
    phone = EXCLUDED.phone,
    real_name = EXCLUDED.real_name,
    id_card_encrypted = EXCLUDED.id_card_encrypted,
    real_name_verified = EXCLUDED.real_name_verified,
    status = 1;

INSERT INTO sys_user (user_id, account_id, dept_id, user_name, nick_name, status) VALUES
    (800007, 999806, 111, 'he_grid', '何网格员', '0'),
    (800008, 999807, 112, 'fan_grid', '范网格员', '0')
ON CONFLICT (user_id) DO UPDATE SET
    account_id = EXCLUDED.account_id,
    dept_id = EXCLUDED.dept_id,
    user_name = EXCLUDED.user_name,
    nick_name = EXCLUDED.nick_name,
    status = '0',
    update_time = CURRENT_TIMESTAMP;

INSERT INTO sys_user_role (user_id, role_id, effective_data_scope, granted_by) VALUES
    (800007, 4, 'OWNER_GROUP', 800003),
    (800008, 4, 'OWNER_GROUP', 800003)
ON CONFLICT (user_id) DO UPDATE SET
    role_id = EXCLUDED.role_id,
    effective_data_scope = EXCLUDED.effective_data_scope,
    granted_by = EXCLUDED.granted_by,
    granted_at = CURRENT_TIMESTAMP;

INSERT INTO sys_user_grid_dept_scope (user_id, grid_dept_id, assigned_by, status) VALUES
    (800004, 104, 800003, 1),
    (800006, 104, 800003, 1),
    (800007, 111, 800003, 1),
    (800008, 112, 800003, 1),
    (800008, 111, 800003, 1)
ON CONFLICT (user_id, grid_dept_id) DO UPDATE SET
    assigned_by = EXCLUDED.assigned_by,
    status = 1,
    updated_at = now();

UPDATE t_account
SET last_active_identity_id = 800007,
    last_active_identity_type = 'SYS_USER',
    update_time = CURRENT_TIMESTAMP
WHERE account_id = 999806;

UPDATE t_account
SET last_active_identity_id = 800008,
    last_active_identity_type = 'SYS_USER',
    update_time = CURRENT_TIMESTAMP
WHERE account_id = 999807;
