-- V3.110: 正式表决统一使用通用表决包、冻结表决人名册、送达和票据台账。

CREATE TABLE t_voting_execution_package (
    package_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_reference_id BIGINT NOT NULL,
    proposal_snapshot_type VARCHAR(64) NOT NULL,
    proposal_snapshot_id BIGINT NOT NULL,
    proposal_snapshot_hash CHAR(64) NOT NULL,
    rule_snapshot_type VARCHAR(64) NOT NULL,
    rule_snapshot_id BIGINT NOT NULL,
    rule_snapshot_hash CHAR(64) NOT NULL,
    scope SMALLINT NOT NULL,
    scope_reference_id BIGINT,
    collection_mode VARCHAR(48) NOT NULL,
    status VARCHAR(16) NOT NULL,
    vote_start_at TIMESTAMP NOT NULL,
    vote_end_at TIMESTAMP NOT NULL,
    package_hash CHAR(64),
    electorate_snapshot_id BIGINT,
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    frozen_by_user_id BIGINT REFERENCES sys_user(user_id),
    frozen_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_voting_execution_business UNIQUE (business_type, business_reference_id),
    CONSTRAINT chk_voting_execution_business_type CHECK (business_type IN ('OWNERS_ASSEMBLY', 'REPAIR_PROJECT')),
    CONSTRAINT chk_voting_execution_scope CHECK (scope IN (1, 2)),
    CONSTRAINT chk_voting_execution_scope_reference CHECK (
        (scope = 1 AND scope_reference_id IS NULL) OR (scope = 2 AND scope_reference_id IS NOT NULL)
    ),
    CONSTRAINT chk_voting_execution_collection_mode CHECK (
        collection_mode IN ('PAPER', 'ONLINE_WITH_PAPER_ASSISTANCE', 'PAPER_AND_ONLINE')
    ),
    CONSTRAINT chk_voting_execution_status CHECK (
        status IN ('DRAFT', 'FROZEN', 'VOTING', 'CLOSED', 'SETTLED', 'VOIDED')
    ),
    CONSTRAINT chk_voting_execution_window CHECK (vote_end_at > vote_start_at),
    CONSTRAINT chk_voting_execution_freeze_fields CHECK (
        (status = 'DRAFT' AND package_hash IS NULL AND electorate_snapshot_id IS NULL AND frozen_at IS NULL)
        OR
        (status <> 'DRAFT' AND package_hash IS NOT NULL AND electorate_snapshot_id IS NOT NULL AND frozen_at IS NOT NULL)
    )
);

CREATE INDEX idx_voting_execution_tenant_status
    ON t_voting_execution_package(tenant_id, status, vote_end_at);

CREATE TABLE t_voting_package_subject (
    package_subject_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_voting_package_subject UNIQUE (package_id, subject_id),
    CONSTRAINT uk_voting_subject_execution_package UNIQUE (subject_id)
);

CREATE TABLE t_voting_electorate_snapshot (
    snapshot_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL UNIQUE REFERENCES t_voting_execution_package(package_id) ON DELETE RESTRICT,
    tenant_id BIGINT NOT NULL,
    scope SMALLINT NOT NULL,
    scope_reference_id BIGINT,
    total_area NUMERIC(14, 2) NOT NULL,
    total_owner_count BIGINT NOT NULL,
    item_count BIGINT NOT NULL,
    aggregate_hash CHAR(64) NOT NULL,
    frozen_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_voting_electorate_scope CHECK (scope IN (1, 2)),
    CONSTRAINT chk_voting_electorate_area CHECK (total_area > 0),
    CONSTRAINT chk_voting_electorate_owners CHECK (total_owner_count > 0),
    CONSTRAINT chk_voting_electorate_items CHECK (item_count > 0)
);

ALTER TABLE t_voting_execution_package
    ADD CONSTRAINT fk_voting_execution_electorate
    FOREIGN KEY (electorate_snapshot_id) REFERENCES t_voting_electorate_snapshot(snapshot_id);

