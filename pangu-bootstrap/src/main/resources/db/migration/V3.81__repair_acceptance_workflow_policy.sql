-- 关联业务：按楼栋维修与全小区公共维修分别冻结验收规则、参与人和用印证据。

CREATE TABLE t_repair_acceptance_policy_snapshot (
    policy_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    workflow_type VARCHAR(40) NOT NULL,
    policy_hash VARCHAR(64) NOT NULL,
    affected_owner_count INT NOT NULL,
    minimum_affected_owner_participants INT,
    minimum_affected_owner_approvals INT,
    version INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    locked_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_acceptance_policy_version UNIQUE (work_order_id, version),
    CONSTRAINT chk_repair_acceptance_workflow CHECK (
        workflow_type IN ('BUILDING_REPAIR', 'COMMUNITY_PUBLIC_REPAIR')
    ),
    CONSTRAINT chk_repair_acceptance_policy_status CHECK (
        status IN ('ACTIVE', 'SUPERSEDED')
    ),
    CONSTRAINT chk_repair_acceptance_policy_shape CHECK (
        (
            workflow_type = 'BUILDING_REPAIR'
            AND affected_owner_count > 0
            AND minimum_affected_owner_participants BETWEEN 1 AND affected_owner_count
            AND minimum_affected_owner_approvals BETWEEN 1 AND minimum_affected_owner_participants
        ) OR (
            workflow_type = 'COMMUNITY_PUBLIC_REPAIR'
            AND affected_owner_count = 0
            AND minimum_affected_owner_participants IS NULL
            AND minimum_affected_owner_approvals IS NULL
        )
    )
);

CREATE UNIQUE INDEX uk_repair_acceptance_active_policy
    ON t_repair_acceptance_policy_snapshot(work_order_id)
    WHERE status = 'ACTIVE';

CREATE TABLE t_repair_acceptance_affected_owner (
    affected_owner_id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES t_repair_acceptance_policy_snapshot(policy_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    owner_uid BIGINT NOT NULL REFERENCES c_user(uid),
    affected_reason VARCHAR(500),
    CONSTRAINT uk_repair_acceptance_affected_room UNIQUE (policy_id, room_id)
);

CREATE INDEX idx_repair_acceptance_affected_owner
    ON t_repair_acceptance_affected_owner(policy_id, owner_uid);

CREATE TABLE t_repair_acceptance (
    acceptance_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL REFERENCES t_repair_acceptance_policy_snapshot(policy_id),
    round_no INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'COLLECTING',
    submitted_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_by_user_id BIGINT REFERENCES sys_user(user_id),
    completed_at TIMESTAMP,
    completion_remark VARCHAR(500),
    CONSTRAINT uk_repair_acceptance_round UNIQUE (work_order_id, round_no),
    CONSTRAINT chk_repair_acceptance_round_no CHECK (round_no > 0),
    CONSTRAINT chk_repair_acceptance_status CHECK (
        status IN ('COLLECTING', 'RECTIFICATION_REQUIRED', 'PASSED')
    )
);

CREATE UNIQUE INDEX uk_repair_acceptance_collecting
    ON t_repair_acceptance(work_order_id)
    WHERE status = 'COLLECTING';

CREATE TABLE t_repair_acceptance_party (
    party_id BIGSERIAL PRIMARY KEY,
    acceptance_id BIGINT NOT NULL REFERENCES t_repair_acceptance(acceptance_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    participant_key VARCHAR(160) NOT NULL,
    party_role VARCHAR(48) NOT NULL,
    room_id BIGINT,
    owner_uid BIGINT REFERENCES c_user(uid),
    participant_account_id BIGINT REFERENCES t_account(account_id),
    participant_user_id BIGINT REFERENCES sys_user(user_id),
    participant_name VARCHAR(120) NOT NULL,
    participant_organization VARCHAR(160),
    committee_position VARCHAR(32),
    conclusion VARCHAR(32) NOT NULL,
    opinion VARCHAR(1000),
    submission_method VARCHAR(32) NOT NULL,
    signature_hash VARCHAR(128),
    evidence_hash VARCHAR(128),
    seal_usage_id BIGINT REFERENCES t_committee_seal_usage(usage_id),
    submitted_by_user_id BIGINT REFERENCES sys_user(user_id),
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_acceptance_party_role CHECK (
        party_role IN (
            'AFFECTED_OWNER', 'BUILDING_LEADER', 'COMMITTEE_EXECUTIVE_APPROVER',
            'COMMITTEE_SEAL_OPERATOR', 'PROPERTY_TECHNICAL_COSIGNER',
            'THIRD_PARTY_TECHNICAL_COSIGNER'
        )
    ),
    CONSTRAINT chk_repair_acceptance_party_conclusion CHECK (
        conclusion IN ('PASSED', 'RECTIFICATION_REQUIRED')
    ),
    CONSTRAINT chk_repair_acceptance_submission_method CHECK (
        submission_method IN ('ONLINE_SELF', 'OFFLINE_RECORDED', 'SEAL_USAGE')
    ),
    CONSTRAINT chk_repair_acceptance_affected_owner_identity CHECK (
        party_role <> 'AFFECTED_OWNER' OR (room_id IS NOT NULL AND owner_uid IS NOT NULL)
    ),
    CONSTRAINT chk_repair_acceptance_seal_usage CHECK (
        party_role <> 'COMMITTEE_SEAL_OPERATOR' OR seal_usage_id IS NOT NULL
    )
);

CREATE INDEX idx_repair_acceptance_party_latest
    ON t_repair_acceptance_party(acceptance_id, participant_key, submitted_at DESC, party_id DESC);

ALTER TABLE t_repair_attachment
    DROP CONSTRAINT IF EXISTS chk_repair_attachment_kind;

ALTER TABLE t_repair_attachment
    ADD CONSTRAINT chk_repair_attachment_kind CHECK (
        attachment_kind IN (
            'OWNER_REPORT_IMAGE', 'INTAKE_ATTACHMENT', 'LOCATION_IMAGE', 'SURVEY_IMAGE', 'SURVEY_VIDEO',
            'QUOTE_DOCUMENT', 'APPROVAL_DOCUMENT', 'SOLITAIRE_SCREENSHOT',
            'GOVERNANCE_SEALED_DOCUMENT', 'ACCEPTANCE_SEALED_DOCUMENT'
        )
    );

COMMENT ON TABLE t_repair_acceptance_policy_snapshot IS
    '方案征询前冻结的验收权限与人数门槛，不提供平台默认值';
COMMENT ON TABLE t_repair_acceptance_affected_owner IS
    '楼栋维修实施方案锁定的受影响业主及房屋快照';
COMMENT ON TABLE t_repair_acceptance IS
    '维修完工验收轮次，整改复验新增轮次而不覆盖历史';
COMMENT ON TABLE t_repair_acceptance_party IS
    '楼组长、受影响业主、业委会、用印人与专业共同签署人的独立验收记录';
