-- ===================================================================
-- V3.2 — 党组书记前置审查（候选人资格审查两段化）
--
-- 背景：M3-3 把 ELECTION 候选人资格审查接通为单段闸
--   （PENDING_REVIEW --candidate:approve--> APPROVED/REJECTED）。
-- 本期按「党建引领 + 居委会把关」治理语义，在居委会资格审查前插入一道
-- 党组书记前置审查（政治/资格初筛），资格审查变为两段：
--
--   提名 → PENDING_PARTY_REVIEW(1)
--        --党组书记前置审查 approve--> PENDING_COMMITTEE_REVIEW(5)
--        --党组书记前置审查 reject -->  REJECTED(3)
--   PENDING_COMMITTEE_REVIEW(5)
--        --居委会资格审查 approve--> APPROVED(2)
--        --居委会资格审查 reject -->  REJECTED(3)
--
-- 数据零迁移：党组前置审查待办沿用历史值 1（原 PENDING_REVIEW 语义平移），
-- 仅新增待办值 5（PENDING_COMMITTEE_REVIEW）。
--
-- 权责严格分权：
--   - 新增 candidate:review:party（党组书记前置审查），授 GOV_SUPER_ADMIN(1) + PARTY_SECRETARY(3)；
--   - candidate:approve（居委会资格审查）从 PARTY_SECRETARY(3) 收回，仅留 GOV_SUPER_ADMIN(1) + COMMUNITY_ADMIN(2)。
-- 自此党组只做前置政审、居委会只做资格把关，街道办超管保留双闸 override。
--
-- 无新增 trigger（仅 ALTER 既有 CHECK 约束）；trigger 编号沿用 V3.1（已占用至 13）。
-- ===================================================================

-- ===== 1. 放开 qualification_status CHECK 约束：允许新增待办值 5 =====
ALTER TABLE t_election_candidate DROP CONSTRAINT chk_candidate_qualification;
ALTER TABLE t_election_candidate ADD CONSTRAINT chk_candidate_qualification
    CHECK (qualification_status IN (1, 2, 3, 4, 5));

COMMENT ON COLUMN t_election_candidate.qualification_status IS
    '资格状态：1-待党组前置审查(PENDING_PARTY_REVIEW), 2-通过(APPROVED), 3-驳回(REJECTED), 4-退出(WITHDRAWN), 5-待居委会资格审查(PENDING_COMMITTEE_REVIEW)';

-- ===== 2. 新增 candidate:review:party 能力点 =====
-- 'G' 端能力（党组书记 PARTY_SECRETARY 属 G）；redline=0，与 candidate:approve 一致。
INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('candidate:review:party', '党组书记前置审查（政治/资格初筛）', 'VOTING', 'G', 0);

-- ===== 3. 授权：candidate:review:party → GOV_SUPER_ADMIN(1) + PARTY_SECRETARY(3) =====
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (1, 'candidate:review:party'),
    (3, 'candidate:review:party');

-- ===== 4. 严格分权：从 PARTY_SECRETARY(3) 收回 candidate:approve =====
-- 党组书记自此只做前置政审，不再直接做居委会资格审查；
-- candidate:approve 仅留 GOV_SUPER_ADMIN(1) + COMMUNITY_ADMIN(2)。
DELETE FROM sys_role_permission WHERE role_id = 3 AND permission_key = 'candidate:approve';
