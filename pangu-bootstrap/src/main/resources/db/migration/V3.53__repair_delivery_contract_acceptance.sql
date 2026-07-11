-- V3.53: 楼栋维修送审、供应商协作、三方合同与受影响业主验收。

ALTER TABLE t_repair_work_order DROP CONSTRAINT IF EXISTS chk_repair_status;

ALTER TABLE t_repair_work_order
    ADD CONSTRAINT chk_repair_status CHECK (status IN (
        'SUBMITTED', 'PENDING_VERIFY', 'NEED_MANUAL_LOCATION', 'VERIFIED', 'ASSIGNED',
        'SURVEYING', 'PLAN_SUBMITTED', 'QUOTE_COLLECTING', 'QUOTE_SUBMITTED',
        'SUPPLIER_RECOMMENDED', 'LOCAL_DECISION_PENDING', 'LOCAL_DECISION_PASSED',
        'ASSEMBLY_DECISION_PENDING', 'APPROVAL_DOCUMENT_PREPARING', 'PRICE_REVIEW_PENDING',
        'GOVERNANCE_PENDING', 'GOVERNANCE_CONFIRMED', 'SEALED', 'CONTRACT_SIGNING',
        'CONTRACT_EFFECTIVE', 'APPROVED', 'IN_PROGRESS', 'PENDING_ACCEPTANCE',
        'ACCEPTANCE_EXCEPTION', 'RECTIFICATION_REQUIRED', 'COMPLETED', 'EVALUATED',
        'ARCHIVED', 'REJECTED', 'CANCELLED', 'SUSPENDED', 'ESCALATED',
        'REASSIGN_REQUIRED', 'PLAN_REVISION_REQUIRED', 'CHANGE_REVIEW_PENDING',
        'PAYMENT_EXCEPTION', 'HANDOVER_LOCK', 'EMERGENCY_REPORTED',
        'EMERGENCY_MITIGATION', 'EMERGENCY_PLAN_PENDING', 'EMERGENCY_REPAIRING'
    ));

CREATE TABLE t_supplier_org_profile (
    supplier_dept_id BIGINT PRIMARY KEY REFERENCES sys_dept(dept_id),
    unified_social_credit_code VARCHAR(18) NOT NULL UNIQUE,
    legal_name VARCHAR(120) NOT NULL,
    contact_name VARCHAR(80) NOT NULL,
    contact_phone VARCHAR(20) NOT NULL,
    business_license_hash VARCHAR(128),
    verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    verified_by_user_id BIGINT REFERENCES sys_user(user_id),
    verified_at TIMESTAMP,
    disabled_reason VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_supplier_profile_status CHECK (
        verification_status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'DISABLED')
    )
);

COMMENT ON TABLE t_supplier_org_profile IS '供应商法律主体扩展，组织与账号仍复用 sys_dept/sys_user';

CREATE TABLE t_supplier_tenant_relation (
    relation_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    supplier_dept_id BIGINT NOT NULL REFERENCES t_supplier_org_profile(supplier_dept_id),
    relation_type VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    service_category VARCHAR(64),
    framework_agreement_hash VARCHAR(128),
    pricing_rule VARCHAR(1000),
    valid_from DATE,
    valid_until DATE,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
    requested_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    confirmed_by_user_id BIGINT REFERENCES sys_user(user_id),
    confirmed_position VARCHAR(32),
    confirmed_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_supplier_tenant_relation UNIQUE (tenant_id, supplier_dept_id, service_category),
    CONSTRAINT chk_supplier_relation_type CHECK (relation_type IN ('NORMAL', 'FRAMEWORK')),
    CONSTRAINT chk_supplier_relation_status CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'EXPIRED', 'DISABLED')),
    CONSTRAINT chk_supplier_relation_dates CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE INDEX idx_supplier_tenant_relation_active
    ON t_supplier_tenant_relation(tenant_id, status, service_category);

CREATE UNIQUE INDEX uk_supplier_tenant_relation_scope
    ON t_supplier_tenant_relation(tenant_id, supplier_dept_id, COALESCE(service_category, ''));

