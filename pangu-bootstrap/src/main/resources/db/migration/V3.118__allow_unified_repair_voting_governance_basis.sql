-- 关联业务：允许维修工程把统一表决通过结果登记为后续锁定方案和选择施工单位的授权依据。

ALTER TABLE t_repair_governance_basis
    DROP CONSTRAINT chk_repair_governance_basis_type,
    ADD CONSTRAINT chk_repair_governance_basis_type CHECK (
        basis_type IN (
            'BUILDING_REPAIR_DECISION',
            'COMMUNITY_ASSEMBLY_DECISION',
            'OWNER_VOTING_DECISION'
        )
    ),
    DROP CONSTRAINT chk_repair_governance_basis_reference,
    ADD CONSTRAINT chk_repair_governance_basis_reference CHECK (
        reference_type IN ('BUILDING_PROCESS', 'ASSEMBLY_SUBJECT', 'VOTING_SUBJECT')
    );
