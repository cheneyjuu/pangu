ALTER TABLE t_outbox_event
    DROP CONSTRAINT chk_outbox_event_type;

ALTER TABLE t_outbox_event
    ADD CONSTRAINT chk_outbox_event_type CHECK (event_type IN (1, 2, 3, 4));

CREATE TABLE t_voting_mobilization_reminder (
    reminder_id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    sent_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    permission_id BIGINT REFERENCES t_voting_mobilization_permission(permission_id),
    target_scope VARCHAR(32) NOT NULL DEFAULT 'UNVOTED_BUILDING_OWNERS',
    target_count INT NOT NULL DEFAULT 0,
    message_template VARCHAR(64) NOT NULL DEFAULT 'VOTE_REMINDER',
    message TEXT,
    outbox_event_id BIGINT REFERENCES t_outbox_event(event_id),
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_voting_mobilization_reminder_subject_building
    ON t_voting_mobilization_reminder(subject_id, building_id, sent_at DESC);

CREATE INDEX idx_voting_mobilization_reminder_sender
    ON t_voting_mobilization_reminder(sent_by_user_id, sent_at DESC);

COMMENT ON TABLE t_voting_mobilization_reminder IS '投票期催票发送记录';
COMMENT ON COLUMN t_voting_mobilization_reminder.target_scope IS '目标范围：UNVOTED_BUILDING_OWNERS=该楼栋未参与投票业主';
COMMENT ON COLUMN t_voting_mobilization_reminder.outbox_event_id IS '通知 outbox 事件 ID，异步消费者据此投递短信/Push';
COMMENT ON COLUMN t_outbox_event.event_type IS '事件类型：1-VOTING_RESULT_ATTEST, 2-WAIVER_APPROVED_ATTEST, 3-FUND_LEDGER_ATTEST, 4-VOTING_REMINDER_REQUESTED';
