CREATE TABLE t_repair_attachment (
    attachment_id BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT NOT NULL REFERENCES t_repair_work_order(work_order_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    attachment_kind VARCHAR(32) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    declared_size BIGINT NOT NULL,
    actual_size BIGINT,
    etag VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    uploaded_by_account_id BIGINT NOT NULL,
    bound_action VARCHAR(40),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    CONSTRAINT uk_repair_attachment_object_key UNIQUE (object_key),
    CONSTRAINT chk_repair_attachment_kind CHECK (
        attachment_kind IN ('LOCATION_IMAGE', 'SURVEY_IMAGE', 'SURVEY_VIDEO')
    ),
    CONSTRAINT chk_repair_attachment_status CHECK (status IN ('PENDING', 'READY', 'BOUND')),
    CONSTRAINT chk_repair_attachment_size CHECK (declared_size > 0 AND (actual_size IS NULL OR actual_size > 0))
);

CREATE INDEX idx_repair_attachment_order
    ON t_repair_attachment (tenant_id, work_order_id, attachment_kind, status);

COMMENT ON TABLE t_repair_attachment IS '维修工单 OSS 现场证据附件；PENDING 签发直传票据，READY 已核验对象，BOUND 已绑定业务动作';
COMMENT ON COLUMN t_repair_attachment.object_key IS '私有 OSS Bucket 对象键，不保存永久公开 URL';
COMMENT ON COLUMN t_repair_attachment.etag IS 'OSS 单文件上传返回的 ETag，用于对象完整性核对与审计';
