-- ===================================================================
-- V3.1 — ELECTION 选举全流程闭环（M3-3）
--
-- M2-2 已落地 ELECTION 引擎 / 领域模型 / t_election_candidate 表 /
-- t_voting_subject.max_winners / t_vote_item.target_id；M3-2 接通了
-- GENERAL/MAJOR 的立项-公示-投票-结算两端管道。本迁移补齐 ELECTION
-- 业务流落库所需的最小 schema：
--   1. 候选人审查/投票计数热点查询索引；
--   2. trigger 13 兜底 ELECTION 议题必须携带 max_winners >= 1。
--
-- 权限无需新增：candidate:nominate（V1.4，GB，授 role 3/4/5/6/8）/
--   candidate:approve（V1.4，G，授 role 1/2/3）/ voting:subject:create
--   （M3-2 扩到 GBS）已齐备。
--
-- trigger 编号：V1..V2.x 已用 1-11，M3-2 用 12，M3-3 起占用 13。
-- ===================================================================

-- ===== 1. 候选人查询索引 =====
-- findApprovedCandidates(subject_id, qualification_status=2) 与
-- countSupportByOpid 之外的候选人列表/审查查询均走 (subject_id, qualification_status)。
CREATE INDEX idx_election_candidate_subject_status
    ON t_election_candidate(subject_id, qualification_status);
COMMENT ON INDEX idx_election_candidate_subject_status IS
    '候选人按议题+资格状态过滤：settle 加载 APPROVED 候选人 / 管理端列表 / 资格审查用';

-- ===== 2. trigger 13: ELECTION 议题 max_winners 一致性兜底 =====
-- 仅约束 ELECTION（subject_type=1）：max_winners 必须非空且 >= 1。
-- 非 ELECTION（GENERAL=3 / MAJOR=2）不约束 max_winners，留 NULL（与 M3-2 insert 行为一致）。
CREATE OR REPLACE FUNCTION fn_election_max_winners_check() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.subject_type = 1 AND (NEW.max_winners IS NULL OR NEW.max_winners < 1) THEN
        RAISE EXCEPTION
            '[trigger 13] ELECTION 议题 max_winners 必须 >= 1，subject_id=% max_winners=%',
            NEW.subject_id, NEW.max_winners;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_election_max_winners
    BEFORE INSERT OR UPDATE ON t_voting_subject
    FOR EACH ROW EXECUTE FUNCTION fn_election_max_winners_check();
