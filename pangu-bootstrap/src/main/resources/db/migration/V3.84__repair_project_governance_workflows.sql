-- 关联业务：为维修工程项目建立楼栋接龙治理流程、全小区业主大会事项关联和治理依据快照。

ALTER TABLE t_repair_local_decision
    ADD COLUMN project_id BIGINT REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    ADD COLUMN plan_id BIGINT REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE;

ALTER TABLE t_repair_local_decision
    ALTER COLUMN work_order_id DROP NOT NULL;

ALTER TABLE t_repair_local_decision
    ADD CONSTRAINT chk_repair_local_decision_business_owner CHECK (
        (work_order_id IS NOT NULL AND project_id IS NULL AND plan_id IS NULL)
        OR (work_order_id IS NULL AND project_id IS NOT NULL AND plan_id IS NOT NULL)
    );

CREATE INDEX idx_repair_local_decision_project_round
    ON t_repair_local_decision(project_id, plan_id, decision_id DESC)
    WHERE project_id IS NOT NULL;

CREATE TABLE t_repair_decision_policy_snapshot (
    policy_snapshot_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    rule_document_attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    rule_version VARCHAR(64) NOT NULL,
    rule_hash CHAR(64) NOT NULL,
    decision_channel VARCHAR(16) NOT NULL,
    delivery_rule VARCHAR(1000) NOT NULL,
    non_response_rule VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'LOCKED',
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_decision_policy_plan UNIQUE (plan_id),
    CONSTRAINT chk_repair_decision_policy_channel CHECK (decision_channel IN ('WECHAT', 'ONLINE')),
    CONSTRAINT chk_repair_decision_policy_non_response CHECK (
        non_response_rule IN ('NOT_PARTICIPATED', 'FOLLOW_MAJORITY', 'ABSTAIN')
    ),
    CONSTRAINT chk_repair_decision_policy_status CHECK (status IN ('LOCKED', 'SUPERSEDED'))
);

CREATE TABLE t_repair_building_process (
    process_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    policy_snapshot_id BIGINT NOT NULL REFERENCES t_repair_decision_policy_snapshot(policy_snapshot_id),
    decision_id BIGINT NOT NULL REFERENCES t_repair_local_decision(decision_id),
    status VARCHAR(40) NOT NULL DEFAULT 'DECISION_COLLECTING',
    official_document_attachment_id BIGINT REFERENCES t_repair_project_attachment(attachment_id),
    review_mode VARCHAR(32),
    reviewed_amount NUMERIC(14, 2),
    price_review_report_attachment_id BIGINT REFERENCES t_repair_project_attachment(attachment_id),
    price_review_conclusion VARCHAR(24),
    price_review_opinion VARCHAR(1000),
    price_reviewed_by_user_id BIGINT REFERENCES sys_user(user_id),
    price_reviewed_at TIMESTAMP,
    approved_by_user_id BIGINT REFERENCES sys_user(user_id),
    approver_position VARCHAR(32),
    approval_opinion VARCHAR(1000),
    approved_at TIMESTAMP,
    seal_usage_id BIGINT REFERENCES t_committee_seal_usage(usage_id),
    process_version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_building_process_plan UNIQUE (plan_id),
    CONSTRAINT chk_repair_building_process_status CHECK (
        status IN (
            'DECISION_COLLECTING', 'DECISION_FAILED', 'DECISION_PASSED',
            'OFFICIAL_DOCUMENT_READY', 'PRICE_REVIEWED', 'PRICE_REVIEW_REJECTED',
            'COMMITTEE_APPROVED', 'AUTHORIZED'
        )
    ),
    CONSTRAINT chk_repair_building_review_mode CHECK (
        review_mode IS NULL OR review_mode IN ('INTERNAL_PRICE_REVIEW', 'THIRD_PARTY_AUDIT', 'NOT_REQUIRED')
    ),
    CONSTRAINT chk_repair_building_review_conclusion CHECK (
        price_review_conclusion IS NULL OR price_review_conclusion IN ('APPROVED', 'REJECTED')
    ),
    CONSTRAINT chk_repair_building_approver_position CHECK (
        approver_position IS NULL OR approver_position IN ('DIRECTOR', 'VICE_DIRECTOR')
    ),
    CONSTRAINT chk_repair_building_review_amount CHECK (
        reviewed_amount IS NULL OR reviewed_amount > 0
    )
);

CREATE INDEX idx_repair_building_process_project
    ON t_repair_building_process(project_id, process_id DESC);

CREATE TABLE t_repair_assembly_subject_link (
    link_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    session_id BIGINT NOT NULL REFERENCES t_owners_assembly_session(session_id),
    package_id BIGINT NOT NULL REFERENCES t_owners_assembly_package(package_id),
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id),
    status VARCHAR(24) NOT NULL DEFAULT 'LINKED',
    result VARCHAR(16),
    linked_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    settled_by_user_id BIGINT REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_assembly_project_plan UNIQUE (plan_id),
    CONSTRAINT uk_repair_assembly_project_subject UNIQUE (project_id, subject_id),
    CONSTRAINT chk_repair_assembly_link_status CHECK (status IN ('LINKED', 'SETTLED')),
    CONSTRAINT chk_repair_assembly_link_result CHECK (result IS NULL OR result IN ('PASSED', 'FAILED'))
);

CREATE INDEX idx_repair_assembly_link_package
    ON t_repair_assembly_subject_link(package_id, subject_id);

CREATE TABLE t_repair_governance_basis (
    basis_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    basis_type VARCHAR(40) NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id BIGINT NOT NULL,
    snapshot_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_governance_basis_plan UNIQUE (plan_id),
    CONSTRAINT chk_repair_governance_basis_type CHECK (
        basis_type IN ('BUILDING_REPAIR_DECISION', 'COMMUNITY_ASSEMBLY_DECISION')
    ),
    CONSTRAINT chk_repair_governance_basis_reference CHECK (
        reference_type IN ('BUILDING_PROCESS', 'ASSEMBLY_SUBJECT')
    ),
    CONSTRAINT chk_repair_governance_basis_status CHECK (status IN ('ACTIVE', 'SUPERSEDED'))
);

ALTER TABLE t_committee_seal_usage
    ALTER COLUMN sealed_attachment_id DROP NOT NULL,
    ADD COLUMN project_source_attachment_id BIGINT REFERENCES t_repair_project_attachment(attachment_id),
    ADD COLUMN project_sealed_attachment_id BIGINT REFERENCES t_repair_project_attachment(attachment_id);

ALTER TABLE t_committee_seal_usage
    ADD CONSTRAINT chk_committee_seal_usage_attachment_owner CHECK (
        (
            sealed_attachment_id IS NOT NULL
            AND project_source_attachment_id IS NULL
            AND project_sealed_attachment_id IS NULL
        ) OR (
            sealed_attachment_id IS NULL
            AND source_attachment_id IS NULL
            AND project_sealed_attachment_id IS NOT NULL
        )
    );

COMMENT ON TABLE t_repair_decision_policy_snapshot IS '楼栋维修征询使用的备案议事规则、送达和未表态处理规则不可变快照';
COMMENT ON TABLE t_repair_building_process IS '楼栋/单元维修独立治理流程，不与全小区业主大会流程混用';
COMMENT ON TABLE t_repair_assembly_subject_link IS '全小区维修项目与正式业主大会会议、表决包及单个表决事项的关联';
COMMENT ON TABLE t_repair_governance_basis IS '维修工程获授权后固化的治理依据快照';
COMMENT ON COLUMN t_committee_seal_usage.project_sealed_attachment_id IS '维修工程项目级盖章文件；与旧工单附件字段二选一';
