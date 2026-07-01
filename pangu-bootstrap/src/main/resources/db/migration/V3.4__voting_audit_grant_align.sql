-- ============================================================================
-- V3.4: 议题读权限缺口补齐（voting:subject:audit）
-- ============================================================================
--
-- 背景：
--   V3.0 引入 voting:subject:audit（管理端读议题列表/详情/进度/投票明细/候选人）时，
--   只授给了 GOV_SUPER_ADMIN(1) / COMMUNITY_ADMIN(2) / COMMITTEE_DIRECTOR(5)。
--   但同期/后续引入的写能力（voting:subject:create/publish/settle、candidate:*）
--   还给了 PARTY_SECRETARY(3) / GRID_OPERATOR(4) / COMMITTEE_MEMBER(6) /
--   COMMITTEE_SECRETARY(7) / OWNER_REPRESENTATIVE(8)，这些角色因此能写却不能读，
--   登录管理端打开「选举投票看板 / 议题表决看板 / 议题筹备」立刻吃 403。
--
--   典型表现：党组书记进「选举投票看板」立刻提示无权限——他需要 audit 读到候选人
--   列表才能做 candidate:review:party 党组审查。
--
-- 修复：
--   把 voting:subject:audit 补齐给 role 3/4/6/7/8。审查类只读权限不属于"红线写"
--   范畴，原 V3.0 备注里 audit 即标记为 GB 端均可挂、redline=0，本次补齐符合原设计意图。
--
-- 校验：
--   1) sys_role_permission 共应有 8 行 voting:subject:audit（含 1/2/5 原有 + 新增 3/4/6/7/8）；
--   2) 党组书记登录管理端「选举投票看板」不再 403，可看到 ELECTION 议题列表与候选人；
--   3) 业委会委员 / 秘书 / 楼栋代表 同样可正常进入「议题筹备」与「议题表决看板」。
-- ============================================================================

INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (3, 'voting:subject:audit'),
    (4, 'voting:subject:audit'),
    (6, 'voting:subject:audit'),
    (7, 'voting:subject:audit'),
    (8, 'voting:subject:audit')
ON CONFLICT (role_id, permission_key) DO NOTHING;
