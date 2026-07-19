-- 关联业务：维修工程责任初判只能由责任路径派生执行状态；共有维修必须另行取得相关业主决定，不能把附件或页面选择当作既有授权或紧急维修依据。

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM t_repair_project_responsibility_determination
        WHERE responsibility_path = 'SHARED_COMMON_REPAIR'
          AND execution_authority_type <> 'OWNER_DECISION'
    ) THEN
        RAISE EXCEPTION
            '历史共有维修责任认定含既有授权或紧急维修执行状态，必须先建立可信事实链路或重新认定，不能自动改写';
    END IF;
END $$;

ALTER TABLE t_repair_project_responsibility_determination
    DROP CONSTRAINT chk_repair_responsibility_determination_route,
    ADD CONSTRAINT chk_repair_responsibility_determination_route CHECK (
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
            AND execution_authority_type = 'OWNER_DECISION')
    );

COMMENT ON COLUMN t_repair_project_responsibility_determination.execution_authority_type IS
    '服务端由责任路径派生的执行状态；共有维修仅表示尚需取得相关业主决定，不能由附件或页面选择替代。';
