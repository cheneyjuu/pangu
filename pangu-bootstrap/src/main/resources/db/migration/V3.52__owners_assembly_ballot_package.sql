-- V3.52: 业主大会表决包、纸质/线上双渠道投票留痕、报修业主大会决策关联。

ALTER TABLE t_vote_item
    ADD COLUMN IF NOT EXISTS valid_flag SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS invalid_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS invalidated_at TIMESTAMP;

ALTER TABLE t_vote_item DROP CONSTRAINT IF EXISTS chk_vote_item_valid_flag;
ALTER TABLE t_vote_item
    ADD CONSTRAINT chk_vote_item_valid_flag CHECK (valid_flag IN (0, 1));

DROP INDEX IF EXISTS uidx_vote_item_subject_op_target;
CREATE UNIQUE INDEX uidx_vote_item_subject_op_target
    ON t_vote_item(subject_id, opid, COALESCE(target_id, 0))
    WHERE valid_flag = 1;

CREATE INDEX IF NOT EXISTS idx_vote_item_active_subject_opid
    ON t_vote_item(subject_id, opid, COALESCE(target_id, 0), vote_id)
    WHERE valid_flag = 1;

COMMENT ON COLUMN t_vote_item.valid_flag IS '1=有效计票；0=被线上实名票等后续有效票替换后作废，仅保留审计';
COMMENT ON COLUMN t_vote_item.invalid_reason IS '作废原因';
COMMENT ON COLUMN t_vote_item.invalidated_at IS '作废时间';

CREATE TABLE t_owners_assembly_session (
    session_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    preparation_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_owners_assembly_session_mode CHECK (preparation_mode IN ('FULL', 'QUICK')),
    CONSTRAINT chk_owners_assembly_session_status CHECK (
        status IN ('PREPARING', 'PACKAGE_DRAFT', 'PUBLIC_NOTICE', 'VOTING', 'SETTLED', 'VOIDED')
    )
);

CREATE INDEX idx_owners_assembly_session_tenant
    ON t_owners_assembly_session(tenant_id, create_time DESC);

COMMENT ON TABLE t_owners_assembly_session IS '一次业主大会；会前方案沟通和正式表决包分离';

