-- 关联业务：受影响业主是否参与工程验收由锁定方案决定，不再由楼栋/小区范围或治理路径推断。

ALTER TABLE t_repair_plan_version
    DROP CONSTRAINT chk_repair_plan_acceptance_rule,
    ADD CONSTRAINT chk_repair_plan_acceptance_rule CHECK (
        (
            affected_owner_scope_description IS NULL
            AND minimum_affected_owner_acceptors IS NULL
            AND affected_owner_pass_rule IS NULL
            AND affected_owner_approval_ratio IS NULL
        ) OR (
            affected_owner_scope_description IS NOT NULL
            AND minimum_affected_owner_acceptors > 0
            AND (
                (affected_owner_pass_rule = 'ALL' AND affected_owner_approval_ratio IS NULL)
                OR (
                    affected_owner_pass_rule = 'AT_LEAST_RATIO'
                    AND affected_owner_approval_ratio > 0
                    AND affected_owner_approval_ratio <= 1
                )
            )
        )
    );

COMMENT ON CONSTRAINT chk_repair_plan_acceptance_rule ON t_repair_plan_version IS
    '受影响业主参与验收时必须明确范围、最低人数和通过方式；与工程空间范围及治理路径无关';
