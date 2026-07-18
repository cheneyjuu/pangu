-- 关联业务：把报修事项与维修工程分离，并为楼栋维修和全小区维修建立不可变实施方案版本。

CREATE SEQUENCE repair_project_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE t_repair_project (
    project_id BIGSERIAL PRIMARY KEY,
    project_no VARCHAR(40) NOT NULL UNIQUE DEFAULT (
        'RP-' || to_char(CURRENT_TIMESTAMP, 'YYYYMMDD') || '-' ||
        lpad(nextval('repair_project_no_seq')::TEXT, 6, '0')
    ),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    project_name VARCHAR(160) NOT NULL,
    workflow_type VARCHAR(40) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    building_id BIGINT,
    unit_name VARCHAR(64),
    fund_source VARCHAR(64) NOT NULL,
    governance_path VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    active_plan_id BIGINT,
    version INT NOT NULL DEFAULT 0,
    created_by_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_project_workflow CHECK (
        workflow_type IN ('BUILDING_REPAIR', 'COMMUNITY_PUBLIC_REPAIR')
    ),
    CONSTRAINT chk_repair_project_scope CHECK (
        scope_type IN ('BUILDING', 'BUILDING_UNIT', 'COMMUNITY')
    ),
    CONSTRAINT chk_repair_project_status CHECK (
        status IN (
            'DRAFT', 'PLAN_LOCKED', 'GOVERNANCE_IN_PROGRESS', 'AUTHORIZED',
            'CONTRACT_EFFECTIVE', 'IN_PROGRESS', 'PENDING_ACCEPTANCE',
            'COMPLETED', 'WARRANTY', 'ARCHIVED', 'CANCELLED'
        )
    ),
    CONSTRAINT chk_repair_project_route CHECK (
        (
            workflow_type = 'BUILDING_REPAIR'
            AND scope_type IN ('BUILDING', 'BUILDING_UNIT')
            AND building_id IS NOT NULL
            AND (scope_type <> 'BUILDING_UNIT' OR unit_name IS NOT NULL)
            AND fund_source = 'BUILDING_MAINTENANCE_FUND'
            AND governance_path = 'BUILDING_REPAIR_DECISION'
        ) OR (
            workflow_type = 'COMMUNITY_PUBLIC_REPAIR'
            AND scope_type = 'COMMUNITY'
            AND building_id IS NULL
            AND unit_name IS NULL
            AND fund_source = 'COMMUNITY_MAINTENANCE_FUND'
            AND governance_path = 'COMMUNITY_ASSEMBLY_DECISION'
        )
    )
);

CREATE INDEX idx_repair_project_tenant_status
    ON t_repair_project(tenant_id, status, update_time DESC);
CREATE INDEX idx_repair_project_building
    ON t_repair_project(tenant_id, building_id, status)
    WHERE building_id IS NOT NULL;

