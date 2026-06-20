-- ===================================================================
-- V3.0 — 投票生命周期闭环（M3-2）
--
-- 本迁移在 V2.0 投票核心之上补齐两端缺失的能力：
--   1. 议题主表 status 取值放开到 1..6，新增 CANCELLED(6)；
--   2. t_voting_subject 增加 proposed_by_user_id / cancelled_at /
--      cancelled_by_user_id / cancel_reason 四个审计字段；
--   3. trigger 12 兜底 cancel_* 字段与 status=6 的一致性；
--   4. 新增 voting:subject:cancel / voting:subject:audit 两条业务能力点
--      （propose/publish 复用 V1.4 已有的 voting:subject:create/publish 并
--       追加授权给 PROPERTY_MANAGER 以承接"日常资金开支议案"场景）。
--
-- trigger 编号：V1..V2.x 已用 1-11；M3-2 起占用 12。
-- ===================================================================

-- ===== 1. 议题主表加 4 个审计字段 + 放开 CANCELLED 状态 =====
ALTER TABLE t_voting_subject
    ADD COLUMN proposed_by_user_id BIGINT,
    ADD COLUMN cancelled_at TIMESTAMP,
    ADD COLUMN cancelled_by_user_id BIGINT,
    ADD COLUMN cancel_reason VARCHAR(500);

COMMENT ON COLUMN t_voting_subject.proposed_by_user_id IS '议题发起人 sys_user.user_id（M3-2 写入；旧记录为 NULL）';
COMMENT ON COLUMN t_voting_subject.cancelled_at IS '撤回时间（status=CANCELLED 时必填）';
COMMENT ON COLUMN t_voting_subject.cancelled_by_user_id IS '撤回操作人 sys_user.user_id（status=CANCELLED 时必填）';
COMMENT ON COLUMN t_voting_subject.cancel_reason IS '撤回原因（status=CANCELLED 时必填，最大 500 字）';

-- 把 status check 从 1..5 放开到 1..6，新增 CANCELLED
ALTER TABLE t_voting_subject DROP CONSTRAINT chk_subject_status;
ALTER TABLE t_voting_subject
    ADD CONSTRAINT chk_subject_status CHECK (status IN (1, 2, 3, 4, 5, 6));

COMMENT ON COLUMN t_voting_subject.status IS
    '状态：1-草稿(DRAFT), 2-已公示(PUBLISHED), 3-投票中(VOTING), 4-已截止(CLOSED), 5-已结算(SETTLED), 6-已撤回(CANCELLED)';

-- ===== 2. trigger 12: CANCELLED 与 cancel_* 字段一致性兜底 =====
CREATE OR REPLACE FUNCTION fn_voting_cancel_consistency() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 6 AND (NEW.cancelled_at IS NULL
                            OR NEW.cancelled_by_user_id IS NULL
                            OR NEW.cancel_reason IS NULL) THEN
        RAISE EXCEPTION
            '[trigger 12] CANCELLED 议题必须同时落 cancelled_at + cancelled_by_user_id + cancel_reason，subject_id=%',
            NEW.subject_id;
    END IF;
    IF NEW.status <> 6 AND (NEW.cancelled_at IS NOT NULL
                             OR NEW.cancelled_by_user_id IS NOT NULL
                             OR NEW.cancel_reason IS NOT NULL) THEN
        RAISE EXCEPTION
            '[trigger 12] 非 CANCELLED 议题不应携带 cancel_* 审计字段，subject_id=% status=%',
            NEW.subject_id, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_voting_cancel_consistency
    BEFORE INSERT OR UPDATE ON t_voting_subject
    FOR EACH ROW EXECUTE FUNCTION fn_voting_cancel_consistency();

-- ===== 3. 新增 voting:subject:cancel / voting:subject:audit 能力点 =====
-- voting:subject:cancel：仅 G 端街道办强撤 PUBLISHED 议题；is_legal_redline=1
--   要求 fixed_data_scope NOT NULL（GOV_SUPER_ADMIN 已锁 ALL_COMMUNITY，trigger 6 通过）
-- voting:subject:audit：管理端议题查看；GB 端均可挂，无 redline
INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('voting:subject:cancel', '强撤已公示议题（仅街道办）',  'VOTING', 'G',  1),
    ('voting:subject:audit',  '管理端查看议题列表/详情',     'VOTING', 'GB', 0);

-- ===== 4. 角色 → 新权限授予 =====
-- voting:subject:cancel：GOV_SUPER_ADMIN 独占（街道办负责换届/突击花钱熔断）
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (1, 'voting:subject:cancel');

-- voting:subject:audit：GOV_SUPER_ADMIN / COMMUNITY_ADMIN / COMMITTEE_DIRECTOR
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (1, 'voting:subject:audit'),
    (2, 'voting:subject:audit'),
    (5, 'voting:subject:audit');

-- ===== 5. 把 voting:subject:create / publish 追加到 PROPERTY_MANAGER =====
-- 业务背景：信托制 / 筹金制下物业经理可发起"日常资金开支议案"（一般决议）。
-- voting:subject:create / publish 当前 allowed_dept_categories='GB'，需扩展为 'GBS' 以放行 S 端。
UPDATE sys_permission
   SET allowed_dept_categories = 'GBS'
 WHERE permission_key IN ('voting:subject:create', 'voting:subject:publish');

INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (10, 'voting:subject:create'),
    (10, 'voting:subject:publish');

-- 注：业主投票（voting:subject:cast）走 c_user 链路，没有 sys_role 关联；
-- endpoint 用 isAuthenticated() + service 层强校验（沿用 M2-3 disclosure 的降级方案）。

-- ===== 6. 索引：scheduler 扫描 PUBLISHED 待开票议题 =====
CREATE INDEX idx_voting_subject_status_start ON t_voting_subject(status, vote_start_at);
COMMENT ON INDEX idx_voting_subject_status_start IS 'VotingOpenScheduler 扫描 status=PUBLISHED AND vote_start_at <= now() 用';
