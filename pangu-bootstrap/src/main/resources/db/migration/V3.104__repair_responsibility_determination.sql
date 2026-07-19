-- 关联业务：版本化保存维修工程责任、资金承担和执行依据；禁止由楼栋范围、前端选择或附件上传直接推导资金路径。

CREATE TABLE t_repair_project_responsibility_determination (
    determination_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    version_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    responsibility_path VARCHAR(64) NOT NULL,
    funding_source_type VARCHAR(64) NOT NULL,
    execution_authority_type VARCHAR(64) NOT NULL,
    basis_attachment_id BIGINT NOT NULL REFERENCES t_repair_project_attachment(attachment_id),
    basis_reference VARCHAR(1000) NOT NULL,
    responsible_party_name VARCHAR(200),
    responsible_party_reference VARCHAR(256),
    approved_amount NUMERIC(14, 2) NOT NULL,
    proposed_by_account_id BIGINT NOT NULL,
    proposed_by_user_id BIGINT NOT NULL,
    proposed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_by_account_id BIGINT,
    confirmed_by_user_id BIGINT,
    confirmed_at TIMESTAMP,
    confirmation_note VARCHAR(1000),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_responsibility_determination_version UNIQUE (project_id, version_no),
    CONSTRAINT chk_repair_responsibility_determination_status CHECK (
        status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'SUPERSEDED', 'REJECTED')
    ),
    CONSTRAINT chk_repair_responsibility_determination_route CHECK (
        (responsibility_path = 'PROPERTY_SERVICE_CONTRACT'
            AND funding_source_type = 'PROPERTY_SERVICE_CONTRACT'
            AND execution_authority_type = 'CONTRACTUAL_EXECUTION')
        OR (responsibility_path = 'DEVELOPER_WARRANTY'
            AND funding_source_type = 'DEVELOPER_WARRANTY'
            AND execution_authority_type = 'WARRANTY_EXECUTION')
        OR (responsibility_path = 'LIABLE_PARTY'
            AND funding_source_type = 'LIABLE_PARTY'
            AND execution_authority_type = 'LIABILITY_EXECUTION')
        OR (responsibility_path = 'SHARED_COMMON_REPAIR'
            AND funding_source_type IN (
                'SPECIAL_MAINTENANCE_LEDGER', 'PUBLIC_REVENUE_LEDGER', 'OWNER_SELF_FUNDING'
            )
            AND execution_authority_type IN (
                'OWNER_DECISION', 'EXISTING_AUTHORIZATION', 'EMERGENCY_REPAIR'
            ))
    ),
    CONSTRAINT chk_repair_responsibility_determination_amount CHECK (approved_amount > 0),
    CONSTRAINT chk_repair_responsibility_determination_confirmation CHECK (
        (status = 'PENDING_CONFIRMATION'
            AND confirmed_by_account_id IS NULL
            AND confirmed_by_user_id IS NULL
            AND confirmed_at IS NULL)
        OR (status = 'CONFIRMED'
            AND confirmed_by_account_id IS NOT NULL
            AND confirmed_by_user_id IS NOT NULL
            AND confirmed_at IS NOT NULL)
        OR status IN ('SUPERSEDED', 'REJECTED')
    )
);

-- 同一项目在任何时刻只能有一份待确认或已确认的当前认定；历史版本只读保留。
CREATE UNIQUE INDEX uk_repair_responsibility_determination_current
    ON t_repair_project_responsibility_determination(project_id)
    WHERE status IN ('PENDING_CONFIRMATION', 'CONFIRMED');

CREATE INDEX idx_repair_responsibility_determination_tenant
    ON t_repair_project_responsibility_determination(tenant_id, project_id, status, version_no DESC);

ALTER TABLE t_repair_funding_slice
    ADD COLUMN responsibility_determination_id BIGINT
        REFERENCES t_repair_project_responsibility_determination(determination_id),
    DROP CONSTRAINT chk_repair_funding_slice_source,
    ADD CONSTRAINT chk_repair_funding_slice_source CHECK (
        source_type IN (
            'SPECIAL_MAINTENANCE_LEDGER', 'PUBLIC_REVENUE_LEDGER', 'PROPERTY_SERVICE_CONTRACT',
            'LIABLE_PARTY', 'DEVELOPER_WARRANTY', 'OWNER_SELF_FUNDING'
        )
    );

CREATE INDEX idx_repair_funding_slice_determination
    ON t_repair_funding_slice(responsibility_determination_id)
    WHERE responsibility_determination_id IS NOT NULL;

COMMENT ON TABLE t_repair_project_responsibility_determination IS
    '本次工程责任、资金承担和执行依据的版本化事实；物业提出，有治理权限主体确认，历史不得覆盖。';
COMMENT ON COLUMN t_repair_project_responsibility_determination.basis_attachment_id IS
    '本项目已归档的责任、合同、保修、追偿、授权或紧急依据原件；附件本身不等于确认。';
COMMENT ON COLUMN t_repair_project_responsibility_determination.approved_amount IS
    '经确认的本次责任承担或资金使用上限，不是中选、合同、结算或付款金额。';
COMMENT ON COLUMN t_repair_funding_slice.responsibility_determination_id IS
    '生成本资金切片的已确认责任认定；历史切片为空，不据此推断新项目责任。';
COMMENT ON COLUMN t_repair_funding_slice.allocation_snapshot_hash IS
    '费用承担快照哈希：共有维修保存承担房屋/面积，直接责任路径保存确认责任方与依据快照。';