CREATE TABLE t_supplier_activation_invitation (
    invitation_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    supplier_dept_id BIGINT NOT NULL REFERENCES t_supplier_org_profile(supplier_dept_id),
    work_order_id BIGINT REFERENCES t_repair_work_order(work_order_id) ON DELETE SET NULL,
    contact_name VARCHAR(80) NOT NULL,
    contact_phone VARCHAR(20) NOT NULL,
    invitation_token_hash VARCHAR(128) NOT NULL UNIQUE,
    default_role_key VARCHAR(50) NOT NULL DEFAULT 'SERVICE_PROVIDER_STAFF',
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    activated_account_id BIGINT REFERENCES t_account(account_id),
    activated_user_id BIGINT REFERENCES sys_user(user_id),
    invited_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    activated_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_supplier_invitation_role CHECK (default_role_key = 'SERVICE_PROVIDER_STAFF'),
    CONSTRAINT chk_supplier_invitation_status CHECK (status IN ('PENDING', 'ACTIVATED', 'EXPIRED', 'CANCELLED'))
);

CREATE TABLE t_repair_quote_invitation (
    quote_invitation_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    supplier_dept_id BIGINT NOT NULL REFERENCES t_supplier_org_profile(supplier_dept_id),
    invited_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    deadline TIMESTAMP,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    response_remark VARCHAR(500),
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    CONSTRAINT uk_repair_quote_invitation UNIQUE (work_order_id, supplier_dept_id),
    CONSTRAINT chk_repair_quote_invitation_status CHECK (
        status IN ('PENDING', 'SUBMITTED', 'DECLINED', 'EXPIRED', 'CANCELLED')
    )
);

ALTER TABLE t_repair_supplier_quote
    ADD COLUMN supplier_dept_id BIGINT REFERENCES t_supplier_org_profile(supplier_dept_id),
    ADD COLUMN quote_invitation_id BIGINT REFERENCES t_repair_quote_invitation(quote_invitation_id),
    ADD COLUMN submission_source VARCHAR(32) NOT NULL DEFAULT 'PROPERTY_ENTRY',
    ADD COLUMN confirmation_status VARCHAR(40) NOT NULL DEFAULT 'PENDING_SUPPLIER_CONFIRMATION',
    ADD COLUMN original_source VARCHAR(32),
    ADD COLUMN original_attachment_hash VARCHAR(128);

ALTER TABLE t_repair_supplier_quote
    ADD CONSTRAINT chk_repair_quote_submission_source CHECK (
        submission_source IN ('SUPPLIER_ONLINE', 'PROPERTY_ENTRY')
    ),
    ADD CONSTRAINT chk_repair_quote_confirmation_status CHECK (
        confirmation_status IN (
            'PENDING_SUPPLIER_CONFIRMATION', 'ONLINE_CONFIRMED',
            'OFFLINE_EVIDENCE_VERIFIED', 'CONTRACT_CONFIRMED'
        )
    );

ALTER TABLE t_repair_supplier_recommendation
    ADD COLUMN selection_method VARCHAR(40) NOT NULL DEFAULT 'COMPETITIVE_QUOTATION',
    ADD COLUMN insufficient_quote_reason VARCHAR(1000),
    ADD COLUMN framework_relation_id BIGINT REFERENCES t_supplier_tenant_relation(relation_id);

ALTER TABLE t_repair_supplier_recommendation
    ADD CONSTRAINT chk_repair_supplier_selection_method CHECK (
        selection_method IN (
            'COMPETITIVE_QUOTATION', 'FRAMEWORK_SUPPLIER',
            'DIRECT_AWARD', 'EMERGENCY_APPOINTMENT'
        )
    );

ALTER TABLE t_repair_local_decision
    ADD COLUMN scope_type VARCHAR(24) NOT NULL DEFAULT 'BUILDING',
    ADD COLUMN unit_name VARCHAR(80),
    ADD COLUMN participated_owner_count INT,
    ADD COLUMN participated_area NUMERIC(14, 2),
    ADD COLUMN disagree_owner_count INT,
    ADD COLUMN disagree_area NUMERIC(14, 2),
    ADD COLUMN abstain_owner_count INT,
    ADD COLUMN abstain_area NUMERIC(14, 2),
    ADD COLUMN invalid_owner_count INT,
    ADD COLUMN invalid_area NUMERIC(14, 2);