CREATE TABLE t_repair_plan_version (
    plan_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    version_no INT NOT NULL,
    problem_cause TEXT NOT NULL,
    implementation_scope TEXT NOT NULL,
    budget_total NUMERIC(14, 2) NOT NULL,
    fund_source VARCHAR(64) NOT NULL,
    allocation_rule_type VARCHAR(40) NOT NULL,
    allocation_rule_description VARCHAR(1000),
    supplier_selection_method VARCHAR(40) NOT NULL,
    supplier_selection_reason VARCHAR(1000) NOT NULL,
    construction_management_requirements TEXT NOT NULL,
    evidence_requirements_json JSONB NOT NULL,
    safety_requirements TEXT NOT NULL,
    acceptance_method TEXT NOT NULL,
    required_acceptance_roles_json JSONB NOT NULL,
    affected_owner_scope_description VARCHAR(1000),
    minimum_affected_owner_acceptors INT,
    affected_owner_pass_rule VARCHAR(32),
    affected_owner_approval_ratio NUMERIC(5, 4),
    settlement_method VARCHAR(40) NOT NULL,
    planned_start_date DATE NOT NULL,
    planned_completion_date DATE NOT NULL,
    warranty_days INT NOT NULL,
    governance_path VARCHAR(64) NOT NULL,
    price_review_required SMALLINT NOT NULL,
    payment_milestones_json JSONB NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    snapshot_hash CHAR(64),
    created_by_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    locked_by_user_id BIGINT REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP,
    CONSTRAINT uk_repair_plan_version UNIQUE (project_id, version_no),
    CONSTRAINT chk_repair_plan_status CHECK (status IN ('DRAFT', 'LOCKED', 'SUPERSEDED')),
    CONSTRAINT chk_repair_plan_amount CHECK (budget_total > 0),
    CONSTRAINT chk_repair_plan_dates CHECK (planned_completion_date >= planned_start_date),
    CONSTRAINT chk_repair_plan_warranty CHECK (warranty_days >= 0),
    CONSTRAINT chk_repair_plan_flags CHECK (price_review_required IN (0, 1)),
    CONSTRAINT chk_repair_plan_acceptance_rule CHECK (
        (
            governance_path = 'BUILDING_REPAIR_DECISION'
            AND affected_owner_scope_description IS NOT NULL
            AND minimum_affected_owner_acceptors IS NOT NULL
            AND minimum_affected_owner_acceptors > 0
            AND affected_owner_pass_rule IN ('ALL', 'AT_LEAST_RATIO')
            AND affected_owner_approval_ratio IS NOT NULL
            AND affected_owner_approval_ratio > 0
            AND affected_owner_approval_ratio <= 1
        ) OR (
            governance_path = 'COMMUNITY_ASSEMBLY_DECISION'
            AND affected_owner_scope_description IS NULL
            AND minimum_affected_owner_acceptors IS NULL
            AND affected_owner_pass_rule IS NULL
            AND affected_owner_approval_ratio IS NULL
        )
    ),
    CONSTRAINT chk_repair_plan_json CHECK (
        jsonb_typeof(evidence_requirements_json) = 'array'
        AND jsonb_typeof(required_acceptance_roles_json) = 'array'
        AND jsonb_typeof(payment_milestones_json) = 'array'
    ),
    CONSTRAINT chk_repair_plan_lock_shape CHECK (
        (status = 'DRAFT' AND snapshot_hash IS NULL AND locked_by_user_id IS NULL AND locked_at IS NULL)
        OR (status IN ('LOCKED', 'SUPERSEDED') AND snapshot_hash IS NOT NULL
            AND locked_by_user_id IS NOT NULL AND locked_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_repair_plan_draft
    ON t_repair_plan_version(project_id)
    WHERE status = 'DRAFT';
CREATE INDEX idx_repair_plan_project
    ON t_repair_plan_version(project_id, version_no DESC);

ALTER TABLE t_repair_project
    ADD CONSTRAINT fk_repair_project_active_plan
    FOREIGN KEY (active_plan_id) REFERENCES t_repair_plan_version(plan_id);

CREATE TABLE t_repair_project_item (
    item_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    item_no VARCHAR(40) NOT NULL,
    building_id BIGINT,
    unit_name VARCHAR(64),
    room_id BIGINT,
    location_text VARCHAR(240) NOT NULL,
    work_content TEXT NOT NULL,
    quantity NUMERIC(14, 3) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    estimated_unit_price NUMERIC(14, 2) NOT NULL,
    estimated_amount NUMERIC(14, 2) NOT NULL,
    sort_order INT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_project_item_no UNIQUE (plan_id, item_no),
    CONSTRAINT chk_repair_project_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_repair_project_item_amount CHECK (
        estimated_unit_price >= 0 AND estimated_amount >= 0
    ),
    CONSTRAINT chk_repair_project_item_order CHECK (sort_order > 0)
);

CREATE INDEX idx_repair_project_item_plan
    ON t_repair_project_item(plan_id, sort_order, item_id);

CREATE TABLE t_repair_project_item_case (
    item_id BIGINT NOT NULL REFERENCES t_repair_project_item(item_id) ON DELETE CASCADE,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (item_id, work_order_id)
);

CREATE INDEX idx_repair_project_item_case_order
    ON t_repair_project_item_case(work_order_id, item_id);

CREATE TABLE t_repair_plan_allocation_room (
    allocation_room_id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    room_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    unit_name VARCHAR(64),
    owner_uid BIGINT NOT NULL REFERENCES c_user(uid),
    build_area NUMERIC(14, 2) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_plan_allocation_room UNIQUE (plan_id, room_id),
    CONSTRAINT chk_repair_plan_allocation_area CHECK (build_area > 0)
);

CREATE INDEX idx_repair_plan_allocation_owner
    ON t_repair_plan_allocation_room(plan_id, owner_uid);

CREATE TABLE t_repair_project_attachment (
    attachment_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    object_key VARCHAR(512) NOT NULL UNIQUE,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    etag VARCHAR(128) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    uploaded_by_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    uploaded_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_project_attachment_size CHECK (file_size > 0)
);

CREATE INDEX idx_repair_project_attachment_project
    ON t_repair_project_attachment(project_id, create_time DESC);

CREATE TABLE t_repair_plan_attachment (
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    purpose VARCHAR(40) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (plan_id, attachment_id, purpose),
    CONSTRAINT chk_repair_plan_attachment_purpose CHECK (
        purpose IN ('ORIGINAL_QUOTE', 'SITE_PHOTO', 'OFFICIAL_DOCUMENT', 'OTHER')
    ),
    CONSTRAINT chk_repair_plan_attachment_order CHECK (sort_order > 0)
);

CREATE TABLE t_repair_project_event (
    event_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    action VARCHAR(64) NOT NULL,
    actor_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    actor_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    payload_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_repair_project_event_project
    ON t_repair_project_event(project_id, event_id);

COMMENT ON TABLE t_repair_project IS '独立维修工程项目；报修事项只通过工程项关联，不再承载合同、施工和付款全链路';
COMMENT ON TABLE t_repair_plan_version IS '不可变实施方案版本；进入治理或签约前必须锁定快照哈希';
COMMENT ON TABLE t_repair_project_item IS '实施方案版本内的维修点位或工作清单项，支持一个项目包含多个点位';
COMMENT ON TABLE t_repair_project_item_case IS '工程项与报修事项多对多关联，支持归并、拆分及无报修计划性维修';
COMMENT ON TABLE t_repair_plan_allocation_room IS '实施方案锁定的费用承担房屋和面积分母快照';
COMMENT ON TABLE t_repair_project_attachment IS '项目级原始文件，不依赖虚构报修工单即可保存计划性维修材料';
COMMENT ON TABLE t_repair_plan_attachment IS '实施方案版本引用的报价、现场照片和正式文件';
COMMENT ON TABLE t_repair_project_event IS '维修工程项目审计事件';
