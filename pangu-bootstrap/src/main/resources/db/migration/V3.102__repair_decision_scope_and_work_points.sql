-- 关联业务：把维修建项从扁平工程项收敛为单一决定范围下的维修点位；历史工程项只读迁入，不参与新写入。

CREATE TABLE t_repair_project_decision_scope (
    decision_scope_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    scope_type VARCHAR(32) NOT NULL,
    building_id BIGINT,
    unit_name VARCHAR(64),
    verification_status VARCHAR(32) NOT NULL,
    verification_basis VARCHAR(1000),
    legacy_read_only BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_decision_scope_type CHECK (
        scope_type IN ('BUILDING', 'BUILDING_UNIT', 'COMMUNITY')
    ),
    CONSTRAINT chk_repair_decision_scope_verification CHECK (
        verification_status IN ('CONFIRMED', 'PENDING_VERIFICATION', 'LEGACY_READ_ONLY')
    ),
    CONSTRAINT chk_repair_decision_scope_shape CHECK (
        (scope_type = 'COMMUNITY' AND building_id IS NULL AND unit_name IS NULL)
        OR (scope_type = 'BUILDING' AND building_id IS NOT NULL AND unit_name IS NULL)
        OR (scope_type = 'BUILDING_UNIT' AND building_id IS NOT NULL AND unit_name IS NOT NULL)
    ),
    CONSTRAINT chk_repair_decision_scope_legacy CHECK (
        (legacy_read_only = TRUE AND verification_status = 'LEGACY_READ_ONLY')
        OR (legacy_read_only = FALSE AND verification_status IN ('CONFIRMED', 'PENDING_VERIFICATION'))
    )
);

CREATE TABLE t_repair_work_point (
    work_point_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    legacy_item_id BIGINT UNIQUE REFERENCES t_repair_project_item(item_id),
    business_name VARCHAR(160) NOT NULL,
    building_id BIGINT,
    unit_name VARCHAR(64),
    location_type VARCHAR(32) NOT NULL,
    reference_room_id BIGINT,
    common_area_name VARCHAR(160),
    space_name VARCHAR(160),
    orientation VARCHAR(80),
    component VARCHAR(160),
    specific_part VARCHAR(240),
    symptom TEXT NOT NULL,
    cause_status VARCHAR(32) NOT NULL,
    cause_basis TEXT,
    proposed_measure TEXT NOT NULL,
    technical_requirements TEXT,
    quantity NUMERIC(14, 3),
    unit VARCHAR(32),
    preliminary_estimated_amount NUMERIC(14, 2),
    estimate_source VARCHAR(500),
    sort_order INT NOT NULL,
    legacy_read_only BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_work_point_location CHECK (
        (location_type = 'REFERENCE_ROOM' AND reference_room_id IS NOT NULL AND common_area_name IS NULL)
        OR (location_type = 'COMMON_AREA' AND reference_room_id IS NULL AND common_area_name IS NOT NULL)
    ),
    CONSTRAINT chk_repair_work_point_cause CHECK (
        cause_status IN ('PENDING_INVESTIGATION', 'CONFIRMED', 'UNCONFIRMED')
        AND (cause_status <> 'CONFIRMED' OR cause_basis IS NOT NULL)
    ),
    CONSTRAINT chk_repair_work_point_quantity CHECK (
        (quantity IS NULL AND unit IS NULL) OR (quantity > 0 AND unit IS NOT NULL)
    ),
    CONSTRAINT chk_repair_work_point_estimate CHECK (
        (preliminary_estimated_amount IS NULL AND estimate_source IS NULL)
        OR (preliminary_estimated_amount >= 0 AND estimate_source IS NOT NULL)
    ),
    CONSTRAINT chk_repair_work_point_order CHECK (sort_order > 0)
);

CREATE INDEX idx_repair_work_point_plan
    ON t_repair_work_point(plan_id, sort_order, work_point_id);
