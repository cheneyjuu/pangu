-- 关联业务：固化有效送达但截止未反馈表决权的逐事项认定依据，区分实际票与规则认定票。

CREATE TABLE t_voting_non_response_derivation (
    derivation_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE RESTRICT,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id) ON DELETE RESTRICT,
    electorate_item_id BIGINT NOT NULL REFERENCES t_voting_electorate_item_snapshot(snapshot_item_id) ON DELETE RESTRICT,
    tenant_id BIGINT NOT NULL,
    representative_opid BIGINT NOT NULL,
    representative_uid BIGINT NOT NULL,
    certified_area NUMERIC(14, 2) NOT NULL,
    non_response_policy VARCHAR(32) NOT NULL,
    derived_choice SMALLINT NOT NULL,
    delivery_evidence_hash CHAR(64) NOT NULL,
    rule_snapshot_hash CHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    row_hash CHAR(64) NOT NULL,
    derived_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_voting_non_response_subject_item UNIQUE (subject_id, electorate_item_id),
    CONSTRAINT chk_voting_non_response_policy CHECK (
        non_response_policy IN ('FOLLOW_MAJORITY', 'ABSTAIN')
    ),
    CONSTRAINT chk_voting_non_response_choice CHECK (derived_choice IN (1, 2, 3)),
    CONSTRAINT chk_voting_non_response_area CHECK (certified_area > 0)
);

CREATE INDEX idx_voting_non_response_package
    ON t_voting_non_response_derivation(package_id, subject_id);

COMMENT ON TABLE t_voting_non_response_derivation IS
    '截止时依据冻结议事规则形成的未反馈票认定记录；不是业主实际提交的选票';
COMMENT ON COLUMN t_voting_non_response_derivation.delivery_evidence_hash IS
    '该冻结名册项全部有效送达证据的稳定聚合摘要';
COMMENT ON COLUMN t_voting_non_response_derivation.row_hash IS
    '认定记录固定字段的 SHA-256 摘要，供结果聚合存证和争议复核';
