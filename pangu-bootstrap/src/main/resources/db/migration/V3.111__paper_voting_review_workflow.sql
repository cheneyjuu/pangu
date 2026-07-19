-- V3.111: 纸质投票原件先完成送达核对和双人录入复核，再进入统一有效票台账。

CREATE TABLE t_paper_voting_delivery (
    paper_delivery_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    electorate_item_id BIGINT NOT NULL REFERENCES t_voting_electorate_item_snapshot(snapshot_item_id),
    tenant_id BIGINT NOT NULL,
    recipient_name VARCHAR(128) NOT NULL,
    delivery_method VARCHAR(64) NOT NULL,
    evidence_source_type VARCHAR(48) NOT NULL,
    evidence_source_id BIGINT NOT NULL,
    evidence_hash VARCHAR(128) NOT NULL,
    delivered_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    delivered_at TIMESTAMP NOT NULL,
    status VARCHAR(24) NOT NULL,
    reviewed_by_user_id BIGINT REFERENCES sys_user(user_id),
    reviewed_at TIMESTAMP,
    review_note VARCHAR(500),
    unified_delivery_id BIGINT REFERENCES t_voting_delivery_record(delivery_id),
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_paper_delivery_evidence UNIQUE (package_id, evidence_source_type, evidence_source_id),
    CONSTRAINT chk_paper_delivery_status CHECK (status IN ('PENDING_REVIEW', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT chk_paper_delivery_review CHECK (
        (status = 'PENDING_REVIEW' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL
            AND review_note IS NULL AND unified_delivery_id IS NULL)
        OR (status = 'CONFIRMED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND unified_delivery_id IS NOT NULL)
        OR (status = 'REJECTED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND review_note IS NOT NULL AND unified_delivery_id IS NULL)
    )
);

CREATE UNIQUE INDEX uk_paper_confirmed_delivery
    ON t_paper_voting_delivery(package_id, electorate_item_id)
    WHERE status = 'CONFIRMED';
CREATE INDEX idx_paper_delivery_workbench
    ON t_paper_voting_delivery(package_id, status, delivered_at, paper_delivery_id);

CREATE TABLE t_paper_ballot (
    paper_ballot_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    electorate_item_id BIGINT NOT NULL REFERENCES t_voting_electorate_item_snapshot(snapshot_item_id),
    tenant_id BIGINT NOT NULL,
    ballot_number VARCHAR(80) NOT NULL,
    template_hash CHAR(64) NOT NULL,
    material_source_type VARCHAR(48) NOT NULL,
    material_source_id BIGINT NOT NULL,
    material_hash VARCHAR(128) NOT NULL,
    received_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    received_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL,
    voided_by_user_id BIGINT REFERENCES sys_user(user_id),
    voided_at TIMESTAMP,
    void_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_paper_ballot_number UNIQUE (package_id, ballot_number),
    CONSTRAINT uk_paper_ballot_material UNIQUE (package_id, material_source_type, material_source_id),
    CONSTRAINT chk_paper_ballot_status CHECK (status IN ('RECEIVED', 'IN_ENTRY', 'COMPLETED', 'VOIDED')),
    CONSTRAINT chk_paper_ballot_void CHECK (
        (status <> 'VOIDED' AND voided_by_user_id IS NULL AND voided_at IS NULL AND void_reason IS NULL)
        OR (status = 'VOIDED' AND voided_by_user_id IS NOT NULL AND voided_at IS NOT NULL AND void_reason IS NOT NULL)
    )
);

CREATE INDEX idx_paper_ballot_workbench
    ON t_paper_ballot(package_id, status, received_at, paper_ballot_id);

CREATE TABLE t_paper_ballot_entry (
    entry_id BIGSERIAL PRIMARY KEY,
    paper_ballot_id BIGINT NOT NULL REFERENCES t_paper_ballot(paper_ballot_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    version_number INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    entered_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    entered_at TIMESTAMP NOT NULL,
    reviewed_by_user_id BIGINT REFERENCES sys_user(user_id),
    reviewed_at TIMESTAMP,
    review_note VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_paper_ballot_entry_version UNIQUE (paper_ballot_id, version_number),
    CONSTRAINT chk_paper_entry_version CHECK (version_number > 0),
    CONSTRAINT chk_paper_entry_status CHECK (status IN ('PENDING_REVIEW', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT chk_paper_entry_reviewer CHECK (
        reviewed_by_user_id IS NULL OR reviewed_by_user_id <> entered_by_user_id
    ),
    CONSTRAINT chk_paper_entry_review CHECK (
        (status = 'PENDING_REVIEW' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL AND review_note IS NULL)
        OR (status = 'CONFIRMED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL)
        OR (status = 'REJECTED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL
            AND review_note IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_paper_ballot_pending_entry
    ON t_paper_ballot_entry(paper_ballot_id)
    WHERE status = 'PENDING_REVIEW';

CREATE TABLE t_paper_ballot_entry_item (
    entry_item_id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES t_paper_ballot_entry(entry_id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    determination VARCHAR(16) NOT NULL,
    choice SMALLINT,
    invalid_reason_code VARCHAR(32),
    invalid_reason_description VARCHAR(500),
    CONSTRAINT uk_paper_entry_subject UNIQUE (entry_id, subject_id),
    CONSTRAINT chk_paper_entry_determination CHECK (determination IN ('VALID', 'INVALID')),
    CONSTRAINT chk_paper_entry_item_content CHECK (
        (determination = 'VALID' AND choice IN (1, 2, 3)
            AND invalid_reason_code IS NULL AND invalid_reason_description IS NULL)
        OR (determination = 'INVALID' AND choice IS NULL AND invalid_reason_code IS NOT NULL)
    )
);

CREATE TABLE t_paper_ballot_outcome (
    outcome_id BIGSERIAL PRIMARY KEY,
    paper_ballot_id BIGINT NOT NULL REFERENCES t_paper_ballot(paper_ballot_id) ON DELETE CASCADE,
    entry_id BIGINT NOT NULL REFERENCES t_paper_ballot_entry(entry_id),
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    status VARCHAR(16) NOT NULL,
    unified_ballot_id BIGINT REFERENCES t_voting_ballot_record(ballot_id),
    conflicting_ballot_id BIGINT REFERENCES t_voting_ballot_record(ballot_id),
    reason VARCHAR(500),
    finalized_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_paper_ballot_subject_outcome UNIQUE (paper_ballot_id, subject_id),
    CONSTRAINT chk_paper_outcome_status CHECK (status IN ('COUNTED', 'INVALID', 'DUPLICATE')),
    CONSTRAINT chk_paper_outcome_reference CHECK (
        (status = 'COUNTED' AND unified_ballot_id IS NOT NULL AND conflicting_ballot_id IS NULL)
        OR (status = 'INVALID' AND unified_ballot_id IS NULL AND conflicting_ballot_id IS NULL AND reason IS NOT NULL)
        OR (status = 'DUPLICATE' AND unified_ballot_id IS NULL AND conflicting_ballot_id IS NOT NULL AND reason IS NOT NULL)
    )
);

COMMENT ON TABLE t_paper_voting_delivery IS '纸质表决材料送达原始登记及核对结果；确认后关联统一送达台账';
COMMENT ON TABLE t_paper_ballot IS '回收纸票原件、票号和冻结模板摘要；登记本身不等于有效投票';
COMMENT ON TABLE t_paper_ballot_entry IS '纸票不可变录入版本及另一名人员的复核结论';
COMMENT ON TABLE t_paper_ballot_entry_item IS '每版纸票录入对表决包内各事项的有效选择或无效原因';
COMMENT ON TABLE t_paper_ballot_outcome IS '复核确认后逐事项计入、无效或重复材料结果';