CREATE INDEX idx_repair_work_point_project
    ON t_repair_work_point(project_id, tenant_id);

CREATE TABLE t_repair_work_point_source (
    work_point_id BIGINT NOT NULL REFERENCES t_repair_work_point(work_point_id) ON DELETE CASCADE,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id),
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (work_point_id, work_order_id)
);

CREATE INDEX idx_repair_work_point_source_order
    ON t_repair_work_point_source(work_order_id, work_point_id);

-- 旧项目未保存可核验的共有权利依据，迁入后明确为只读历史，不允许被新流程再次锁定或执行。
INSERT INTO t_repair_project_decision_scope (
    project_id, tenant_id, scope_type, building_id, unit_name,
    verification_status, verification_basis, legacy_read_only, create_time
)
SELECT project_id, tenant_id, scope_type, building_id, unit_name,
       'LEGACY_READ_ONLY', '由旧项目范围字段迁入；未补造共有权利、决定或勘验依据。', TRUE, create_time
FROM t_repair_project;

-- 旧工程项只保留为历史点位/初步估算记录；不把数量单价、原因或报价关系伪造成新事实。
INSERT INTO t_repair_work_point (
    project_id, plan_id, tenant_id, legacy_item_id, business_name, building_id, unit_name,
    location_type, reference_room_id, common_area_name, symptom, cause_status, proposed_measure,
    quantity, unit, preliminary_estimated_amount, estimate_source, sort_order, legacy_read_only, create_time
)
SELECT item.project_id, item.plan_id, item.tenant_id, item.item_id,
       item.location_text, item.building_id, item.unit_name,
       CASE WHEN item.room_id IS NULL THEN 'COMMON_AREA' ELSE 'REFERENCE_ROOM' END,
       item.room_id,
       CASE WHEN item.room_id IS NULL THEN item.location_text ELSE NULL END,
       '历史工程项未记录问题现象。', 'PENDING_INVESTIGATION', item.work_content,
       item.quantity, item.unit, item.estimated_amount, 'LEGACY_ITEM_MIGRATION',
       item.sort_order, TRUE, item.create_time
FROM t_repair_project_item item;

INSERT INTO t_repair_work_point_source (work_point_id, work_order_id, tenant_id, create_time)
SELECT work_point.work_point_id, legacy_source.work_order_id, legacy_source.tenant_id, legacy_source.create_time
FROM t_repair_project_item_case legacy_source
JOIN t_repair_work_point work_point ON work_point.legacy_item_id = legacy_source.item_id;

-- 后续报价、施工和结算只写 work_point_id；旧 item 外键保留为历史读取和审计证据。
ALTER TABLE t_repair_project_supplier_quote_line
    ADD COLUMN work_point_id BIGINT REFERENCES t_repair_work_point(work_point_id),
    ALTER COLUMN project_item_id DROP NOT NULL;

UPDATE t_repair_project_supplier_quote_line quote_line
SET work_point_id = work_point.work_point_id
FROM t_repair_work_point work_point
WHERE work_point.legacy_item_id = quote_line.project_item_id;

CREATE INDEX idx_repair_project_quote_line_work_point
    ON t_repair_project_supplier_quote_line(work_point_id, line_no)
    WHERE work_point_id IS NOT NULL;

ALTER TABLE t_repair_execution_record
    ADD COLUMN work_point_id BIGINT REFERENCES t_repair_work_point(work_point_id),
    ALTER COLUMN item_id DROP NOT NULL;

UPDATE t_repair_execution_record record
SET work_point_id = work_point.work_point_id
FROM t_repair_work_point work_point
WHERE work_point.legacy_item_id = record.item_id;

ALTER TABLE t_repair_material_inspection
    ADD COLUMN work_point_id BIGINT REFERENCES t_repair_work_point(work_point_id),
    ALTER COLUMN item_id DROP NOT NULL;

UPDATE t_repair_material_inspection inspection
SET work_point_id = work_point.work_point_id
FROM t_repair_work_point work_point
WHERE work_point.legacy_item_id = inspection.item_id;

