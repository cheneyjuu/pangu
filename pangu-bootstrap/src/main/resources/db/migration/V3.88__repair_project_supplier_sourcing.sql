-- V3.88: 维修工程项目级邀价、报价版本和中选供应商快照。

CREATE TABLE t_repair_project_quote_invitation (
    invitation_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    supplier_dept_id BIGINT NOT NULL REFERENCES t_supplier_org_profile(supplier_dept_id),
    invited_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    deadline TIMESTAMP,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    invitation_round INTEGER NOT NULL DEFAULT 1,
    invitation_type VARCHAR(16) NOT NULL DEFAULT 'INITIAL',
    revision_reason VARCHAR(500),
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    CONSTRAINT uk_repair_project_quote_invitation_round
        UNIQUE (project_id, plan_id, supplier_dept_id, invitation_round),
    CONSTRAINT chk_repair_project_quote_invitation_status
        CHECK (status IN ('PENDING', 'SUBMITTED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_repair_project_quote_invitation_round CHECK (invitation_round > 0),
    CONSTRAINT chk_repair_project_quote_invitation_type
        CHECK (invitation_type IN ('INITIAL', 'REVISION')),
    CONSTRAINT chk_repair_project_quote_revision_reason
        CHECK (invitation_type = 'INITIAL' OR revision_reason IS NOT NULL)
);

CREATE INDEX idx_repair_project_quote_invitation_latest
    ON t_repair_project_quote_invitation(project_id, plan_id, supplier_dept_id, invitation_round DESC);

CREATE INDEX idx_repair_project_quote_invitation_supplier
    ON t_repair_project_quote_invitation(tenant_id, supplier_dept_id, status, sent_at DESC);

CREATE TABLE t_repair_project_supplier_quote (
    quote_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    supplier_dept_id BIGINT NOT NULL REFERENCES t_supplier_org_profile(supplier_dept_id),
    supplier_name VARCHAR(120) NOT NULL,
    quote_amount NUMERIC(14, 2) NOT NULL,
    quote_summary TEXT,
    attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    attachment_hash CHAR(64) NOT NULL,
    submitted_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    submitted_by_role_key VARCHAR(64) NOT NULL,
    submission_source VARCHAR(32) NOT NULL,
    confirmation_status VARCHAR(40) NOT NULL,
    original_source VARCHAR(32),
    quote_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    revision_no INTEGER NOT NULL DEFAULT 1,
    superseded_by_quote_id BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_project_quote_amount CHECK (quote_amount >= 0),
    CONSTRAINT chk_repair_project_quote_submission_source
        CHECK (submission_source IN ('SUPPLIER_ONLINE', 'PROPERTY_ENTRY')),
    CONSTRAINT chk_repair_project_quote_confirmation_status CHECK (
        confirmation_status IN (
            'PENDING_SUPPLIER_CONFIRMATION', 'ONLINE_CONFIRMED',
            'OFFLINE_EVIDENCE_VERIFIED', 'CONTRACT_CONFIRMED'
        )
    ),
    CONSTRAINT chk_repair_project_quote_status
        CHECK (quote_status IN ('ACTIVE', 'REVISION_REQUESTED', 'SUPERSEDED')),
    CONSTRAINT chk_repair_project_quote_revision_no CHECK (revision_no > 0)
);

ALTER TABLE t_repair_project_supplier_quote
    ADD CONSTRAINT fk_repair_project_quote_superseded_by
        FOREIGN KEY (superseded_by_quote_id) REFERENCES t_repair_project_supplier_quote(quote_id);

CREATE UNIQUE INDEX uk_repair_project_supplier_active_quote
    ON t_repair_project_supplier_quote(project_id, plan_id, supplier_dept_id)
    WHERE quote_status IN ('ACTIVE', 'REVISION_REQUESTED');

CREATE INDEX idx_repair_project_supplier_quote_history
    ON t_repair_project_supplier_quote(project_id, plan_id, supplier_dept_id, revision_no DESC);

CREATE TABLE t_repair_project_supplier_selection (
    selection_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    quote_id BIGINT NOT NULL REFERENCES t_repair_project_supplier_quote(quote_id),
    supplier_dept_id BIGINT NOT NULL REFERENCES t_supplier_org_profile(supplier_dept_id),
    supplier_name VARCHAR(120) NOT NULL,
    quote_amount NUMERIC(14, 2) NOT NULL,
    selection_method VARCHAR(40) NOT NULL,
    recommendation_reason VARCHAR(1000),
    insufficient_quote_reason VARCHAR(1000),
    framework_relation_id BIGINT REFERENCES t_supplier_tenant_relation(relation_id),
    recommended_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_project_selection_amount CHECK (quote_amount >= 0),
    CONSTRAINT chk_repair_project_selection_method CHECK (
        selection_method IN (
            'COMPETITIVE_QUOTATION', 'FRAMEWORK_SUPPLIER',
            'DIRECT_AWARD', 'EMERGENCY_APPOINTMENT'
        )
    )
);

CREATE INDEX idx_repair_project_supplier_selection_latest
    ON t_repair_project_supplier_selection(project_id, plan_id, create_time DESC, selection_id DESC);

COMMENT ON TABLE t_repair_project_quote_invitation IS
    '维修工程项目方案级邀价及修订轮次；与旧工单报价历史分离。';
COMMENT ON TABLE t_repair_project_supplier_quote IS
    '维修工程项目方案级供应商报价版本；原件使用项目附件权限边界。';
COMMENT ON TABLE t_repair_project_supplier_selection IS
    '物业从有效项目报价中形成的中选快照；锁定方案、治理披露和合同共同引用。';
