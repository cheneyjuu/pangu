-- ===================================================================
-- 1. 党员比例放宽申请主表 (t_party_ratio_waiver)
--    G 端刚性前置审批状态机：DRAFT → PENDING_COMMITTEE → PENDING_STREET → APPROVED → APPLIED
--    分支：REJECTED / REVOKED / REVOKED_BY_SYSTEM
-- ===================================================================
CREATE TABLE t_party_ratio_waiver (
    waiver_id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    initiator_user_id BIGINT NOT NULL,
    requested_ratio DECIMAL(4,2) NOT NULL,
    party_pool_size BIGINT NOT NULL,
    total_eligible_size BIGINT NOT NULL,
    reason_text TEXT NOT NULL,
    reason_evidence_keys TEXT,
    status SMALLINT NOT NULL DEFAULT 1,
    committee_approver BIGINT,
    committee_approval_at TIMESTAMP,
    committee_opinion TEXT,
    street_approver BIGINT,
    street_approval_at TIMESTAMP,
    street_opinion TEXT,
    applied_at TIMESTAMP,
    local_payload_hash VARCHAR(64),
    local_payload_locked_at TIMESTAMP,
    blockchain_tx_hash VARCHAR(128),
    blockchain_chain_provider VARCHAR(32),
    chain_attest_status SMALLINT NOT NULL DEFAULT 1,
    chain_attest_attempts INT NOT NULL DEFAULT 0,
    chain_attest_last_error TEXT,
    chain_confirmed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_waiver_status CHECK (status IN (1, 2, 3, 4, 5, 6, 7, 8)),
    CONSTRAINT chk_waiver_chain_attest CHECK (chain_attest_status IN (1, 2, 3, 4)),
    CONSTRAINT chk_waiver_ratio CHECK (requested_ratio >= 0.00 AND requested_ratio < 0.50),
    CONSTRAINT chk_waiver_pool_nonneg CHECK (party_pool_size >= 0 AND total_eligible_size >= 0),
    CONSTRAINT chk_waiver_approver_diff CHECK (
        committee_approver IS NULL OR street_approver IS NULL OR committee_approver <> street_approver
    )
);

-- 同议题至多一条活跃 waiver（部分唯一索引；终止态不计入）
-- 终止态 = REJECTED(5), REVOKED(6), REVOKED_BY_SYSTEM(7), APPLIED(8)
-- 活跃态 = DRAFT(1), PENDING_COMMITTEE(2), PENDING_STREET(3), APPROVED(4)
CREATE UNIQUE INDEX uidx_waiver_active_per_subject
    ON t_party_ratio_waiver(subject_id)
    WHERE status NOT IN (5, 6, 7, 8);
CREATE INDEX idx_waiver_tenant ON t_party_ratio_waiver(tenant_id);
CREATE INDEX idx_waiver_status ON t_party_ratio_waiver(status);

COMMENT ON TABLE t_party_ratio_waiver IS '党员比例放宽申请表（G 端刚性前置审批状态机）';
COMMENT ON COLUMN t_party_ratio_waiver.initiator_user_id IS '申请发起人（必须 dept_type=2 居委会）';
COMMENT ON COLUMN t_party_ratio_waiver.requested_ratio IS '申请放宽至的党员比例（0.00 ~ 0.50，不含 0.50）';
COMMENT ON COLUMN t_party_ratio_waiver.party_pool_size IS '申报时的党员池快照（候选人池中的党员人数）';
COMMENT ON COLUMN t_party_ratio_waiver.total_eligible_size IS '申报时的合格候选人池快照';
COMMENT ON COLUMN t_party_ratio_waiver.reason_text IS '申请理由（实质字符 ≥ 50；纯 Java 香农熵+3-gram 重复率校验）';
COMMENT ON COLUMN t_party_ratio_waiver.reason_evidence_keys IS 'OSS 证据材料 key 列表（逗号分隔字符串，避免 PG 数组跨库兼容）';
COMMENT ON COLUMN t_party_ratio_waiver.status IS '状态：1-DRAFT, 2-PENDING_COMMITTEE, 3-PENDING_STREET, 4-APPROVED, 5-REJECTED, 6-REVOKED, 7-REVOKED_BY_SYSTEM, 8-APPLIED';
COMMENT ON COLUMN t_party_ratio_waiver.committee_approver IS '居委会初审人（dept_type=2）';
COMMENT ON COLUMN t_party_ratio_waiver.street_approver IS '街道办终审人（dept_type=1）；与 committee_approver 不可相同';
COMMENT ON COLUMN t_party_ratio_waiver.local_payload_hash IS 'APPROVED 瞬间锁定的本地 payload SHA256（64 hex）';
COMMENT ON COLUMN t_party_ratio_waiver.blockchain_tx_hash IS '司法链回执 hash（异步反填；stub 阶段为 STUB-{eventId}）';
COMMENT ON COLUMN t_party_ratio_waiver.chain_attest_status IS '链上存证状态：1-PENDING, 2-SUBMITTED, 3-CONFIRMED, 4-FAILED';
COMMENT ON COLUMN t_party_ratio_waiver.version IS '乐观锁版本号';

-- ===================================================================
-- 2. 断路器对账记录表 (t_waiver_snapshot_comparison)
--    settle / publish_day / vote_end 三点对账，即使不撤销也写入审计
-- ===================================================================
CREATE TABLE t_waiver_snapshot_comparison (
    comparison_id BIGSERIAL PRIMARY KEY,
    waiver_id BIGINT NOT NULL REFERENCES t_party_ratio_waiver(waiver_id),
    subject_id BIGINT NOT NULL,
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trigger_phase SMALLINT NOT NULL,
    recorded_party_count BIGINT NOT NULL,
    recorded_eligible_count BIGINT NOT NULL,
    recorded_ratio DECIMAL(6,4) NOT NULL,
    current_party_count BIGINT NOT NULL,
    current_eligible_count BIGINT NOT NULL,
    current_natural_ratio DECIMAL(6,4) NOT NULL,
    delta_party BIGINT NOT NULL,
    delta_eligible BIGINT NOT NULL,
    action_taken SMALLINT NOT NULL,
    audit_hash VARCHAR(64) NOT NULL,
    CONSTRAINT chk_waiver_snap_phase CHECK (trigger_phase IN (1, 2, 3)),
    CONSTRAINT chk_waiver_snap_action CHECK (action_taken IN (1, 2, 3))
);

CREATE INDEX idx_waiver_snap_waiver ON t_waiver_snapshot_comparison(waiver_id);
CREATE INDEX idx_waiver_snap_subject ON t_waiver_snapshot_comparison(subject_id);

COMMENT ON TABLE t_waiver_snapshot_comparison IS 'Waiver 断路器三点对账记录（审计 + 自动撤销决策依据）';
COMMENT ON COLUMN t_waiver_snapshot_comparison.trigger_phase IS '触发时机：1-SETTLE, 2-PUBLISH_DAY, 3-VOTE_END';
COMMENT ON COLUMN t_waiver_snapshot_comparison.action_taken IS '动作：1-NONE(放行), 2-REVOKED_BY_SYSTEM(自然达标自动撤销), 3-WARN_REGRESSION(党员池倒退告警)';
COMMENT ON COLUMN t_waiver_snapshot_comparison.audit_hash IS '本行审计 hash（防篡改）';