ALTER TABLE t_repair_project_settlement_item
    ADD COLUMN work_point_id BIGINT REFERENCES t_repair_work_point(work_point_id),
    ALTER COLUMN project_item_id DROP NOT NULL;

UPDATE t_repair_project_settlement_item settlement_item
SET work_point_id = work_point.work_point_id
FROM t_repair_work_point work_point
WHERE work_point.legacy_item_id = settlement_item.project_item_id;

-- 范围只决定技术处理路径，不能自动推导专项维修资金、公共收益或治理程序。
-- 历史项目保留旧值，新草稿必须以空值表示尚未接入可信来源。
ALTER TABLE t_repair_project
    DROP CONSTRAINT chk_repair_project_route,
    ALTER COLUMN fund_source DROP NOT NULL,
    ALTER COLUMN governance_path DROP NOT NULL,
    ADD CONSTRAINT chk_repair_project_route CHECK (
        (
            workflow_type = 'BUILDING_REPAIR'
            AND scope_type IN ('BUILDING', 'BUILDING_UNIT')
            AND building_id IS NOT NULL
            AND (scope_type <> 'BUILDING_UNIT' OR unit_name IS NOT NULL)
            AND (
                (fund_source IS NULL AND governance_path IS NULL)
                OR (
                    fund_source = 'BUILDING_MAINTENANCE_FUND'
                    AND governance_path = 'BUILDING_REPAIR_DECISION'
                )
            )
        ) OR (
            workflow_type = 'COMMUNITY_PUBLIC_REPAIR'
            AND scope_type = 'COMMUNITY'
            AND building_id IS NULL
            AND unit_name IS NULL
            AND (
                (fund_source IS NULL AND governance_path IS NULL)
                OR (
                    fund_source = 'COMMUNITY_MAINTENANCE_FUND'
                    AND governance_path = 'COMMUNITY_ASSEMBLY_DECISION'
                )
            )
        )
    );

