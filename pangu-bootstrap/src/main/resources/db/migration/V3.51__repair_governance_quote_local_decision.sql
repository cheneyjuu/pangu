-- V3.51: 报修供应商报价、单楼栋接龙、业委会确认与盖章。

ALTER TABLE t_repair_work_order DROP CONSTRAINT IF EXISTS chk_repair_status;

ALTER TABLE t_repair_work_order
    ADD CONSTRAINT chk_repair_status CHECK (status IN (
        'SUBMITTED', 'PENDING_VERIFY', 'NEED_MANUAL_LOCATION', 'VERIFIED', 'ASSIGNED',
        'SURVEYING', 'PLAN_SUBMITTED', 'QUOTE_COLLECTING', 'QUOTE_SUBMITTED',
        'SUPPLIER_RECOMMENDED', 'LOCAL_DECISION_PENDING', 'ASSEMBLY_DECISION_PENDING',
        'GOVERNANCE_PENDING', 'GOVERNANCE_CONFIRMED', 'SEALED', 'APPROVED', 'IN_PROGRESS',
        'PENDING_ACCEPTANCE', 'RECTIFICATION_REQUIRED', 'COMPLETED', 'EVALUATED', 'ARCHIVED',
        'REJECTED', 'CANCELLED', 'SUSPENDED', 'ESCALATED', 'REASSIGN_REQUIRED',
        'PLAN_REVISION_REQUIRED', 'CHANGE_REVIEW_PENDING', 'PAYMENT_EXCEPTION', 'HANDOVER_LOCK'
    ));

CREATE TABLE t_committee_member_position (
    position_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    position VARCHAR(32) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    appointed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_committee_member_position CHECK (position IN ('DIRECTOR', 'VICE_DIRECTOR', 'MEMBER')),
    CONSTRAINT chk_committee_member_position_status CHECK (status IN (1, 2))
);

CREATE UNIQUE INDEX uk_committee_member_position_active
    ON t_committee_member_position(tenant_id, user_id)
    WHERE status = 1;

COMMENT ON TABLE t_committee_member_position IS '业委会届期成员职务，副主任以职务属性表达，不新增静态 RBAC 角色';
COMMENT ON COLUMN t_committee_member_position.position IS 'DIRECTOR=主任，VICE_DIRECTOR=副主任，MEMBER=委员';

INSERT INTO t_committee_member_position (tenant_id, user_id, position, status)
SELECT d.tenant_id,
       u.user_id,
       CASE r.role_key WHEN 'COMMITTEE_DIRECTOR' THEN 'DIRECTOR' ELSE 'MEMBER' END,
       1
FROM sys_user u
JOIN sys_dept d ON d.dept_id = u.dept_id
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
WHERE r.role_key IN ('COMMITTEE_DIRECTOR', 'COMMITTEE_MEMBER')
  AND d.tenant_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE TABLE t_repair_supplier_quote (
    quote_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    supplier_name VARCHAR(120) NOT NULL,
    quote_amount NUMERIC(14, 2) NOT NULL,
    quote_summary TEXT,
    attachment_hash VARCHAR(128),
    submitted_by_user_id BIGINT REFERENCES sys_user(user_id),
    submitted_by_role_key VARCHAR(64),
    submitted_by_supplier SMALLINT NOT NULL DEFAULT 0,
    supplier_confirmed SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_quote_amount CHECK (quote_amount >= 0),
    CONSTRAINT chk_repair_quote_flags CHECK (
        submitted_by_supplier IN (0, 1)
        AND supplier_confirmed IN (0, 1)
    )
);

CREATE INDEX idx_repair_quote_order ON t_repair_supplier_quote(work_order_id, create_time);
CREATE INDEX idx_repair_quote_tenant ON t_repair_supplier_quote(tenant_id, create_time DESC);

COMMENT ON TABLE t_repair_supplier_quote IS '维修供应商报价，支持供应商自提或物业代录纸质/微信/PDF 报价';
COMMENT ON COLUMN t_repair_supplier_quote.attachment_hash IS '原始报价附件或确认材料哈希/对象标识';

CREATE TABLE t_repair_supplier_recommendation (
    recommendation_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    quote_id BIGINT NOT NULL REFERENCES t_repair_supplier_quote(quote_id),
    recommended_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    recommendation_reason VARCHAR(1000),
    single_source SMALLINT NOT NULL DEFAULT 0,
    single_source_reason VARCHAR(1000),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_recommendation_flags CHECK (single_source IN (0, 1)),
    CONSTRAINT chk_repair_single_source_reason CHECK (
        single_source = 0 OR single_source_reason IS NOT NULL
    )
);

CREATE INDEX idx_repair_recommendation_order
    ON t_repair_supplier_recommendation(work_order_id, create_time DESC);

COMMENT ON TABLE t_repair_supplier_recommendation IS '物业推荐供应商记录；推荐不等于最终生效，仍需业委会确认和盖章';

CREATE TABLE t_repair_local_decision (
    decision_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL UNIQUE REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    scope_label VARCHAR(120),
    total_owner_count INT NOT NULL,
    total_area NUMERIC(14, 2) NOT NULL,
    agree_owner_count INT,
    agree_area NUMERIC(14, 2),
    evidence_attachment_hash VARCHAR(128),
    printed_and_attached SMALLINT NOT NULL DEFAULT 0,
    result VARCHAR(32) NOT NULL DEFAULT 'COLLECTING',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_local_decision_totals CHECK (total_owner_count > 0 AND total_area > 0),
    CONSTRAINT chk_repair_local_decision_agree CHECK (
        agree_owner_count IS NULL OR (agree_owner_count >= 0 AND agree_owner_count <= total_owner_count)
    ),
    CONSTRAINT chk_repair_local_decision_area CHECK (
        agree_area IS NULL OR (agree_area >= 0 AND agree_area <= total_area)
    ),
    CONSTRAINT chk_repair_local_decision_printed CHECK (printed_and_attached IN (0, 1)),
    CONSTRAINT chk_repair_local_decision_result CHECK (result IN ('COLLECTING', 'PASSED', 'FAILED', 'DISPUTED'))
);

CREATE INDEX idx_repair_local_decision_tenant
    ON t_repair_local_decision(tenant_id, building_id, create_time DESC);

COMMENT ON TABLE t_repair_local_decision IS '单楼栋/一个单元号维修接龙决策，当前小区策略下作为正式表决';
COMMENT ON COLUMN t_repair_local_decision.evidence_attachment_hash IS '微信接龙截图/打印件材料哈希或对象标识';
COMMENT ON COLUMN t_repair_local_decision.printed_and_attached IS '1=微信截图已打印并附在物业报批文件后';

CREATE TABLE t_repair_governance_approval (
    approval_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    approver_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    approver_position VARCHAR(32) NOT NULL,
    opinion VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_governance_approver_position CHECK (approver_position IN ('DIRECTOR', 'VICE_DIRECTOR'))
);

CREATE INDEX idx_repair_governance_approval_order
    ON t_repair_governance_approval(work_order_id, create_time DESC);

COMMENT ON TABLE t_repair_governance_approval IS '业委会主任或副主任确认维修报批记录';

CREATE TABLE t_repair_governance_seal (
    seal_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    sealed_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    seal_type VARCHAR(32) NOT NULL,
    sealed_file_hash VARCHAR(128) NOT NULL,
    remark VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_repair_governance_seal_order
    ON t_repair_governance_seal(work_order_id, create_time DESC);

COMMENT ON TABLE t_repair_governance_seal IS '业委会盖章事件；与主任/副主任确认分开留痕';
