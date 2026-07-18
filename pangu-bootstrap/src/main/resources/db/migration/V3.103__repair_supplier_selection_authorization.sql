-- 关联业务：将楼栋维修有效决定、用印授权和施工单位选择规则固化为可核验依据，禁止物业绕过授权直接定商。

ALTER TABLE t_repair_governance_basis
    ADD COLUMN approved_supplier_selection_method VARCHAR(40),
    ADD COLUMN approved_supplier_evaluation_rule VARCHAR(40),
    ADD COLUMN minimum_invited_supplier_count INTEGER,
    ADD COLUMN minimum_valid_quote_count INTEGER,
    ADD COLUMN non_competitive_selection_basis VARCHAR(1000);

ALTER TABLE t_repair_governance_basis
    ADD CONSTRAINT chk_repair_governance_basis_selection_method CHECK (
        approved_supplier_selection_method IS NULL
        OR approved_supplier_selection_method IN (
            'COMPETITIVE_QUOTATION', 'FRAMEWORK_SUPPLIER',
            'DIRECT_AWARD', 'EMERGENCY_APPOINTMENT'
        )
    ),
    ADD CONSTRAINT chk_repair_governance_basis_evaluation_rule CHECK (
        approved_supplier_evaluation_rule IS NULL
        OR approved_supplier_evaluation_rule IN (
            'LOWEST_COMPLIANT_QUOTE', 'COMPREHENSIVE_EVALUATION',
            'AUTHORIZED_DIRECT_SELECTION'
        )
    ),
    ADD CONSTRAINT chk_repair_governance_basis_minimum_counts CHECK (
        (minimum_invited_supplier_count IS NULL OR minimum_invited_supplier_count > 0)
        AND (minimum_valid_quote_count IS NULL OR minimum_valid_quote_count > 0)
        AND (
            minimum_invited_supplier_count IS NULL
            OR minimum_valid_quote_count IS NULL
            OR minimum_valid_quote_count <= minimum_invited_supplier_count
        )
    );

COMMENT ON COLUMN t_repair_governance_basis.approved_supplier_selection_method IS
    '盖章授权文件明确的施工单位选择方式；历史治理依据为空，不可据此定商';
COMMENT ON COLUMN t_repair_governance_basis.approved_supplier_evaluation_rule IS
    '盖章授权文件明确的最低合格报价、综合评审或授权直接选择规则';
COMMENT ON COLUMN t_repair_governance_basis.minimum_invited_supplier_count IS
    '仅在授权文件明确时保存的最低邀价数；空值不代表系统默认门槛';
COMMENT ON COLUMN t_repair_governance_basis.minimum_valid_quote_count IS
    '仅在授权文件明确时保存的最低有效报价数；空值不代表系统默认门槛';
COMMENT ON COLUMN t_repair_governance_basis.non_competitive_selection_basis IS
    '框架、直接或紧急定商在授权文件中的明确依据';

ALTER TABLE t_repair_project_supplier_selection
    RENAME COLUMN recommendation_reason TO selection_rationale;

ALTER TABLE t_repair_project_supplier_selection
    RENAME COLUMN recommended_by_user_id TO confirmed_by_user_id;

ALTER TABLE t_repair_project_supplier_selection
    ADD COLUMN selection_evaluation_rule VARCHAR(40),
    ADD COLUMN selection_evidence_attachment_id BIGINT
        REFERENCES t_repair_project_attachment(attachment_id),
    ADD COLUMN governance_basis_id BIGINT REFERENCES t_repair_governance_basis(basis_id),
    ADD COLUMN governance_basis_hash CHAR(64);

ALTER TABLE t_repair_project_supplier_selection
    ADD CONSTRAINT chk_repair_project_selection_evaluation_rule CHECK (
        selection_evaluation_rule IS NULL
        OR selection_evaluation_rule IN (
            'LOWEST_COMPLIANT_QUOTE', 'COMPREHENSIVE_EVALUATION',
            'AUTHORIZED_DIRECT_SELECTION'
        )
    );

COMMENT ON TABLE t_repair_project_supplier_selection IS
    '经有效决定/授权依据、评审或定商记录和有权主体确认的中选供应商快照；历史记录保留原字段事实';
COMMENT ON COLUMN t_repair_project_supplier_selection.selection_rationale IS
    '有权确认人对具体中选结果的评审或定商说明';
COMMENT ON COLUMN t_repair_project_supplier_selection.selection_evidence_attachment_id IS
    '评审记录、比价表或定商记录原件';
COMMENT ON COLUMN t_repair_project_supplier_selection.governance_basis_id IS
    '本次最终定商所依据的不可变决定/授权快照';
