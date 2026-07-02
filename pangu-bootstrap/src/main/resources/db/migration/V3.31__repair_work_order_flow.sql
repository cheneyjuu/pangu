-- V3.31: 三端报修工单闭环（业主小程序 / 管理台 / 工作端）。

CREATE SEQUENCE IF NOT EXISTS repair_work_order_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE t_repair_work_order (
    work_order_id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE DEFAULT (
        'RO-' || to_char(CURRENT_TIMESTAMP, 'YYYYMMDD') || '-' ||
        lpad(nextval('repair_work_order_no_seq')::TEXT, 6, '0')
    ),
    tenant_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT,
    source VARCHAR(32) NOT NULL,
    space_scope VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reporter_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    reporter_uid BIGINT REFERENCES c_user(uid),
    reporter_user_id BIGINT REFERENCES sys_user(user_id),
    room_id BIGINT,
    building_id BIGINT,
    location_text VARCHAR(200),
    need_manual_location SMALLINT NOT NULL DEFAULT 0,
    location_locked SMALLINT NOT NULL DEFAULT 0,
    assigned_user_id BIGINT REFERENCES sys_user(user_id),
    assignee_role_key VARCHAR(64),
    assignee_dept_id BIGINT REFERENCES sys_dept(dept_id),
    category VARCHAR(64),
    risk_level VARCHAR(32),
    survey_summary TEXT,
    plan_budget NUMERIC(14, 2),
    fund_source VARCHAR(64),
    fund_gate_blocked SMALLINT NOT NULL DEFAULT 1,
    satisfaction_score SMALLINT,
    satisfaction_comment VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_source CHECK (source IN ('C_OWNER_APP', 'ADMIN_PC', 'WORKER_APP')),
    CONSTRAINT chk_repair_scope CHECK (space_scope IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT chk_repair_status CHECK (status IN (
        'SUBMITTED', 'PENDING_VERIFY', 'NEED_MANUAL_LOCATION', 'VERIFIED', 'ASSIGNED',
        'SURVEYING', 'PLAN_SUBMITTED', 'GOVERNANCE_PENDING', 'APPROVED', 'IN_PROGRESS',
        'PENDING_ACCEPTANCE', 'RECTIFICATION_REQUIRED', 'COMPLETED', 'EVALUATED', 'ARCHIVED',
        'REJECTED', 'CANCELLED', 'SUSPENDED', 'ESCALATED', 'REASSIGN_REQUIRED',
        'PLAN_REVISION_REQUIRED', 'CHANGE_REVIEW_PENDING', 'PAYMENT_EXCEPTION', 'HANDOVER_LOCK'
    )),
    CONSTRAINT chk_repair_flags CHECK (
        need_manual_location IN (0, 1)
        AND location_locked IN (0, 1)
        AND fund_gate_blocked IN (0, 1)
    ),
    CONSTRAINT chk_repair_score CHECK (satisfaction_score IS NULL OR satisfaction_score BETWEEN 1 AND 5),
    CONSTRAINT chk_repair_private_room CHECK (
        space_scope <> 'PRIVATE' OR (reporter_uid IS NOT NULL AND room_id IS NOT NULL AND building_id IS NOT NULL)
    )
);

CREATE INDEX idx_repair_tenant_status ON t_repair_work_order(tenant_id, status, create_time DESC);
CREATE INDEX idx_repair_reporter ON t_repair_work_order(reporter_account_id, tenant_id, create_time DESC);
CREATE INDEX idx_repair_building ON t_repair_work_order(tenant_id, building_id, status);
CREATE INDEX idx_repair_assignee ON t_repair_work_order(assigned_user_id, status);

COMMENT ON TABLE t_repair_work_order IS '维修报修工单主表，承载业主端提交、物业/网格现场核验、方案预算、验收评价闭环';
COMMENT ON COLUMN t_repair_work_order.need_manual_location IS '1=位置不足，物业/网格需现场补充；不得退回业主补材料';
COMMENT ON COLUMN t_repair_work_order.location_locked IS '1=空间节点已核验锁定，资金/表决接口可读取该范围';
COMMENT ON COLUMN t_repair_work_order.fund_gate_blocked IS '1=资金闸门关闭，未核验前禁止预算、表决、资金链路';

CREATE TABLE t_repair_work_order_event (
    event_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    actor_account_id BIGINT,
    actor_identity_type VARCHAR(16),
    actor_identity_id BIGINT,
    remark VARCHAR(500),
    payload_json JSONB NOT NULL DEFAULT '{}'::JSONB,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_repair_event_order ON t_repair_work_order_event(work_order_id, create_time);
CREATE INDEX idx_repair_event_tenant ON t_repair_work_order_event(tenant_id, create_time DESC);

COMMENT ON TABLE t_repair_work_order_event IS '维修报修工单审计事件，记录提交、纠偏、核验、方案、验收、评价等动作留痕';

INSERT INTO sys_permission (permission_key, description, permission_group, allowed_dept_categories, is_legal_redline) VALUES
    ('repair:workorder:read',       '查看维修报修工单',          'REPAIR', 'GBS', 0),
    ('repair:workorder:manage',     '受理/派单/路径判定维修工单', 'REPAIR', 'GBS', 0),
    ('repair:workorder:field',      '现场核验/初勘/施工维修工单', 'REPAIR', 'GBS', 0),
    ('repair:workorder:governance', '治理审批/验收/归档维修工单', 'REPAIR', 'GB',  0)
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT r.role_id, p.permission_key
FROM sys_role r
JOIN (
    VALUES
        ('GOV_SUPER_ADMIN', 'repair:workorder:read'),
        ('GOV_SUPER_ADMIN', 'repair:workorder:manage'),
        ('GOV_SUPER_ADMIN', 'repair:workorder:field'),
        ('GOV_SUPER_ADMIN', 'repair:workorder:governance'),
        ('COMMUNITY_ADMIN', 'repair:workorder:read'),
        ('COMMUNITY_ADMIN', 'repair:workorder:manage'),
        ('COMMUNITY_ADMIN', 'repair:workorder:governance'),
        ('PARTY_SECRETARY', 'repair:workorder:read'),
        ('PARTY_SECRETARY', 'repair:workorder:governance'),
        ('GRID_MEMBER', 'repair:workorder:read'),
        ('GRID_MEMBER', 'repair:workorder:field'),
        ('COMMITTEE_DIRECTOR', 'repair:workorder:read'),
        ('COMMITTEE_DIRECTOR', 'repair:workorder:manage'),
        ('COMMITTEE_DIRECTOR', 'repair:workorder:governance'),
        ('COMMITTEE_MEMBER', 'repair:workorder:read'),
        ('COMMITTEE_MEMBER', 'repair:workorder:governance'),
        ('OWNER_REPRESENTATIVE', 'repair:workorder:read'),
        ('OWNER_REPRESENTATIVE', 'repair:workorder:field'),
        ('VOLUNTEER', 'repair:workorder:read'),
        ('VOLUNTEER', 'repair:workorder:field'),
        ('PROPERTY_MANAGER', 'repair:workorder:read'),
        ('PROPERTY_MANAGER', 'repair:workorder:manage'),
        ('PROPERTY_MANAGER', 'repair:workorder:field'),
        ('PROPERTY_STAFF', 'repair:workorder:read'),
        ('PROPERTY_STAFF', 'repair:workorder:field'),
        ('SERVICE_PROVIDER_MANAGER', 'repair:workorder:read'),
        ('SERVICE_PROVIDER_MANAGER', 'repair:workorder:field'),
        ('SERVICE_PROVIDER_STAFF', 'repair:workorder:read'),
        ('SERVICE_PROVIDER_STAFF', 'repair:workorder:field')
) AS p(role_key, permission_key) ON p.role_key = r.role_key
ON CONFLICT (role_id, permission_key) DO NOTHING;
