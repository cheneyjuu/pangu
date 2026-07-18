-- V3.97: 业主大会以业务办理顺序保存事项草案和原始材料，禁止人工录入内部标识或文件哈希。

ALTER TABLE t_owners_assembly_session
    DROP CONSTRAINT IF EXISTS chk_owners_assembly_session_mode;

ALTER TABLE t_owners_assembly_session
    ADD CONSTRAINT chk_owners_assembly_session_mode CHECK (
        preparation_mode IN (
            'FULL', 'QUICK',
            'WRITTEN_DECISION', 'OFFLINE_MEETING', 'ONLINE_AND_OFFLINE'
        )
    );

CREATE TABLE t_owners_assembly_subject_draft (
    draft_id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES t_owners_assembly_session(session_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    scope_reference_id BIGINT,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    party_ratio_floor NUMERIC(8, 5),
    proposed_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_owners_assembly_subject_draft_type CHECK (subject_type IN ('GENERAL', 'MAJOR')),
    CONSTRAINT chk_owners_assembly_subject_draft_scope CHECK (scope IN ('COMMUNITY', 'BUILDING', 'UNIT'))
);

CREATE INDEX idx_owners_assembly_subject_draft_session
    ON t_owners_assembly_subject_draft(session_id, tenant_id, draft_id);

COMMENT ON TABLE t_owners_assembly_subject_draft IS
    '业主大会会前表决事项草案；确认公示和表决安排时转换为正式 voting_subject';

CREATE TABLE t_owners_assembly_material (
    material_id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES t_owners_assembly_session(session_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    material_type VARCHAR(40) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    etag VARCHAR(128) NOT NULL,
    content_sha256 VARCHAR(128) NOT NULL,
    uploaded_by_account_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_owners_assembly_material_type CHECK (
        material_type IN (
            'PUBLIC_NOTICE', 'PLAN_ATTACHMENT', 'PAPER_BALLOT_TEMPLATE',
            'DELIVERY_EVIDENCE', 'PAPER_BALLOT'
        )
    ),
    CONSTRAINT chk_owners_assembly_material_size CHECK (file_size > 0)
);

CREATE INDEX idx_owners_assembly_material_session
    ON t_owners_assembly_material(session_id, tenant_id, material_type, material_id);

COMMENT ON TABLE t_owners_assembly_material IS
    '业主大会公告、方案、纸质选票及送达、回收凭证原件；摘要由服务端计算并留档';
COMMENT ON COLUMN t_owners_assembly_material.content_sha256 IS
    '服务端按上传原始文件计算的 SHA-256 摘要，供正式表决安排锁定使用';
