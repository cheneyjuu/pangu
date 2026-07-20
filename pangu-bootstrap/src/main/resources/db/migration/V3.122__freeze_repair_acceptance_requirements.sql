-- 关联业务：把实施方案约定的验收方式、参与方、定案人和依据冻结为可执行项目验收快照。

ALTER TABLE t_repair_plan_version
    ADD COLUMN acceptance_requirements_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    ADD COLUMN acceptance_finalizer_roles_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    ADD COLUMN acceptance_basis_attachment_ids_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    ADD COLUMN acceptance_basis_summary VARCHAR(1000),
    ADD CONSTRAINT chk_repair_plan_acceptance_snapshot_json CHECK (
        jsonb_typeof(acceptance_requirements_json) = 'array'
        AND jsonb_typeof(acceptance_finalizer_roles_json) = 'array'
        AND jsonb_typeof(acceptance_basis_attachment_ids_json) = 'array'
    );

ALTER TABLE t_repair_acceptance_policy_snapshot
    ADD COLUMN acceptance_method TEXT,
    ADD COLUMN requirements_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    ADD COLUMN finalizer_roles_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    ADD COLUMN basis_attachment_ids_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    ADD COLUMN basis_summary VARCHAR(1000),
    DROP CONSTRAINT chk_repair_acceptance_policy_shape,
    DROP CONSTRAINT chk_repair_project_acceptance_rule,
    ADD CONSTRAINT chk_repair_acceptance_policy_counts CHECK (
        affected_owner_count >= 0
        AND (
            (affected_owner_count = 0
                AND minimum_affected_owner_participants IS NULL
                AND minimum_affected_owner_approvals IS NULL)
            OR
            (affected_owner_count > 0
                AND minimum_affected_owner_participants BETWEEN 1 AND affected_owner_count
                AND minimum_affected_owner_approvals BETWEEN 1 AND minimum_affected_owner_participants)
        )
    ),
    ADD CONSTRAINT chk_repair_acceptance_policy_json CHECK (
        jsonb_typeof(requirements_json) = 'array'
        AND jsonb_typeof(finalizer_roles_json) = 'array'
        AND jsonb_typeof(basis_attachment_ids_json) = 'array'
    ),
    ADD CONSTRAINT chk_repair_project_acceptance_owner_ratio CHECK (
        project_id IS NULL
        OR affected_owner_count = 0
        OR (
            affected_owner_pass_rule IN ('ALL', 'AT_LEAST_RATIO')
            AND affected_owner_approval_ratio > 0
            AND affected_owner_approval_ratio <= 1
        )
    );

-- 历史快照保持原流程语义；新项目必须由服务端从方案填写完整、可解释的要求。
UPDATE t_repair_acceptance_policy_snapshot
SET acceptance_method = COALESCE(acceptance_method, '按历史流程验收'),
    requirements_json = CASE workflow_type
        WHEN 'BUILDING_REPAIR' THEN '[
            {"requirementCode":"BUILDING_LEADER","businessName":"楼栋代表验收","eligibleRoles":["BUILDING_LEADER"],"minimumPassingCount":1,"evidenceRequired":false},
            {"requirementCode":"AFFECTED_OWNER","businessName":"受影响业主验收","eligibleRoles":["AFFECTED_OWNER"],"minimumPassingCount":1,"evidenceRequired":false}
        ]'::JSONB
        ELSE '[
            {"requirementCode":"COMMITTEE_EXECUTIVE","businessName":"业委会负责人验收","eligibleRoles":["COMMITTEE_EXECUTIVE_APPROVER"],"minimumPassingCount":1,"evidenceRequired":false},
            {"requirementCode":"COMMITTEE_SEAL","businessName":"验收文件用印","eligibleRoles":["COMMITTEE_SEAL_OPERATOR"],"minimumPassingCount":1,"evidenceRequired":true},
            {"requirementCode":"TECHNICAL_COSIGN","businessName":"专业人员共同验收","eligibleRoles":["PROPERTY_TECHNICAL_COSIGNER","THIRD_PARTY_TECHNICAL_COSIGNER"],"minimumPassingCount":1,"evidenceRequired":true}
        ]'::JSONB
    END,
    finalizer_roles_json = CASE workflow_type
        WHEN 'BUILDING_REPAIR' THEN '["BUILDING_LEADER"]'::JSONB
        ELSE '["COMMITTEE_EXECUTIVE_APPROVER"]'::JSONB
    END
WHERE jsonb_array_length(requirements_json) = 0;

COMMENT ON COLUMN t_repair_plan_version.acceptance_requirements_json IS
    '授权提案中明确的验收要求组合；参与方来自方案依据，不由维修范围类型推断';
COMMENT ON COLUMN t_repair_plan_version.acceptance_finalizer_roles_json IS
    '可对验收轮次形成最终结论的业务角色';
COMMENT ON COLUMN t_repair_acceptance_policy_snapshot.requirements_json IS
    '竣工结算核验时从锁定方案复制的不可变验收要求';
COMMENT ON COLUMN t_repair_acceptance_policy_snapshot.basis_attachment_ids_json IS
    '形成验收要求的方案、合同或有效决定依据附件';