CREATE TABLE t_owners_assembly_package (
    package_id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES t_owners_assembly_session(session_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    package_version INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    voting_channel_policy VARCHAR(32) NOT NULL,
    public_notice_days INT NOT NULL DEFAULT 7,
    announcement_hash VARCHAR(128) NOT NULL,
    attachment_manifest_hash VARCHAR(128) NOT NULL,
    ballot_template_hash VARCHAR(128) NOT NULL,
    electronic_seal_hash VARCHAR(128),
    package_hash VARCHAR(128),
    public_notice_start_at TIMESTAMP,
    public_notice_end_at TIMESTAMP,
    vote_start_at TIMESTAMP NOT NULL,
    vote_end_at TIMESTAMP NOT NULL,
    locked_by_user_id BIGINT REFERENCES sys_user(user_id),
    locked_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_owners_assembly_package_status CHECK (
        status IN ('PACKAGE_DRAFT', 'PUBLIC_NOTICE', 'VOTING', 'SETTLED', 'VOIDED')
    ),
    CONSTRAINT chk_owners_assembly_package_channel CHECK (
        voting_channel_policy IN ('PAPER_ONLY', 'ONLINE_ONLY', 'PAPER_AND_ONLINE')
    ),
    CONSTRAINT chk_owners_assembly_notice_days CHECK (public_notice_days >= 7),
    CONSTRAINT chk_owners_assembly_vote_window CHECK (vote_end_at > vote_start_at)
);

CREATE INDEX idx_owners_assembly_package_session
    ON t_owners_assembly_package(session_id, package_version DESC);
CREATE INDEX idx_owners_assembly_package_tenant
    ON t_owners_assembly_package(tenant_id, status, vote_end_at);

COMMENT ON TABLE t_owners_assembly_package IS '业主大会正式表决包：公告、附件、选票模板、公章和哈希锁定单元';
COMMENT ON COLUMN t_owners_assembly_package.voting_channel_policy IS 'PAPER_ONLY=纸质；ONLINE_ONLY=线上；PAPER_AND_ONLINE=两条线并行';
COMMENT ON COLUMN t_owners_assembly_package.package_hash IS '公告、附件、选票模板、投票事项等版本化内容哈希';

CREATE TABLE t_owners_assembly_subject (
    assembly_subject_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_owners_assembly_package(package_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_owners_assembly_subject
    ON t_owners_assembly_subject(package_id, subject_id);
CREATE UNIQUE INDEX uk_owners_assembly_subject_once
    ON t_owners_assembly_subject(subject_id);

COMMENT ON TABLE t_owners_assembly_subject IS '表决包内的正式投票事项，一个业主大会可包含多个 voting_subject';

CREATE TABLE t_owners_assembly_delivery (
    delivery_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_owners_assembly_package(package_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    opid BIGINT NOT NULL REFERENCES c_owner_property(opid),
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    delivery_channel VARCHAR(32) NOT NULL,
    delivery_method VARCHAR(64) NOT NULL,
    evidence_hash VARCHAR(128) NOT NULL,
    delivered_by_user_id BIGINT REFERENCES sys_user(user_id),
    delivered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_owners_assembly_delivery_channel CHECK (delivery_channel IN ('PAPER', 'ONLINE'))
);

CREATE UNIQUE INDEX uk_owners_assembly_delivery_channel
    ON t_owners_assembly_delivery(package_id, opid, delivery_channel);
CREATE INDEX idx_owners_assembly_delivery_uid
    ON t_owners_assembly_delivery(tenant_id, uid, delivered_at DESC);

COMMENT ON TABLE t_owners_assembly_delivery IS '纸质送达或线上推送证据；投票前必须有对应通道送达记录';

CREATE TABLE t_owners_assembly_vote_record (
    assembly_vote_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_owners_assembly_package(package_id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    vote_id BIGINT NOT NULL REFERENCES t_vote_item(vote_id),
    tenant_id BIGINT NOT NULL,
    opid BIGINT NOT NULL REFERENCES c_owner_property(opid),
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    vote_channel VARCHAR(32) NOT NULL,
    package_hash VARCHAR(128) NOT NULL,
    ballot_file_hash VARCHAR(128) NOT NULL,
    signature_hash VARCHAR(256),
    valid_flag SMALLINT NOT NULL DEFAULT 1,
    invalidated_by_vote_id BIGINT,
    invalid_reason VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_owners_assembly_vote_channel CHECK (vote_channel IN ('PAPER', 'ONLINE')),
    CONSTRAINT chk_owners_assembly_vote_valid CHECK (valid_flag IN (0, 1))
);

CREATE UNIQUE INDEX uk_owners_assembly_vote_record_vote
    ON t_owners_assembly_vote_record(vote_id);
CREATE UNIQUE INDEX uk_owners_assembly_active_vote_record
    ON t_owners_assembly_vote_record(subject_id, opid)
    WHERE valid_flag = 1;
CREATE INDEX idx_owners_assembly_vote_package
    ON t_owners_assembly_vote_record(package_id, subject_id, valid_flag);

COMMENT ON TABLE t_owners_assembly_vote_record IS '业主大会投票审计记录，关联真正计票的 t_vote_item';
COMMENT ON COLUMN t_owners_assembly_vote_record.signature_hash IS '线上电子签名摘要；纸票可为空';

CREATE TABLE t_repair_assembly_decision (
    repair_assembly_decision_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL UNIQUE REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL REFERENCES t_owners_assembly_package(package_id),
    result VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_assembly_decision_result CHECK (result IN ('PENDING', 'PASSED', 'FAILED'))
);

CREATE INDEX idx_repair_assembly_decision_package
    ON t_repair_assembly_decision(package_id);

COMMENT ON TABLE t_repair_assembly_decision IS '跨楼栋或小区整体维修关联业主大会正式表决包';
