ALTER TABLE t_voting_reminder_delivery
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at TIMESTAMP,
    ADD COLUMN submitted_at TIMESTAMP,
    ADD COLUMN confirmed_at TIMESTAMP,
    ADD COLUMN failed_at TIMESTAMP,
    ADD COLUMN provider_message_id VARCHAR(128),
    ADD COLUMN last_error TEXT;

CREATE INDEX idx_voting_reminder_delivery_ready
    ON t_voting_reminder_delivery(delivery_status, attempts, created_at)
    WHERE delivery_status IN (1, 4);

COMMENT ON COLUMN t_voting_reminder_delivery.attempts IS '投递供应商尝试次数';
COMMENT ON COLUMN t_voting_reminder_delivery.provider_message_id IS '短信/Push/站内信供应商返回的消息 ID';
COMMENT ON COLUMN t_voting_reminder_delivery.last_error IS '最近一次供应商投递失败原因';
