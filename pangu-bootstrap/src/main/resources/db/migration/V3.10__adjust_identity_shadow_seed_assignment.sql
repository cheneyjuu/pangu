-- ===================================================================
-- V3.10 — 调整 D-mini 多分身 seed 的楼栋占用
-- V3.9 为满足 GRID_OPERATOR 触发器给 800006 预绑了既有楼栋，会干扰
-- 楼栋责任田“同角色同楼栋互斥”的基线测试与演示数据。
-- 这里撤销既有测试楼栋占用，改为仅保留一条隔离楼栋绑定。
-- ===================================================================

UPDATE sys_user_building
SET status = 2,
    revoke_reason = 'D-mini seed moved to isolated building'
WHERE user_id = 800006
  AND building_id IN (30001, 30002)
  AND status = 1;

INSERT INTO sys_user_building (user_id, building_id, tenant_id, assigned_by, status)
VALUES (800006, 39999, 10001, 800003, 1);
