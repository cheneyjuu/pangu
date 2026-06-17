-- ===================================================================
-- 1. 表决议题主表 (t_voting_subject)
--    选举与决议复用同一张表；scope 决定分母范围（社区/楼栋/单元）
-- ===================================================================
CREATE TABLE t_voting_subject (
    subject_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    subject_type SMALLINT NOT NULL,
    scope SMALLINT NOT NULL DEFAULT 1,
    scope_reference_id BIGINT,
    status SMALLINT NOT NULL DEFAULT 1,
    publish_at TIMESTAMP,
    vote_start_at TIMESTAMP,
    vote_end_at TIMESTAMP,
    settled_at TIMESTAMP,
    party_ratio_floor DECIMAL(4,2) NOT NULL DEFAULT 0.50,
    max_winners INT,
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_subject_type CHECK (subject_type IN (1, 2, 3)),
    -- scope=3 (UNIT) 暂未实现：c_owner_property 缺少 unit_id 字段；本期只允许 1/2 入库，
    -- 待后续迁移补 unit_id + DefaultVotingDenominatorResolver 实现 UNIT 路径后再放开
    CONSTRAINT chk_subject_scope CHECK (scope IN (1, 2)),
    CONSTRAINT chk_subject_status CHECK (status IN (1, 2, 3, 4, 5)),
    CONSTRAINT chk_party_ratio_floor CHECK (party_ratio_floor >= 0.00 AND party_ratio_floor <= 1.00)
);

CREATE INDEX idx_voting_subject_tenant ON t_voting_subject(tenant_id);
CREATE INDEX idx_voting_subject_status_end ON t_voting_subject(status, vote_end_at);

COMMENT ON TABLE t_voting_subject IS '表决议题主表（选举/重大决议/一般决议复用）';
COMMENT ON COLUMN t_voting_subject.subject_type IS '议题类型：1-选举(ELECTION), 2-重大决议(MAJOR), 3-一般决议(GENERAL)';
COMMENT ON COLUMN t_voting_subject.scope IS '分母范围：1-社区(COMMUNITY), 2-楼栋(BUILDING)；UNIT 暂未实现';
COMMENT ON COLUMN t_voting_subject.scope_reference_id IS '范围引用 ID：scope=BUILDING 时为 building_id，COMMUNITY 时可为 NULL';
COMMENT ON COLUMN t_voting_subject.status IS '状态：1-草稿(DRAFT), 2-已公示(PUBLISHED), 3-投票中(VOTING), 4-已截止(CLOSED), 5-已结算(SETTLED)';
COMMENT ON COLUMN t_voting_subject.party_ratio_floor IS '党员比例下限（默认 0.50；放宽申请通过后由 ApplicationService 写入实际值）';
COMMENT ON COLUMN t_voting_subject.max_winners IS '应选当选人数（仅选举议题使用）';
COMMENT ON COLUMN t_voting_subject.version IS '乐观锁版本号（防重复结算）';

-- ===================================================================
-- 2. 选举候选人表 (t_election_candidate)
-- ===================================================================
CREATE TABLE t_election_candidate (
    candidate_id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    uid BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    is_party_member SMALLINT NOT NULL DEFAULT 0,
    qualification_status SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_candidate_party CHECK (is_party_member IN (0, 1)),
    CONSTRAINT chk_candidate_qualification CHECK (qualification_status IN (1, 2, 3, 4))
);

CREATE INDEX idx_election_candidate_subject ON t_election_candidate(subject_id);
CREATE UNIQUE INDEX uidx_election_candidate_subject_uid ON t_election_candidate(subject_id, uid);

COMMENT ON TABLE t_election_candidate IS '选举议题候选人表（仅 subject_type=ELECTION 时使用）';
COMMENT ON COLUMN t_election_candidate.is_party_member IS '是否党员：0-否, 1-是';
COMMENT ON COLUMN t_election_candidate.qualification_status IS '资格状态：1-待审核(PENDING_REVIEW), 2-通过(APPROVED), 3-驳回(REJECTED), 4-退出(WITHDRAWN)';

-- ===================================================================
-- 3. 投票明细表 (t_vote_item)
--    一票一行；UNIQUE(subject_id, opid, target_id) 防同一房产对同一目标重复投票
-- ===================================================================
CREATE TABLE t_vote_item (
    vote_id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    opid BIGINT NOT NULL,
    uid BIGINT NOT NULL,
    target_id BIGINT,
    property_area DECIMAL(10,2) NOT NULL,
    choice SMALLINT NOT NULL,
    voted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signature_hash VARCHAR(128),
    CONSTRAINT chk_vote_choice CHECK (choice IN (1, 2, 3)),
    CONSTRAINT chk_vote_area_positive CHECK (property_area > 0)
);

CREATE INDEX idx_vote_item_subject ON t_vote_item(subject_id);
CREATE INDEX idx_vote_item_subject_uid ON t_vote_item(subject_id, uid);
CREATE UNIQUE INDEX uidx_vote_item_subject_op_target ON t_vote_item(subject_id, opid, COALESCE(target_id, 0));

COMMENT ON TABLE t_vote_item IS '投票明细表（一票一行，含双重去重所需字段）';
COMMENT ON COLUMN t_vote_item.target_id IS '投票对象 ID：选举时为候选人 ID；议案表决时为 NULL（每议题一票）';
COMMENT ON COLUMN t_vote_item.property_area IS '该票对应的房产专有面积（投票时落定快照）';
COMMENT ON COLUMN t_vote_item.choice IS '投票选择：1-赞成(SUPPORT), 2-反对(OPPOSE), 3-弃权(ABSTAIN)';
COMMENT ON COLUMN t_vote_item.signature_hash IS '电子签名摘要（可选）';

-- ===================================================================
-- 4. 表决结果快照表 (t_voting_result)
--    settle 完写入；可重复 settle 但需带 settle_version 保护
-- ===================================================================
CREATE TABLE t_voting_result (
    result_id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL UNIQUE REFERENCES t_voting_subject(subject_id),
    settle_version INT NOT NULL DEFAULT 1,
    total_area DECIMAL(14,2) NOT NULL,
    total_owner_count BIGINT NOT NULL,
    participating_area DECIMAL(14,2) NOT NULL,
    participating_owner_count BIGINT NOT NULL,
    quorum_satisfied SMALLINT NOT NULL,
    passed SMALLINT NOT NULL,
    result_payload JSONB,
    denominator_snapshot_id BIGINT,
    attestation_tx_hash VARCHAR(128),
    settled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_result_quorum CHECK (quorum_satisfied IN (0, 1)),
    CONSTRAINT chk_result_passed CHECK (passed IN (0, 1))
);

CREATE INDEX idx_voting_result_settled_at ON t_voting_result(settled_at);

COMMENT ON TABLE t_voting_result IS '表决结果快照表（一议题最多一行）';
COMMENT ON COLUMN t_voting_result.settle_version IS '结算版本（重新结算时递增）';
COMMENT ON COLUMN t_voting_result.quorum_satisfied IS '法定人数/面积是否双过 2/3：0-未达, 1-达标';
COMMENT ON COLUMN t_voting_result.passed IS '议题是否通过：0-未通过, 1-通过';
COMMENT ON COLUMN t_voting_result.result_payload IS '强类型结果序列化（候选人当选名单/赞成数等）';
COMMENT ON COLUMN t_voting_result.denominator_snapshot_id IS '关联的分母快照 ID（外键稍后由 V2.3 引入）';
COMMENT ON COLUMN t_voting_result.attestation_tx_hash IS '司法链存证回执 hash（stub 阶段为 STUB-{eventId}）';
