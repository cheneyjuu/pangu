-- 关联业务：将供应商遴选依据与最终定商理由分离，并在方案层固化楼栋维修受影响业主名单。

ALTER TABLE t_repair_plan_version
    ALTER COLUMN supplier_selection_reason DROP NOT NULL;

CREATE TABLE t_repair_plan_affected_owner (
    plan_affected_owner_id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    room_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    building_name VARCHAR(160) NOT NULL,
    unit_name VARCHAR(64),
    room_name VARCHAR(160) NOT NULL,
    owner_uid BIGINT NOT NULL REFERENCES c_user(uid),
    affected_reason VARCHAR(500) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_plan_affected_owner UNIQUE (plan_id, room_id),
    CONSTRAINT chk_repair_plan_affected_owner_source CHECK (
        source_type IN ('SYSTEM_RECOMMENDED', 'PROPERTY_ADJUSTED')
    )
);

CREATE INDEX idx_repair_plan_affected_owner_plan
    ON t_repair_plan_affected_owner(plan_id, tenant_id);

CREATE INDEX idx_repair_plan_affected_owner_identity
    ON t_repair_plan_affected_owner(tenant_id, owner_uid, room_id);

COMMENT ON COLUMN t_repair_plan_version.supplier_selection_reason IS
    '非竞争性遴选方式的采用依据；竞争性询价为空，最终中选供应商理由记录在定商结果中';
COMMENT ON TABLE t_repair_plan_affected_owner IS
    '楼栋维修实施方案固化的受影响业主名单；独立于费用承担房屋快照，可由物业基于系统推荐留痕调整';
COMMENT ON COLUMN t_repair_plan_affected_owner.source_type IS
    'SYSTEM_RECOMMENDED=系统按项目范围推荐；PROPERTY_ADJUSTED=物业说明原因后调整';