CREATE TABLE t_voting_electorate_item_snapshot (
    snapshot_item_id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES t_voting_electorate_snapshot(snapshot_id) ON DELETE CASCADE,
    roster_id BIGINT NOT NULL REFERENCES c_property_roster(roster_id),
    room_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    certified_area NUMERIC(14, 2) NOT NULL,
    representative_opid BIGINT NOT NULL REFERENCES c_owner_property(opid),
    representative_uid BIGINT NOT NULL REFERENCES c_user(uid),
    co_owner_uids JSONB NOT NULL,
    row_hash CHAR(64) NOT NULL,
    CONSTRAINT uk_voting_electorate_room UNIQUE (snapshot_id, room_id),
    CONSTRAINT uk_voting_electorate_representative UNIQUE (snapshot_id, representative_opid),
    CONSTRAINT chk_voting_electorate_item_area CHECK (certified_area > 0),
    CONSTRAINT chk_voting_electorate_coowners CHECK (jsonb_typeof(co_owner_uids) = 'array')
);

CREATE INDEX idx_voting_electorate_item_uid
    ON t_voting_electorate_item_snapshot(snapshot_id, representative_uid);

CREATE TABLE t_voting_delivery_record (
    delivery_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    electorate_item_id BIGINT NOT NULL REFERENCES t_voting_electorate_item_snapshot(snapshot_item_id),
    tenant_id BIGINT NOT NULL,
    delivery_channel SMALLINT NOT NULL,
    delivery_method VARCHAR(64) NOT NULL,
    evidence_hash VARCHAR(128) NOT NULL,
    delivered_by_user_id BIGINT REFERENCES sys_user(user_id),
    delivered_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_voting_delivery_channel UNIQUE (package_id, electorate_item_id, delivery_channel),
    CONSTRAINT chk_voting_delivery_channel CHECK (delivery_channel IN (1, 2))
);

CREATE TABLE t_voting_ballot_record (
    ballot_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    vote_id BIGINT NOT NULL UNIQUE REFERENCES t_vote_item(vote_id),
    electorate_item_id BIGINT NOT NULL REFERENCES t_voting_electorate_item_snapshot(snapshot_item_id),
    tenant_id BIGINT NOT NULL,
    vote_channel SMALLINT NOT NULL,
    package_hash CHAR(64) NOT NULL,
    ballot_file_hash VARCHAR(128),
    signature_hash VARCHAR(256),
    recorded_by_user_id BIGINT REFERENCES sys_user(user_id),
    cast_at TIMESTAMP NOT NULL,
    valid_flag SMALLINT NOT NULL DEFAULT 1,
    invalid_reason VARCHAR(500),
    invalidated_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_voting_ballot_channel CHECK (vote_channel IN (1, 2, 3)),
    CONSTRAINT chk_voting_ballot_valid CHECK (valid_flag IN (0, 1))
);

CREATE UNIQUE INDEX uk_voting_active_ballot
    ON t_voting_ballot_record(subject_id, electorate_item_id)
    WHERE valid_flag = 1;
CREATE INDEX idx_voting_ballot_package
    ON t_voting_ballot_record(package_id, subject_id, valid_flag);

CREATE TABLE t_voting_execution_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16),
    actor_user_id BIGINT REFERENCES sys_user(user_id),
    detail JSONB,
    occurred_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_voting_execution_audit_package
    ON t_voting_execution_audit(package_id, occurred_at, audit_id);

ALTER TABLE t_voting_result
    ADD COLUMN execution_package_id BIGINT REFERENCES t_voting_execution_package(package_id),
    ADD COLUMN electorate_snapshot_id BIGINT REFERENCES t_voting_electorate_snapshot(snapshot_id),
    ADD COLUMN proposal_snapshot_hash CHAR(64),
    ADD COLUMN rule_snapshot_hash CHAR(64),
    ADD COLUMN execution_package_hash CHAR(64);

COMMENT ON TABLE t_voting_execution_package IS '正式表决统一执行包：冻结方案、规则、范围、收集方式、时间和表决人名册';
COMMENT ON TABLE t_voting_electorate_snapshot IS '表决包锁定时形成的表决人名册及计票基数';
COMMENT ON TABLE t_voting_electorate_item_snapshot IS '按专有部分保存的唯一表决代表和共有人审计快照';
COMMENT ON TABLE t_voting_delivery_record IS '正式表决材料逐户送达记录，送达不等于已经投票';
COMMENT ON TABLE t_voting_ballot_record IS '纸质和线上统一票据台账，关联真正参与计票的 t_vote_item';
COMMENT ON TABLE t_voting_execution_audit IS '表决包冻结、开始、收票、截止和结算的执行审计';
COMMENT ON COLUMN t_voting_result.execution_package_id IS '产生本结果的通用表决包';
COMMENT ON COLUMN t_voting_result.electorate_snapshot_id IS '结算使用的冻结表决人名册';
