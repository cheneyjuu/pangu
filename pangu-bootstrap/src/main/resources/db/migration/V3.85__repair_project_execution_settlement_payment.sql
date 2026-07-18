-- 关联业务：把合同、施工取证、材料进场、项目验收、实际结算、付款和完工披露接到维修工程项目。

CREATE TABLE t_repair_project_cost_review (
    review_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    review_mode VARCHAR(32) NOT NULL,
    reviewed_amount NUMERIC(14, 2) NOT NULL,
    report_attachment_id BIGINT REFERENCES t_repair_project_attachment(attachment_id),
    reviewed_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_project_cost_review_plan UNIQUE (plan_id),
    CONSTRAINT chk_repair_project_cost_review_mode CHECK (
        review_mode IN ('INTERNAL_PRICE_REVIEW', 'THIRD_PARTY_AUDIT', 'NOT_REQUIRED')
    ),
    CONSTRAINT chk_repair_project_cost_review_amount CHECK (reviewed_amount > 0)
);

ALTER TABLE t_repair_project_event
    ALTER COLUMN actor_user_id DROP NOT NULL,
    ADD COLUMN actor_owner_uid BIGINT REFERENCES c_user(uid),
    ADD CONSTRAINT chk_repair_project_event_actor_identity CHECK (
        (actor_user_id IS NOT NULL AND actor_owner_uid IS NULL)
        OR (actor_user_id IS NULL AND actor_owner_uid IS NOT NULL)
    );

ALTER TABLE t_repair_contract
    ADD COLUMN project_id BIGINT REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    ADD COLUMN plan_id BIGINT REFERENCES t_repair_plan_version(plan_id),
    ADD COLUMN contract_attachment_id BIGINT REFERENCES t_repair_project_attachment(attachment_id),
    ALTER COLUMN work_order_id DROP NOT NULL;

ALTER TABLE t_repair_contract
    ADD CONSTRAINT chk_repair_contract_business_owner CHECK (
        (work_order_id IS NOT NULL AND project_id IS NULL AND plan_id IS NULL)
        OR (work_order_id IS NULL AND project_id IS NOT NULL AND plan_id IS NOT NULL)
    );

CREATE UNIQUE INDEX uk_repair_contract_project_active
    ON t_repair_contract(project_id)
    WHERE project_id IS NOT NULL AND status <> 'VOIDED';

ALTER TABLE t_repair_contract_signature
    ADD COLUMN signature_attachment_id BIGINT REFERENCES t_repair_project_attachment(attachment_id);

CREATE TABLE t_repair_execution_record (
    record_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id),
    item_id BIGINT NOT NULL REFERENCES t_repair_project_item(item_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    stage VARCHAR(32) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    submitted_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    verification_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    verified_by_user_id BIGINT REFERENCES sys_user(user_id),
    verification_opinion VARCHAR(1000),
    verified_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_execution_stage CHECK (
        stage IN ('BEFORE_CONSTRUCTION', 'MATERIAL_ENTRY', 'DURING_CONSTRUCTION',
                  'CONCEALED_WORK', 'COMPLETION', 'ACCEPTANCE')
    ),
    CONSTRAINT chk_repair_execution_verification CHECK (
        verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')
    ),
    CONSTRAINT chk_repair_execution_verifier CHECK (
        (verification_status = 'PENDING' AND verified_by_user_id IS NULL AND verified_at IS NULL)
        OR (verification_status <> 'PENDING' AND verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL)
    )
);

CREATE INDEX idx_repair_execution_project_stage
    ON t_repair_execution_record(project_id, stage, record_id);

CREATE TABLE t_repair_execution_attachment (
    record_id BIGINT NOT NULL REFERENCES t_repair_execution_record(record_id) ON DELETE CASCADE,
    attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    sort_order INT NOT NULL,
    PRIMARY KEY (record_id, attachment_id),
    CONSTRAINT chk_repair_execution_attachment_order CHECK (sort_order > 0)
);

CREATE TABLE t_repair_material_inspection (
    inspection_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id),
    item_id BIGINT NOT NULL REFERENCES t_repair_project_item(item_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    material_name VARCHAR(160) NOT NULL,
    brand VARCHAR(160) NOT NULL,
    model VARCHAR(160) NOT NULL,
    specification VARCHAR(240) NOT NULL,
    quantity NUMERIC(14, 3) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    manufacturer VARCHAR(200) NOT NULL,
    qualification_attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    submitted_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    verified_by_user_id BIGINT REFERENCES sys_user(user_id),
    verification_opinion VARCHAR(1000),
    verified_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_material_quantity CHECK (quantity > 0),
    CONSTRAINT chk_repair_material_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT chk_repair_material_verifier CHECK (
        (status = 'PENDING' AND verified_by_user_id IS NULL AND verified_at IS NULL)
        OR (status <> 'PENDING' AND verified_by_user_id IS NOT NULL AND verified_at IS NOT NULL)
    )
);

