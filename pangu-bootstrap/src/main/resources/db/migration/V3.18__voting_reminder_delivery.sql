CREATE TABLE t_voting_reminder_delivery (
    delivery_id BIGSERIAL PRIMARY KEY,
    outbox_event_id BIGINT NOT NULL REFERENCES t_outbox_event(event_id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    opid BIGINT NOT NULL REFERENCES c_owner_property(opid),
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    phone VARCHAR(20) NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'SMS',
    message_template VARCHAR(64) NOT NULL,
    message TEXT,
    delivery_status SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_voting_reminder_delivery_channel CHECK (channel IN ('SMS', 'PUSH', 'IN_APP')),
    CONSTRAINT chk_voting_reminder_delivery_status CHECK (delivery_status IN (1, 2, 3, 4)),
    CONSTRAINT uidx_voting_reminder_delivery_event_owner_channel UNIQUE (outbox_event_id, opid, channel)
);

CREATE INDEX idx_voting_reminder_delivery_event
    ON t_voting_reminder_delivery(outbox_event_id, delivery_status);

CREATE INDEX idx_voting_reminder_delivery_uid
    ON t_voting_reminder_delivery(uid, created_at DESC);

COMMENT ON TABLE t_voting_reminder_delivery IS '催票 outbox 展开的逐户投递明细，后续真实短信/Push provider 从此表接续';
COMMENT ON COLUMN t_voting_reminder_delivery.delivery_status IS '1 READY/待投递供应商，2 SUBMITTED，3 CONFIRMED，4 FAILED';