-- 建项草稿只写预算、点位和来源；其余字段仅保存历史或后续可信冻结快照。
ALTER TABLE t_repair_plan_version
    ALTER COLUMN fund_source DROP NOT NULL,
    ALTER COLUMN allocation_rule_type DROP NOT NULL,
    ALTER COLUMN supplier_selection_method DROP NOT NULL,
    ALTER COLUMN construction_management_requirements DROP NOT NULL,
    ALTER COLUMN evidence_requirements_json DROP NOT NULL,
    ALTER COLUMN safety_requirements DROP NOT NULL,
    ALTER COLUMN acceptance_method DROP NOT NULL,
    ALTER COLUMN required_acceptance_roles_json DROP NOT NULL,
    ALTER COLUMN settlement_method DROP NOT NULL,
    ALTER COLUMN planned_start_date DROP NOT NULL,
    ALTER COLUMN planned_completion_date DROP NOT NULL,
    ALTER COLUMN warranty_days DROP NOT NULL,
    ALTER COLUMN governance_path DROP NOT NULL,
    ALTER COLUMN price_review_required DROP NOT NULL,
    ALTER COLUMN payment_milestones_json DROP NOT NULL,
    DROP CONSTRAINT chk_repair_plan_acceptance_rule,
    DROP CONSTRAINT chk_repair_plan_json,
    ADD CONSTRAINT chk_repair_plan_acceptance_rule CHECK (
        (governance_path IS NULL
            AND affected_owner_scope_description IS NULL
            AND minimum_affected_owner_acceptors IS NULL
            AND affected_owner_pass_rule IS NULL
            AND affected_owner_approval_ratio IS NULL)
        OR (
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
    ADD CONSTRAINT chk_repair_plan_json CHECK (
        (evidence_requirements_json IS NULL OR jsonb_typeof(evidence_requirements_json) = 'array')
        AND (required_acceptance_roles_json IS NULL OR jsonb_typeof(required_acceptance_roles_json) = 'array')
        AND (payment_milestones_json IS NULL OR jsonb_typeof(payment_milestones_json) = 'array')
    );

-- 资金切片只能由可信账簿、责任认定或有效决定适配器写入；建项接口没有插入语句。
CREATE TABLE t_repair_funding_slice (
    funding_slice_id BIGSERIAL PRIMARY KEY,
    decision_scope_id BIGINT NOT NULL REFERENCES t_repair_project_decision_scope(decision_scope_id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    source_type VARCHAR(64) NOT NULL,
    source_record_type VARCHAR(64) NOT NULL,
    source_record_id VARCHAR(128) NOT NULL,
    ledger_reference VARCHAR(256) NOT NULL,
    allocation_snapshot_hash CHAR(64) NOT NULL,
    approved_amount NUMERIC(14, 2) NOT NULL,
    verification_status VARCHAR(32) NOT NULL,
    legacy_read_only BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_funding_slice_source UNIQUE (decision_scope_id, source_type, source_record_type, source_record_id),
    CONSTRAINT chk_repair_funding_slice_source CHECK (
        source_type IN (
            'SPECIAL_MAINTENANCE_LEDGER', 'PUBLIC_REVENUE_LEDGER', 'LIABLE_PARTY',
            'DEVELOPER_WARRANTY', 'OWNER_SELF_FUNDING'
        )
    ),
    CONSTRAINT chk_repair_funding_slice_amount CHECK (approved_amount >= 0),
    CONSTRAINT chk_repair_funding_slice_hash CHECK (allocation_snapshot_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_repair_funding_slice_verification CHECK (
        (legacy_read_only = TRUE AND verification_status = 'LEGACY_READ_ONLY')
        OR (
            legacy_read_only = FALSE
            AND verification_status IN ('PENDING_VERIFICATION', 'CONFIRMED')
            AND (verification_status <> 'CONFIRMED' OR verified_at IS NOT NULL)
        )
    )
);

CREATE INDEX idx_repair_funding_slice_scope
    ON t_repair_funding_slice(decision_scope_id, tenant_id, verification_status);

COMMENT ON TABLE t_repair_project_decision_scope IS
    '一个维修工程唯一的共有与决定范围；待核验范围只能保留草稿。';
COMMENT ON TABLE t_repair_work_point IS
    '可定位、可勘验的维修对象；不是报价行、结算行或数量乘单价的承载对象。';
COMMENT ON TABLE t_repair_work_point_source IS
    '维修点位与业主报修、物业巡检或勘验工单的来源关联。';
COMMENT ON TABLE t_repair_funding_slice IS
    '可信资金来源、账簿/责任记录、承担范围和金额快照；不可由维修建项表单直接声明。';
COMMENT ON COLUMN t_repair_work_point.preliminary_estimated_amount IS
    '建项阶段可选初步估算，不等同于报价、合同或结算金额。';

-- 结算税率、税额和含税总额与供应商报价一致，都是单据头事实；
-- 历史行级税额保留只读，新结算不再写入这些旧列。
ALTER TABLE t_repair_project_settlement
    ADD COLUMN tax_rate NUMERIC(7, 3),
    ADD CONSTRAINT chk_repair_project_settlement_tax_rate CHECK (
        tax_rate IS NULL OR (tax_rate >= 0 AND tax_rate <= 100)
    );

ALTER TABLE t_repair_project_settlement_item
    ALTER COLUMN tax_rate DROP NOT NULL,
    ALTER COLUMN tax_amount DROP NOT NULL,
    ALTER COLUMN amount_including_tax DROP NOT NULL;

COMMENT ON COLUMN t_repair_project_settlement.tax_rate IS
    '结算单头统一税率，百分数口径，例如 9 表示 9%。';
COMMENT ON COLUMN t_repair_project_settlement_item.work_point_id IS
    '可选维修点位引用；运输、清运等项目通用专业明细可以为空。';
