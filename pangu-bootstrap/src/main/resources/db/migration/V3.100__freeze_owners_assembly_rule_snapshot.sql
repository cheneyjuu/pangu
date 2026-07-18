-- 关联业务：在业主大会确认正式公示与表决安排时冻结已确认议事规则，禁止历史会次回读当前有效规则。

CREATE TABLE t_owners_assembly_rule_snapshot (
    rule_snapshot_id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL UNIQUE
        REFERENCES t_owners_assembly_session(session_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    rule_id BIGINT NOT NULL REFERENCES t_owners_assembly_rule(rule_id),
    rule_name VARCHAR(200) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    effective_date DATE NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    configuration_json JSONB NOT NULL,
    configuration_sha256 CHAR(64) NOT NULL,
    snapshotted_by_account_id BIGINT REFERENCES t_account(account_id),
    snapshotted_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_owners_assembly_rule_snapshot_tenant
    ON t_owners_assembly_rule_snapshot(tenant_id, rule_id, rule_snapshot_id DESC);

COMMENT ON TABLE t_owners_assembly_rule_snapshot IS
    '业主大会正式办理冻结的议事规则：规则原件标识和结构化配置与会次一一对应，不随 ACTIVE 规则变更';

ALTER TABLE t_owners_assembly_package
    ADD COLUMN IF NOT EXISTS rule_snapshot_id BIGINT
        REFERENCES t_owners_assembly_rule_snapshot(rule_snapshot_id);

CREATE INDEX IF NOT EXISTS idx_owners_assembly_package_rule_snapshot
    ON t_owners_assembly_package(rule_snapshot_id);

COMMENT ON COLUMN t_owners_assembly_package.rule_snapshot_id IS
    '本表决包使用的已冻结议事规则快照；历史空值不得按平台默认规则继续办理';

ALTER TABLE t_owners_assembly_package
    DROP CONSTRAINT IF EXISTS chk_owners_assembly_notice_days;

ALTER TABLE t_owners_assembly_package
    ADD CONSTRAINT chk_owners_assembly_notice_days CHECK (public_notice_days >= 0);
