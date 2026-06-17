-- ===================================================================
-- 1. 投票分母聚合快照 (t_voting_denominator_snapshot)
--    每议题至多一行；aggregate_hash 为行级 row_hash 的 Merkle root
-- ===================================================================
CREATE TABLE t_voting_denominator_snapshot (
    snapshot_id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL UNIQUE REFERENCES t_voting_subject(subject_id),
    scope SMALLINT NOT NULL,
    scope_reference_id BIGINT,
    total_area DECIMAL(14,2) NOT NULL,
    total_owner_count BIGINT NOT NULL,
    item_count BIGINT NOT NULL,
    aggregate_hash VARCHAR(64) NOT NULL,
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_denom_total_area_positive CHECK (total_area > 0),
    CONSTRAINT chk_denom_total_owner_positive CHECK (total_owner_count > 0)
);

CREATE INDEX idx_denom_snap_at ON t_voting_denominator_snapshot(snapshot_at);

COMMENT ON TABLE t_voting_denominator_snapshot IS '投票分母聚合快照（双重去重后落定的不可变记录）';
COMMENT ON COLUMN t_voting_denominator_snapshot.scope IS '范围：1-COMMUNITY, 2-BUILDING, 3-UNIT';
COMMENT ON COLUMN t_voting_denominator_snapshot.total_area IS '专有面积分母（room_id 去重）';
COMMENT ON COLUMN t_voting_denominator_snapshot.total_owner_count IS '业主人数分母（primary_owner_uid 去重）';
COMMENT ON COLUMN t_voting_denominator_snapshot.aggregate_hash IS '行级 row_hash 的 Merkle root（SHA256 hex）';

-- ===================================================================
-- 2. 投票分母行级明细 (t_voting_denominator_item_snapshot)
--    每行对应一个 room；为应对群体性质询可逐行还原
-- ===================================================================
CREATE TABLE t_voting_denominator_item_snapshot (
    item_id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES t_voting_denominator_snapshot(snapshot_id),
    room_id BIGINT NOT NULL,
    building_id BIGINT,
    certified_area DECIMAL(10,2) NOT NULL,
    primary_owner_uid BIGINT NOT NULL,
    co_owner_uids TEXT,
    eligibility_flag SMALLINT NOT NULL,
    row_hash VARCHAR(64) NOT NULL,
    CONSTRAINT chk_item_eligibility CHECK (eligibility_flag IN (1, 2, 3, 4))
);

CREATE INDEX idx_item_snap_id ON t_voting_denominator_item_snapshot(snapshot_id);

COMMENT ON TABLE t_voting_denominator_item_snapshot IS '投票分母行级明细（room 维度）';
COMMENT ON COLUMN t_voting_denominator_item_snapshot.primary_owner_uid IS '该房代表业主 UID（优先 is_voting_delegate=1，次按 opid 升序）';
COMMENT ON COLUMN t_voting_denominator_item_snapshot.co_owner_uids IS '共有产权人 UID 列表（逗号分隔字符串）';
COMMENT ON COLUMN t_voting_denominator_item_snapshot.eligibility_flag IS '资格标记：1-ELIGIBLE, 2-RESTRICTED_BY_FEE, 3-EXCLUDED_PARKING, 4-EXCLUDED_OTHER';
COMMENT ON COLUMN t_voting_denominator_item_snapshot.row_hash IS '行级 SHA256 摘要';

-- ===================================================================
-- 3. 补全 V2.0 中 t_voting_result.denominator_snapshot_id 的外键
-- ===================================================================
ALTER TABLE t_voting_result
    ADD CONSTRAINT fk_voting_result_denom_snapshot
    FOREIGN KEY (denominator_snapshot_id) REFERENCES t_voting_denominator_snapshot(snapshot_id);
