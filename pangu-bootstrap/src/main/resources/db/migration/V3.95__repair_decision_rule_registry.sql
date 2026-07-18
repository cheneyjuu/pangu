-- 关联业务：按小区备案楼栋维修征询规则，并在项目发起时形成不可变规则快照。

CREATE TABLE t_repair_decision_rule (
    rule_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    rule_name VARCHAR(200) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    effective_at TIMESTAMP NOT NULL,
    delivery_rule VARCHAR(1000) NOT NULL,
    non_response_rule VARCHAR(32) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    etag VARCHAR(255) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    registered_by_account_id BIGINT NOT NULL,
    registered_by_user_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_repair_decision_rule_version UNIQUE (tenant_id, rule_name, rule_version),
    CONSTRAINT chk_repair_decision_rule_non_response CHECK (
        non_response_rule IN ('NOT_PARTICIPATED', 'FOLLOW_MAJORITY', 'ABSTAIN')
    ),
    CONSTRAINT chk_repair_decision_rule_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT chk_repair_decision_rule_file_size CHECK (file_size > 0)
);

CREATE UNIQUE INDEX uk_repair_decision_rule_active
    ON t_repair_decision_rule(tenant_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_repair_decision_rule_history
    ON t_repair_decision_rule(tenant_id, effective_at DESC, rule_id DESC);

COMMENT ON TABLE t_repair_decision_rule IS
    '小区实际备案的楼栋维修征询规则版本；登记后不可改写，新版本通过替代留存历史';
COMMENT ON COLUMN t_repair_decision_rule.delivery_rule IS '备案规则确定的有效送达方式';
COMMENT ON COLUMN t_repair_decision_rule.non_response_rule IS '备案规则确定的未表态处理方式';

ALTER TABLE t_repair_decision_policy_snapshot
    ALTER COLUMN rule_document_attachment_id DROP NOT NULL,
    ADD COLUMN rule_id BIGINT REFERENCES t_repair_decision_rule(rule_id),
    ADD COLUMN rule_name VARCHAR(200),
    ADD COLUMN rule_effective_at TIMESTAMP;

ALTER TABLE t_repair_decision_policy_snapshot
    ADD CONSTRAINT chk_repair_decision_policy_rule_source CHECK (
        rule_id IS NOT NULL OR rule_document_attachment_id IS NOT NULL
    );

COMMENT ON COLUMN t_repair_decision_policy_snapshot.rule_document_attachment_id IS
    '历史项目内临时规则附件，仅保留旧流程只读兼容；新流程使用 rule_id';
COMMENT ON COLUMN t_repair_decision_policy_snapshot.rule_id IS
    '发起征询时选中的小区有效备案规则';