CREATE INDEX idx_repair_material_project
    ON t_repair_material_inspection(project_id, inspection_id);

CREATE TABLE t_repair_material_photo (
    inspection_id BIGINT NOT NULL REFERENCES t_repair_material_inspection(inspection_id) ON DELETE CASCADE,
    attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    sort_order INT NOT NULL,
    PRIMARY KEY (inspection_id, attachment_id),
    CONSTRAINT chk_repair_material_photo_order CHECK (sort_order > 0)
);

CREATE TABLE t_repair_project_settlement (
    settlement_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id),
    contract_id BIGINT NOT NULL REFERENCES t_repair_contract(contract_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    version_no INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    subtotal_amount NUMERIC(14, 2) NOT NULL,
    tax_amount NUMERIC(14, 2) NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL,
    settlement_attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    submitted_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    verified_by_user_id BIGINT REFERENCES sys_user(user_id),
    verification_opinion VARCHAR(1000),
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP,
    CONSTRAINT uk_repair_project_settlement_version UNIQUE (project_id, version_no),
    CONSTRAINT chk_repair_project_settlement_status CHECK (status IN ('SUBMITTED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT chk_repair_project_settlement_amount CHECK (
        subtotal_amount >= 0 AND tax_amount >= 0 AND total_amount = subtotal_amount + tax_amount
    )
);

CREATE UNIQUE INDEX uk_repair_project_settlement_active
    ON t_repair_project_settlement(project_id)
    WHERE status IN ('SUBMITTED', 'VERIFIED');

CREATE TABLE t_repair_project_settlement_item (
    settlement_item_id BIGSERIAL PRIMARY KEY,
    settlement_id BIGINT NOT NULL REFERENCES t_repair_project_settlement(settlement_id) ON DELETE CASCADE,
    project_item_id BIGINT NOT NULL REFERENCES t_repair_project_item(item_id),
    actual_quantity NUMERIC(14, 3) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    actual_unit_price NUMERIC(14, 2) NOT NULL,
    amount_excluding_tax NUMERIC(14, 2) NOT NULL,
    tax_rate NUMERIC(7, 6) NOT NULL,
    tax_amount NUMERIC(14, 2) NOT NULL,
    amount_including_tax NUMERIC(14, 2) NOT NULL,
    variance_reason VARCHAR(1000),
    CONSTRAINT uk_repair_project_settlement_item UNIQUE (settlement_id, project_item_id),
    CONSTRAINT chk_repair_settlement_item_quantity CHECK (actual_quantity >= 0),
    CONSTRAINT chk_repair_settlement_item_price CHECK (actual_unit_price >= 0),
    CONSTRAINT chk_repair_settlement_item_tax_rate CHECK (tax_rate BETWEEN 0 AND 1),
    CONSTRAINT chk_repair_settlement_item_total CHECK (
        amount_including_tax = amount_excluding_tax + tax_amount
    )
);

ALTER TABLE t_repair_acceptance_policy_snapshot
    ADD COLUMN project_id BIGINT REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    ADD COLUMN plan_id BIGINT REFERENCES t_repair_plan_version(plan_id),
    ADD COLUMN affected_owner_pass_rule VARCHAR(24),
    ADD COLUMN affected_owner_approval_ratio NUMERIC(7, 6),
    ALTER COLUMN work_order_id DROP NOT NULL;

ALTER TABLE t_repair_acceptance_policy_snapshot
    ADD CONSTRAINT chk_repair_acceptance_policy_business_owner CHECK (
        (work_order_id IS NOT NULL AND project_id IS NULL AND plan_id IS NULL)
        OR (work_order_id IS NULL AND project_id IS NOT NULL AND plan_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_repair_project_acceptance_rule CHECK (
        project_id IS NULL OR (
            (workflow_type = 'BUILDING_REPAIR'
             AND affected_owner_pass_rule IN ('ALL', 'AT_LEAST_RATIO')
             AND affected_owner_approval_ratio > 0
             AND affected_owner_approval_ratio <= 1)
            OR
            (workflow_type = 'COMMUNITY_PUBLIC_REPAIR'
             AND affected_owner_pass_rule IS NULL
             AND affected_owner_approval_ratio IS NULL)
        )
    );

CREATE UNIQUE INDEX uk_repair_project_acceptance_policy_version
    ON t_repair_acceptance_policy_snapshot(project_id, version)
    WHERE project_id IS NOT NULL;

CREATE UNIQUE INDEX uk_repair_project_acceptance_active_policy
    ON t_repair_acceptance_policy_snapshot(project_id)
    WHERE project_id IS NOT NULL AND status = 'ACTIVE';

ALTER TABLE t_repair_acceptance
    ADD COLUMN project_id BIGINT REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    ADD COLUMN settlement_id BIGINT REFERENCES t_repair_project_settlement(settlement_id),
    ADD COLUMN result_project_attachment_id BIGINT REFERENCES t_repair_project_attachment(attachment_id),
    ALTER COLUMN work_order_id DROP NOT NULL;

ALTER TABLE t_repair_acceptance
    ADD CONSTRAINT chk_repair_acceptance_business_owner CHECK (
        (work_order_id IS NOT NULL AND project_id IS NULL AND settlement_id IS NULL)
        OR (work_order_id IS NULL AND project_id IS NOT NULL AND settlement_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_repair_project_acceptance_result CHECK (
        project_id IS NULL
        OR (status = 'COLLECTING' AND result_project_attachment_id IS NULL)
        OR (status <> 'COLLECTING' AND result_project_attachment_id IS NOT NULL)
    );

CREATE UNIQUE INDEX uk_repair_project_acceptance_round
    ON t_repair_acceptance(project_id, round_no)
    WHERE project_id IS NOT NULL;

CREATE UNIQUE INDEX uk_repair_project_acceptance_collecting
    ON t_repair_acceptance(project_id)
    WHERE project_id IS NOT NULL AND status = 'COLLECTING';

ALTER TABLE t_repair_acceptance_party
    ADD COLUMN evidence_project_attachment_id BIGINT REFERENCES t_repair_project_attachment(attachment_id);

ALTER TABLE t_repair_payment_request
    ADD COLUMN project_id BIGINT REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    ADD COLUMN cumulative_requested_amount NUMERIC(14, 2),
    ADD COLUMN eligible_upper_limit NUMERIC(14, 2),
    ADD COLUMN eligibility_result JSONB,
    ALTER COLUMN work_order_id DROP NOT NULL,
    ALTER COLUMN condition_evidence_hash DROP NOT NULL;

ALTER TABLE t_repair_payment_request DROP CONSTRAINT chk_repair_payment_milestone;
ALTER TABLE t_repair_payment_request
    ADD CONSTRAINT chk_repair_payment_milestone CHECK (
        milestone_type IN ('ADVANCE', 'PROGRESS', 'ACCEPTANCE', 'WARRANTY', 'COMPLETION', 'WARRANTY_RELEASE')
    ),
    ADD CONSTRAINT chk_repair_payment_business_owner CHECK (
        (work_order_id IS NOT NULL AND project_id IS NULL)
        OR (work_order_id IS NULL AND project_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_repair_project_payment_snapshot CHECK (
        project_id IS NULL OR (
            cumulative_requested_amount IS NOT NULL
            AND eligible_upper_limit IS NOT NULL
            AND eligibility_result IS NOT NULL
        )
    );

CREATE INDEX idx_repair_payment_project
    ON t_repair_payment_request(project_id, payment_request_id)
    WHERE project_id IS NOT NULL;

CREATE TABLE t_repair_payment_evidence (
    payment_request_id BIGINT NOT NULL REFERENCES t_repair_payment_request(payment_request_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    evidence_code VARCHAR(64) NOT NULL,
    attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    PRIMARY KEY (payment_request_id, evidence_code)
);

CREATE TABLE t_repair_completion_disclosure (
    disclosure_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    notice_start_date DATE NOT NULL,
    notice_end_date DATE NOT NULL,
    posting_scope VARCHAR(500) NOT NULL,
    notice_attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    property_report_attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    warranty_start_date DATE NOT NULL,
    warranty_end_date DATE NOT NULL,
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_completion_notice_dates CHECK (notice_end_date >= notice_start_date),
    CONSTRAINT chk_repair_completion_warranty_dates CHECK (warranty_end_date >= warranty_start_date)
);

CREATE TABLE t_repair_completion_disclosure_photo (
    disclosure_id BIGINT NOT NULL REFERENCES t_repair_completion_disclosure(disclosure_id) ON DELETE CASCADE,
    attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    sort_order INT NOT NULL,
    PRIMARY KEY (disclosure_id, attachment_id),
    CONSTRAINT chk_repair_completion_photo_order CHECK (sort_order > 0)
);

COMMENT ON TABLE t_repair_execution_record IS '按工程项和阶段保存施工前、施工中、隐蔽工程、完工与验收过程证据';
COMMENT ON TABLE t_repair_material_inspection IS '材料品牌、型号、规格、数量、合格证明和物业核验事实';
COMMENT ON TABLE t_repair_project_settlement IS '施工单位提交、物业核验的项目级结构化竣工结算';
COMMENT ON TABLE t_repair_payment_evidence IS '维修付款申请的结构化必需材料，不再用单个条件哈希代替';
COMMENT ON TABLE t_repair_completion_disclosure IS '完工告示、物业书面维修报告和质保期限归档';
