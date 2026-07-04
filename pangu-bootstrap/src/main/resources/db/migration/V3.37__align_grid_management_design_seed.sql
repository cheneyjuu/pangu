-- V3.37: 对齐网格组织管理设计稿的小区与楼栋演示数据。
-- 设计稿使用 3 个小区、每小区 6 栋楼；这里用 10001/10002/10003 作为真实 tenant_id。

INSERT INTO c_owner_property (uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status) VALUES
    (70002, 10001, 30001, 30001101, 86.00, 1, 1),
    (70002, 10001, 30002, 30002101, 92.00, 1, 1),
    (70002, 10001, 30003, 30003101, 78.00, 1, 1),
    (70002, 10001, 30004, 30004101, 120.00, 1, 1),
    (70002, 10001, 30005, 30005101, 64.00, 1, 1),
    (70002, 10001, 30006, 30006101, 88.00, 1, 1),
    (70002, 10002, 40001, 40001101, 86.00, 1, 1),
    (70002, 10002, 40002, 40002101, 92.00, 1, 1),
    (70002, 10002, 40003, 40003101, 78.00, 1, 1),
    (70002, 10002, 40004, 40004101, 120.00, 1, 1),
    (70002, 10002, 40005, 40005101, 64.00, 1, 1),
    (70002, 10002, 40006, 40006101, 88.00, 1, 1),
    (70002, 10003, 50001, 50001101, 86.00, 1, 1),
    (70002, 10003, 50002, 50002101, 92.00, 1, 1),
    (70002, 10003, 50003, 50003101, 78.00, 1, 1),
    (70002, 10003, 50004, 50004101, 120.00, 1, 1),
    (70002, 10003, 50005, 50005101, 64.00, 1, 1),
    (70002, 10003, 50006, 50006101, 88.00, 1, 1)
ON CONFLICT (tenant_id, room_id, uid) DO UPDATE SET
    building_id = EXCLUDED.building_id,
    build_area = EXCLUDED.build_area,
    is_voting_delegate = EXCLUDED.is_voting_delegate,
    account_status = EXCLUDED.account_status;

INSERT INTO sys_dept_tenant_scope (dept_id, tenant_id, assigned_by, status) VALUES
    (101, 10001, 800003, 1),
    (101, 10002, 800003, 1),
    (101, 10003, 800003, 1)
ON CONFLICT (dept_id, tenant_id) DO UPDATE SET
    assigned_by = EXCLUDED.assigned_by,
    status = 1,
    updated_at = now();

UPDATE sys_dept_building_scope
SET status = 2,
    updated_at = now()
WHERE dept_id IN (104, 111, 112)
  AND status = 1;

INSERT INTO sys_dept_building_scope (dept_id, tenant_id, building_id, assigned_by, status) VALUES
    (104, 10001, 30001, 800003, 1),
    (104, 10001, 30002, 800003, 1),
    (104, 10002, 40001, 800003, 1),
    (111, 10001, 30003, 800003, 1),
    (111, 10001, 30004, 800003, 1),
    (111, 10003, 50005, 800003, 1),
    (111, 10003, 50006, 800003, 1),
    (112, 10002, 40002, 800003, 1),
    (112, 10002, 40003, 800003, 1),
    (112, 10002, 40004, 800003, 1)
ON CONFLICT (dept_id, tenant_id, building_id) DO UPDATE SET
    assigned_by = EXCLUDED.assigned_by,
    status = 1,
    updated_at = now();