ALTER TABLE t_repair_local_decision
    ADD CONSTRAINT chk_repair_local_scope_type CHECK (scope_type IN ('BUILDING', 'BUILDING_UNIT')),
    ADD CONSTRAINT chk_repair_local_participation CHECK (
        (participated_owner_count IS NULL AND participated_area IS NULL) OR (
            participated_owner_count IS NOT NULL AND participated_area IS NOT NULL
            AND participated_owner_count >= 0 AND participated_owner_count <= total_owner_count
            AND participated_area >= 0 AND participated_area <= total_area
        )
    );

CREATE TABLE t_repair_solitaire_entry (
    entry_id BIGSERIAL PRIMARY KEY,
    decision_id BIGINT NOT NULL REFERENCES t_repair_local_decision(decision_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    owner_uid BIGINT REFERENCES c_user(uid),
    choice VARCHAR(24) NOT NULL,
    build_area NUMERIC(14, 2) NOT NULL,
    original_text VARCHAR(1000),
    verification_status VARCHAR(24) NOT NULL DEFAULT 'VERIFIED',
    verified_by_user_id BIGINT REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_solitaire_room UNIQUE (decision_id, room_id),
    CONSTRAINT chk_repair_solitaire_choice CHECK (
        choice IN ('AGREE', 'DISAGREE', 'ABSTAIN', 'INVALID', 'NOT_VOTED', 'CONFLICTED')
    ),
    CONSTRAINT chk_repair_solitaire_verification CHECK (
        verification_status IN ('PENDING', 'VERIFIED', 'DISPUTED')
    )
);

CREATE TABLE t_repair_solitaire_evidence (
    evidence_id BIGSERIAL PRIMARY KEY,
    decision_id BIGINT NOT NULL REFERENCES t_repair_local_decision(decision_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    attachment_hash VARCHAR(128) NOT NULL,
    wechat_group_name VARCHAR(120),
    captured_at TIMESTAMP,
    uploaded_by_account_id BIGINT REFERENCES t_account(account_id),
    uploaded_by_user_id BIGINT REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_solitaire_evidence_type CHECK (
        evidence_type IN ('WECHAT_SCREENSHOT', 'PRINTED_COPY', 'OBJECTION', 'OTHER')
    )
);

CREATE TABLE t_repair_approval_package (
    package_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    version INT NOT NULL,
    official_document_hash VARCHAR(128) NOT NULL,
    merged_package_hash VARCHAR(128) NOT NULL,
    printed_and_attached SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    submitted_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_at TIMESTAMP,
    void_reason VARCHAR(500),
    CONSTRAINT uk_repair_approval_package_version UNIQUE (work_order_id, version),
    CONSTRAINT chk_repair_approval_package_printed CHECK (printed_and_attached IN (0, 1)),
    CONSTRAINT chk_repair_approval_package_status CHECK (status IN ('SUBMITTED', 'RETURNED', 'VOIDED', 'APPROVED'))
);

CREATE UNIQUE INDEX uk_repair_approval_package_active
    ON t_repair_approval_package(work_order_id) WHERE status IN ('SUBMITTED', 'APPROVED');

CREATE TABLE t_repair_approval_attachment (
    attachment_id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES t_repair_approval_package(package_id) ON DELETE CASCADE,
    attachment_type VARCHAR(40) NOT NULL,
    attachment_hash VARCHAR(128) NOT NULL,
    original_file_name VARCHAR(255),
    sort_order INT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_approval_attachment_order UNIQUE (package_id, sort_order)
);

CREATE TABLE t_repair_price_review (
    price_review_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    package_id BIGINT NOT NULL REFERENCES t_repair_approval_package(package_id),
    tenant_id BIGINT NOT NULL,
    review_mode VARCHAR(32) NOT NULL,
    reviewed_amount NUMERIC(14, 2) NOT NULL,
    review_report_hash VARCHAR(128),
    conclusion VARCHAR(32) NOT NULL,
    opinion VARCHAR(1000),
    reviewed_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_price_review_mode CHECK (review_mode IN ('INTERNAL_PRICE_REVIEW', 'THIRD_PARTY_COST_AUDIT')),
    CONSTRAINT chk_repair_price_review_conclusion CHECK (conclusion IN ('APPROVED', 'RETURNED', 'HOLD')),
    CONSTRAINT chk_repair_price_review_amount CHECK (reviewed_amount >= 0),
    CONSTRAINT chk_repair_external_review_file CHECK (
        review_mode <> 'THIRD_PARTY_COST_AUDIT' OR review_report_hash IS NOT NULL
    )
);

CREATE TABLE t_repair_contract (
    contract_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    supplier_dept_id BIGINT REFERENCES t_supplier_org_profile(supplier_dept_id),
    supplier_name VARCHAR(120) NOT NULL,
    contract_amount NUMERIC(14, 2) NOT NULL,
    repair_scope_hash VARCHAR(128) NOT NULL,
    fund_source VARCHAR(64) NOT NULL,
    signing_method VARCHAR(24) NOT NULL,
    contract_file_hash VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SIGNING',
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    effective_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_contract_active UNIQUE (work_order_id),
    CONSTRAINT chk_repair_contract_amount CHECK (contract_amount >= 0),
    CONSTRAINT chk_repair_contract_method CHECK (signing_method IN ('ONLINE', 'OFFLINE', 'MIXED')),
    CONSTRAINT chk_repair_contract_status CHECK (status IN ('SIGNING', 'EFFECTIVE', 'VOIDED'))
);

CREATE TABLE t_repair_contract_signature (
    signature_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL REFERENCES t_repair_contract(contract_id) ON DELETE CASCADE,
    party_type VARCHAR(32) NOT NULL,
    signer_name VARCHAR(120) NOT NULL,
    signer_user_id BIGINT REFERENCES sys_user(user_id),
    signature_method VARCHAR(24) NOT NULL,
    signature_file_hash VARCHAR(128),
    signed_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_contract_party UNIQUE (contract_id, party_type),
    CONSTRAINT chk_repair_contract_party CHECK (party_type IN ('OWNERS_ASSEMBLY_OR_GROUP', 'PROPERTY', 'SUPPLIER')),
    CONSTRAINT chk_repair_contract_signature_method CHECK (signature_method IN ('ELECTRONIC', 'PAPER_SCAN'))
);

CREATE TABLE t_repair_acceptance_scope (
    scope_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    affected_reason VARCHAR(500),
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_acceptance_scope UNIQUE (work_order_id, room_id)
);

CREATE TABLE t_repair_acceptance_record (
    acceptance_record_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    room_id BIGINT,
    participant_type VARCHAR(32) NOT NULL,
    participant_account_id BIGINT REFERENCES t_account(account_id),
    participant_user_id BIGINT REFERENCES sys_user(user_id),
    participant_name VARCHAR(120) NOT NULL,
    conclusion VARCHAR(32) NOT NULL,
    opinion VARCHAR(1000),
    signature_hash VARCHAR(128),
    evidence_hash VARCHAR(128),
    submitted_by_user_id BIGINT REFERENCES sys_user(user_id),
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_acceptance_participant CHECK (
        participant_type IN ('AFFECTED_OWNER', 'OWNER_REPRESENTATIVE', 'PROPERTY_REPRESENTATIVE', 'COMMITTEE_REPRESENTATIVE')
    ),
    CONSTRAINT chk_repair_acceptance_conclusion CHECK (
        conclusion IN ('PASSED', 'RECTIFICATION_REQUIRED', 'UNREACHABLE', 'AUTHORIZED')
    )
);

CREATE TABLE t_repair_payment_request (
    payment_request_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    contract_id BIGINT NOT NULL REFERENCES t_repair_contract(contract_id),
    tenant_id BIGINT NOT NULL,
    milestone_type VARCHAR(24) NOT NULL,
    requested_amount NUMERIC(14, 2) NOT NULL,
    condition_evidence_hash VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_FINANCE',
    requested_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_repair_payment_milestone CHECK (milestone_type IN ('ADVANCE', 'PROGRESS', 'ACCEPTANCE', 'WARRANTY')),
    CONSTRAINT chk_repair_payment_status CHECK (status IN ('PENDING_FINANCE', 'APPROVED', 'PAID', 'RETURNED', 'FAILED')),
    CONSTRAINT chk_repair_payment_amount CHECK (requested_amount > 0)
);

CREATE TABLE t_repair_emergency_case (
    emergency_case_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL UNIQUE REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    emergency_type VARCHAR(40) NOT NULL,
    danger_proof_hash VARCHAR(128) NOT NULL,
    mitigation_record_hash VARCHAR(128) NOT NULL,
    reported_to_committee_at TIMESTAMP,
    reported_to_housing_authority_at TIMESTAMP,
    professional_audit_hash VARCHAR(128),
    created_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES
    ('repair:workorder:supplier', '供应商邀价与本企业报价', 'REPAIR', 'S', 0),
    ('repair:workorder:local-decision', '楼组长接龙与验收组织', 'REPAIR', 'B', 0)
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT r.role_id, p.permission_key
FROM sys_role r
JOIN (
    VALUES
        ('SERVICE_PROVIDER_MANAGER', 'repair:workorder:supplier'),
        ('SERVICE_PROVIDER_STAFF', 'repair:workorder:supplier'),
        ('OWNER_REPRESENTATIVE', 'repair:workorder:local-decision')
) AS p(role_key, permission_key) ON p.role_key = r.role_key
ON CONFLICT (role_id, permission_key) DO NOTHING;

DELETE FROM sys_role_permission rp
USING sys_role r
WHERE rp.role_id = r.role_id
  AND r.role_key IN ('SERVICE_PROVIDER_MANAGER', 'SERVICE_PROVIDER_STAFF')
  AND rp.permission_key IN ('repair:workorder:read', 'repair:workorder:field');

INSERT INTO sys_menu (
    menu_id, parent_id, route_id, menu_name, path, icon, order_num, visible, status,
    required_permission, required_any_permissions, required_role_keys
) VALUES
    (10000, 0, 'supplier-service', '供应商工作台', '/supplier-workbench', 'BriefcaseBusiness',
     10, 1, '0', NULL, NULL, 'SERVICE_PROVIDER_MANAGER,SERVICE_PROVIDER_STAFF'),
    (10010, 10000, 'supplier-workbench', '待报价与报价', '/supplier-workbench', NULL,
     10, 1, '0', 'repair:workorder:supplier', NULL, NULL)
ON CONFLICT (menu_id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    route_id = EXCLUDED.route_id,
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    icon = EXCLUDED.icon,
    order_num = EXCLUDED.order_num,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    required_permission = EXCLUDED.required_permission,
    required_any_permissions = EXCLUDED.required_any_permissions,
    required_role_keys = EXCLUDED.required_role_keys;

DELETE FROM sys_role_menu role_menu
USING sys_role role
WHERE role_menu.role_id = role.role_id
  AND role.role_key IN ('SERVICE_PROVIDER_MANAGER', 'SERVICE_PROVIDER_STAFF');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM sys_role
CROSS JOIN (VALUES (10000), (10010)) menu(menu_id)
WHERE role_key IN ('SERVICE_PROVIDER_MANAGER', 'SERVICE_PROVIDER_STAFF')
ON CONFLICT (role_id, menu_id) DO NOTHING;

SELECT setval('sys_menu_menu_id_seq', GREATEST((SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu), 10010), true);

COMMENT ON TABLE t_repair_approval_package IS '物业正式文件与接龙/报价原件的版本化送审包';
COMMENT ON TABLE t_repair_contract IS '动用维修资金的三方施工承包合同';
COMMENT ON TABLE t_repair_acceptance_record IS '受影响业主、楼组长及可选见证人的独立验收记录';
