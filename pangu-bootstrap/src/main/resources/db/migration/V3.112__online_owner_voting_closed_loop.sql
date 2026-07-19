-- V3.112: 线上实名表决先确认锁定材料，再原子提交整包选择；互联网方式支持按业主请求提供纸票。

CREATE TABLE t_online_voting_acknowledgement (
    acknowledgement_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    electorate_item_id BIGINT NOT NULL REFERENCES t_voting_electorate_item_snapshot(snapshot_item_id),
    tenant_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    opid BIGINT NOT NULL REFERENCES c_owner_property(opid),
    package_hash CHAR(64) NOT NULL,
    acknowledgement_hash CHAR(64) NOT NULL,
    unified_delivery_id BIGINT NOT NULL REFERENCES t_voting_delivery_record(delivery_id),
    acknowledged_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_online_acknowledgement_item UNIQUE (package_id, electorate_item_id),
    CONSTRAINT uk_online_acknowledgement_delivery UNIQUE (unified_delivery_id)
);

CREATE TABLE t_online_ballot_submission (
    submission_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    electorate_item_id BIGINT NOT NULL REFERENCES t_voting_electorate_item_snapshot(snapshot_item_id),
    tenant_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    opid BIGINT NOT NULL REFERENCES c_owner_property(opid),
    idempotency_key VARCHAR(128) NOT NULL,
    package_hash CHAR(64) NOT NULL,
    choice_manifest_hash CHAR(64) NOT NULL,
    confirmation_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_online_submission_item UNIQUE (package_id, electorate_item_id),
    CONSTRAINT uk_online_submission_idempotency UNIQUE (package_id, idempotency_key),
    CONSTRAINT chk_online_submission_status CHECK (status IN ('ACCEPTED'))
);

CREATE TABLE t_online_ballot_submission_item (
    submission_item_id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES t_online_ballot_submission(submission_id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    choice SMALLINT NOT NULL,
    unified_ballot_id BIGINT NOT NULL UNIQUE REFERENCES t_voting_ballot_record(ballot_id),
    item_confirmation_hash CHAR(64) NOT NULL,
    CONSTRAINT uk_online_submission_subject UNIQUE (submission_id, subject_id),
    CONSTRAINT chk_online_submission_choice CHECK (choice IN (1, 2, 3))
);

CREATE TABLE t_online_paper_assistance_request (
    request_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_voting_execution_package(package_id) ON DELETE CASCADE,
    electorate_item_id BIGINT NOT NULL REFERENCES t_voting_electorate_item_snapshot(snapshot_item_id),
    tenant_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    opid BIGINT NOT NULL REFERENCES c_owner_property(opid),
    status VARCHAR(16) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    fulfilled_at TIMESTAMP,
    withdrawn_at TIMESTAMP,
    paper_delivery_id BIGINT REFERENCES t_paper_voting_delivery(paper_delivery_id),
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_online_paper_assistance_item UNIQUE (package_id, electorate_item_id),
    CONSTRAINT chk_online_paper_assistance_status CHECK (status IN ('REQUESTED', 'FULFILLED', 'WITHDRAWN')),
    CONSTRAINT chk_online_paper_assistance_state CHECK (
        (status = 'REQUESTED' AND fulfilled_at IS NULL AND withdrawn_at IS NULL AND paper_delivery_id IS NULL)
        OR (status = 'FULFILLED' AND fulfilled_at IS NOT NULL AND withdrawn_at IS NULL AND paper_delivery_id IS NOT NULL)
        OR (status = 'WITHDRAWN' AND fulfilled_at IS NULL AND withdrawn_at IS NOT NULL AND paper_delivery_id IS NULL)
    )
);

CREATE INDEX idx_online_acknowledgement_owner
    ON t_online_voting_acknowledgement(package_id, uid, opid);
CREATE INDEX idx_online_submission_owner
    ON t_online_ballot_submission(package_id, uid, opid);
CREATE INDEX idx_online_paper_assistance_workbench
    ON t_online_paper_assistance_request(package_id, status, requested_at, request_id);

COMMENT ON TABLE t_online_voting_acknowledgement IS '业主本人确认已阅读当前锁定表决包，同时形成线上有效送达';
COMMENT ON TABLE t_online_ballot_submission IS '业主本人对表决包全部事项的一次不可变在线确认和回执';
COMMENT ON TABLE t_online_ballot_submission_item IS '在线整包提交逐事项关联的统一有效票，不向业主端回显选择';
COMMENT ON TABLE t_online_paper_assistance_request IS '互联网表决中业主申请改用纸质表决票的办理状态';
