-- ============================================================================
-- V3.6: ELECTION 议题双签状态机（梯度 B）
-- ============================================================================
--
-- 业务目标：
--   1) ELECTION 议题新增 PENDING_COMMITTEE / PENDING_STREET 两个审批中状态；
--   2) 增加 review_history JSONB 审计字段，为后续 controller/service 落审批轨迹预留；
--   3) 新增居委会初审 / 街道办终审权限点。
--
-- 状态取值：
--   1-DRAFT, 2-PUBLISHED, 3-VOTING, 4-CLOSED, 5-SETTLED, 6-CANCELLED,
--   7-PENDING_COMMITTEE, 8-PENDING_STREET
-- ============================================================================

ALTER TABLE t_voting_subject DROP CONSTRAINT IF EXISTS chk_subject_status;
ALTER TABLE t_voting_subject
    ADD CONSTRAINT chk_subject_status CHECK (status IN (1, 2, 3, 4, 5, 6, 7, 8));

COMMENT ON COLUMN t_voting_subject.status IS
    '状态：1-草稿(DRAFT), 2-已公示(PUBLISHED), 3-投票中(VOTING), 4-已截止(CLOSED), 5-已结算(SETTLED), 6-已撤回(CANCELLED), 7-居委会初审中(PENDING_COMMITTEE), 8-街道办终审中(PENDING_STREET)';

ALTER TABLE t_voting_subject
    ADD COLUMN IF NOT EXISTS review_history JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN t_voting_subject.review_history IS
    '议题双签审批轨迹 JSONB 数组，元素含 reviewer/decision/reason/at 等元数据';

INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('voting:subject:review:committee', '居委会初审选举议题',       'VOTING', 'G', 0),
    ('voting:subject:review:street',    '街道办终审并公示选举议题', 'VOTING', 'G', 0)
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (2, 'voting:subject:review:committee'),
    (1, 'voting:subject:review:committee'),
    (1, 'voting:subject:review:street')
ON CONFLICT (role_id, permission_key) DO NOTHING;
