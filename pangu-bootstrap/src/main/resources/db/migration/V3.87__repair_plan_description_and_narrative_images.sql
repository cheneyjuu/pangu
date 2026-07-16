-- 关联业务：合并维修问题与实施范围，并为方案正文提供私有图片引用。

ALTER TABLE t_repair_plan_version
    ADD COLUMN plan_description TEXT;

UPDATE t_repair_plan_version
SET plan_description = CASE
    WHEN trim(problem_cause) = trim(implementation_scope) THEN problem_cause
    ELSE '<h3>现场问题与原因</h3>' || problem_cause
        || '<h3>维修范围与实施内容</h3>' || implementation_scope
END;

ALTER TABLE t_repair_plan_version
    ALTER COLUMN plan_description SET NOT NULL,
    ALTER COLUMN problem_cause DROP NOT NULL,
    ALTER COLUMN implementation_scope DROP NOT NULL;

COMMENT ON COLUMN t_repair_plan_version.plan_description
    IS '合并后的问题与维修方案正文；新方案只写入该字段';
COMMENT ON COLUMN t_repair_plan_version.problem_cause
    IS '历史锁定方案原问题原因，仅保留审计，不再写入新方案';
COMMENT ON COLUMN t_repair_plan_version.implementation_scope
    IS '历史锁定方案原实施范围，仅保留审计，不再写入新方案';

CREATE TABLE t_repair_narrative_image (
    image_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    project_id BIGINT REFERENCES t_repair_project(project_id) ON DELETE CASCADE,
    plan_id BIGINT REFERENCES t_repair_plan_version(plan_id) ON DELETE CASCADE,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    etag VARCHAR(128) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    uploaded_by_account_id BIGINT NOT NULL REFERENCES t_account(account_id),
    uploaded_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    bound_at TIMESTAMP,
    CONSTRAINT chk_repair_narrative_image_type CHECK (
        content_type IN ('image/jpeg', 'image/png', 'image/webp')
    ),
    CONSTRAINT chk_repair_narrative_image_size CHECK (file_size > 0 AND file_size <= 5242880),
    CONSTRAINT chk_repair_narrative_image_status CHECK (status IN ('DRAFT', 'BOUND')),
    CONSTRAINT chk_repair_narrative_image_binding CHECK (
        (status = 'DRAFT' AND project_id IS NULL AND plan_id IS NULL AND bound_at IS NULL)
        OR (status = 'BOUND' AND project_id IS NOT NULL AND plan_id IS NOT NULL AND bound_at IS NOT NULL)
    )
);

CREATE INDEX idx_repair_narrative_image_plan
    ON t_repair_narrative_image(plan_id, image_id)
    WHERE status = 'BOUND';

CREATE INDEX idx_repair_narrative_image_expiry
    ON t_repair_narrative_image(create_time, image_id)
    WHERE status = 'DRAFT';

COMMENT ON TABLE t_repair_narrative_image
    IS '维修实施方案正文私有图片；草稿上传后只可绑定到同租户的一个方案版本';
