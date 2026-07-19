-- 关联业务：把维修授权提案唯一关联到统一表决事项、表决包和已启用议事规则版本。

CREATE TABLE t_repair_project_voting (
    link_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id),
    plan_id BIGINT NOT NULL REFERENCES t_repair_plan_version(plan_id),
    tenant_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL UNIQUE REFERENCES t_voting_subject(subject_id),
    execution_package_id BIGINT NOT NULL UNIQUE REFERENCES t_voting_execution_package(package_id),
    rule_id BIGINT NOT NULL REFERENCES t_owners_assembly_rule(rule_id),
    rule_configuration_hash CHAR(64) NOT NULL,
    collection_mode VARCHAR(48) NOT NULL,
    status VARCHAR(16) NOT NULL,
    result VARCHAR(16),
    prepared_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    prepared_at TIMESTAMP NOT NULL,
    opened_by_user_id BIGINT REFERENCES sys_user(user_id),
    opened_at TIMESTAMP,
    settled_by_user_id BIGINT REFERENCES sys_user(user_id),
    settled_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_project_voting_plan UNIQUE (project_id, plan_id),
    CONSTRAINT chk_repair_project_voting_mode CHECK (
        collection_mode IN ('PAPER', 'ONLINE_WITH_PAPER_ASSISTANCE', 'PAPER_AND_ONLINE')
    ),
    CONSTRAINT chk_repair_project_voting_status CHECK (
        status IN ('PREPARED', 'VOTING', 'SETTLED', 'VOIDED')
    ),
    CONSTRAINT chk_repair_project_voting_result CHECK (result IS NULL OR result IN ('PASSED', 'FAILED')),
    CONSTRAINT chk_repair_project_voting_lifecycle CHECK (
        (status = 'PREPARED' AND opened_at IS NULL AND settled_at IS NULL AND result IS NULL)
        OR (status = 'VOTING' AND opened_at IS NOT NULL AND settled_at IS NULL AND result IS NULL)
        OR (status = 'SETTLED' AND opened_at IS NOT NULL AND settled_at IS NOT NULL AND result IS NOT NULL)
        OR status = 'VOIDED'
    )
);

CREATE INDEX idx_repair_project_voting_tenant_status
    ON t_repair_project_voting(tenant_id, status, prepared_at);

COMMENT ON TABLE t_repair_project_voting IS
    '维修授权提案与统一表决包的唯一关联；不复制名册、选票或计票结果';
