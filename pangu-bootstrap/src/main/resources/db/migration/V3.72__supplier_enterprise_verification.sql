-- 关联业务：供应商企业主体核验方式选择、租户级核验结论及不可变审计留痕。

CREATE TABLE t_supplier_enterprise_verification (
    verification_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    supplier_dept_id BIGINT NOT NULL REFERENCES t_supplier_org_profile(supplier_dept_id),
    legal_name_snapshot VARCHAR(120) NOT NULL,
    unified_social_credit_code_snapshot VARCHAR(18) NOT NULL,
    verification_method VARCHAR(32) NOT NULL,
    provider_code VARCHAR(64),
    source_code VARCHAR(64),
    provider_request_id VARCHAR(128),
    provider_result_code VARCHAR(64),
    verification_result VARCHAR(24) NOT NULL,
    business_status VARCHAR(64),
    result_message VARCHAR(500),
    inconsistent_fields_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    evidence_reference VARCHAR(500),
    remark VARCHAR(500),
    operator_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    operator_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    operator_role_key VARCHAR(50) NOT NULL,
    simulated SMALLINT NOT NULL DEFAULT 0,
    verified_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_supplier_enterprise_verification_method CHECK (
        verification_method IN ('PROPERTY_MANUAL', 'PLATFORM_API')
    ),
    CONSTRAINT chk_supplier_enterprise_verification_result CHECK (
        verification_result IN ('PASSED', 'REJECTED', 'ERROR')
    ),
    CONSTRAINT chk_supplier_enterprise_verification_simulated CHECK (simulated IN (0, 1)),
    CONSTRAINT chk_supplier_enterprise_verification_channel CHECK (
        (verification_method = 'PROPERTY_MANUAL' AND source_code IS NOT NULL AND provider_code IS NULL)
        OR
        (verification_method = 'PLATFORM_API' AND provider_code IS NOT NULL AND source_code IS NULL)
    ),
    CONSTRAINT chk_supplier_enterprise_verification_inconsistent_fields CHECK (
        jsonb_typeof(inconsistent_fields_json) = 'array'
    )
);

CREATE INDEX idx_supplier_enterprise_verification_history
    ON t_supplier_enterprise_verification(tenant_id, supplier_dept_id, verified_at DESC, verification_id DESC);

COMMENT ON TABLE t_supplier_enterprise_verification IS
    '供应商企业主体核验不可变审计记录；物业手工核验与平台接口核验统一落表，模拟结果必须显式标记';
COMMENT ON COLUMN t_supplier_enterprise_verification.operator_account_id IS
    '执行核验的自然人账号 ID，用于跨工作身份审计追溯';
COMMENT ON COLUMN t_supplier_enterprise_verification.simulated IS
    '1=开发测试模拟平台结果，不代表真实企业主体核验结论且生产环境禁止使用';

ALTER TABLE t_supplier_tenant_relation
    ADD COLUMN enterprise_verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    ADD COLUMN current_enterprise_verification_id BIGINT REFERENCES t_supplier_enterprise_verification(verification_id),
    ADD COLUMN enterprise_verified_by_user_id BIGINT REFERENCES sys_user(user_id),
    ADD COLUMN enterprise_verified_at TIMESTAMP,
    ADD CONSTRAINT chk_supplier_tenant_enterprise_verification_status CHECK (
        enterprise_verification_status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'DISABLED')
    );

-- 历史核验状态只在迁移时复制到租户关系，避免升级后已核验供应商突然失去签约资格。
UPDATE t_supplier_tenant_relation relation
SET enterprise_verification_status = profile.verification_status,
    enterprise_verified_by_user_id = profile.verified_by_user_id,
    enterprise_verified_at = profile.verified_at
FROM t_supplier_org_profile profile
WHERE profile.supplier_dept_id = relation.supplier_dept_id;

COMMENT ON COLUMN t_supplier_tenant_relation.enterprise_verification_status IS
    '当前租户对该供应商的企业主体核验结论；物业手工结论不得自动扩散到其他租户';
COMMENT ON COLUMN t_supplier_tenant_relation.current_enterprise_verification_id IS
    '当前生效核验记录；历史记录保留且不可覆盖';

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES
    ('repair:supplier:verify', '核验维修供应商企业主体并查看审计记录', 'REPAIR', 'S', 1)
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role.role_id, 'repair:supplier:verify'
FROM sys_role role
WHERE role.role_key = 'PROPERTY_MANAGER'
ON CONFLICT (role_id, permission_key) DO NOTHING;
