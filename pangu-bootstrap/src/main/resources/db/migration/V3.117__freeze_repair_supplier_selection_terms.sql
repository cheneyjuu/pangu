-- 关联业务：在相关业主表决前冻结施工单位选择方式，并在表决通过后固化获批预算。

ALTER TABLE t_repair_plan_version
    ADD COLUMN supplier_selection_evaluation_rule VARCHAR(40),
    ADD COLUMN minimum_invited_supplier_count INTEGER,
    ADD COLUMN minimum_valid_quote_count INTEGER,
    ADD COLUMN non_competitive_selection_basis VARCHAR(1000);

ALTER TABLE t_repair_plan_version
    ADD CONSTRAINT chk_repair_plan_supplier_selection_terms CHECK (
        (
            supplier_selection_evaluation_rule IS NULL
            AND minimum_invited_supplier_count IS NULL
            AND minimum_valid_quote_count IS NULL
            AND non_competitive_selection_basis IS NULL
        ) OR (
            supplier_selection_method = 'COMPETITIVE_QUOTATION'
            AND supplier_selection_evaluation_rule IN (
                'LOWEST_COMPLIANT_QUOTE', 'COMPREHENSIVE_EVALUATION'
            )
            AND non_competitive_selection_basis IS NULL
            AND (minimum_invited_supplier_count IS NULL OR minimum_invited_supplier_count > 0)
            AND (minimum_valid_quote_count IS NULL OR minimum_valid_quote_count > 0)
            AND (
                minimum_invited_supplier_count IS NULL
                OR minimum_valid_quote_count IS NULL
                OR minimum_valid_quote_count <= minimum_invited_supplier_count
            )
        ) OR (
            supplier_selection_method IN (
                'FRAMEWORK_SUPPLIER', 'DIRECT_AWARD', 'EMERGENCY_APPOINTMENT'
            )
            AND supplier_selection_evaluation_rule = 'AUTHORIZED_DIRECT_SELECTION'
            AND minimum_invited_supplier_count IS NULL
            AND minimum_valid_quote_count IS NULL
            AND NULLIF(BTRIM(non_competitive_selection_basis), '') IS NOT NULL
        )
    );

ALTER TABLE t_repair_governance_basis
    ADD COLUMN approved_budget_amount NUMERIC(14, 2),
    ADD CONSTRAINT chk_repair_governance_basis_approved_budget CHECK (
        approved_budget_amount IS NULL OR approved_budget_amount > 0
    );

COMMENT ON COLUMN t_repair_plan_version.supplier_selection_evaluation_rule IS
    '授权提案中冻结的报价比较方式；非询价方式固定为按已批准书面依据选择';
COMMENT ON COLUMN t_repair_plan_version.minimum_invited_supplier_count IS
    '实施方案或有效依据明确的最低邀请单位数；空值不代表平台默认门槛';
COMMENT ON COLUMN t_repair_plan_version.minimum_valid_quote_count IS
    '实施方案或有效依据明确的最低有效报价数；空值不代表平台默认门槛';
COMMENT ON COLUMN t_repair_plan_version.non_competitive_selection_basis IS
    '长期合作单位、直接委托或紧急指定的适用依据';
COMMENT ON COLUMN t_repair_governance_basis.approved_budget_amount IS
    '表决或其他有效授权通过的预算上限；历史依据为空时不得由前端补造';
