-- ===================================================================
-- V3.3 — 业主名册查询权限种子（M4 读侧）
--
-- 背景：M4-2 业主名册分页 + 详情接口（GET /api/v1/owners, /owners/{uid}）。
--   - 行级数据范围由 @DataScope(buildingAlias="op") 注入；
--   - endpoint 权限关键字：owner:list / owner:detail；
--   - 仅纯读，不涉及写动作，不改 schema。
--
-- 授权范围（保守，与现有读侧 candidate:nominate 等一致的边界）：
--   1 GOV_SUPER_ADMIN     - 街道办超管，跨小区聚合
--   2 COMMUNITY_ADMIN     - 居委会主任
--   3 PARTY_SECRETARY     - 党组书记
--   4 GRID_OPERATOR       - 网格员（按 DataScope 限本人楼栋）
--   5 COMMITTEE_DIRECTOR  - 业委会主任
--   6 COMMITTEE_MEMBER    - 业委会筹备组 / 委员
-- 不授 8 OWNER_DELEGATE（业主代表）—— 避免业主间名册 PII 跨户泄露。
-- ===================================================================

INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('owner:list',   '业主名册分页查询',    'OWNER', 'GB', 0),
    ('owner:detail', '业主名册详情查询',    'OWNER', 'GB', 0);

INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (1, 'owner:list'), (1, 'owner:detail'),
    (2, 'owner:list'), (2, 'owner:detail'),
    (3, 'owner:list'), (3, 'owner:detail'),
    (4, 'owner:list'), (4, 'owner:detail'),
    (5, 'owner:list'), (5, 'owner:detail'),
    (6, 'owner:list'), (6, 'owner:detail');
