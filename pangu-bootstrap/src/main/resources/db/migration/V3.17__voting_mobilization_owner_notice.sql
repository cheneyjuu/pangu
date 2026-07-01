CREATE TABLE t_voting_mobilization_owner_notice (
    notice_id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL REFERENCES t_voting_subject(subject_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL,
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    building_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    notified_by_user_id BIGINT NOT NULL REFERENCES sys_user(user_id),
    channel VARCHAR(16) NOT NULL,
    note TEXT,
    notified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_voting_owner_notice_channel CHECK (channel IN ('PHONE', 'VISIT', 'WECHAT')),
    CONSTRAINT uidx_voting_owner_notice_subject_uid_channel UNIQUE (subject_id, uid, channel)
);

CREATE INDEX idx_voting_owner_notice_subject_uid
    ON t_voting_mobilization_owner_notice(subject_id, uid, notified_at DESC);

CREATE INDEX idx_voting_owner_notice_notifier
    ON t_voting_mobilization_owner_notice(notified_by_user_id, notified_at DESC);

COMMENT ON TABLE t_voting_mobilization_owner_notice IS '投票期逐户催票已通知记录';
COMMENT ON COLUMN t_voting_mobilization_owner_notice.channel IS '通知渠道：PHONE/访问电话，VISIT/上门，WECHAT/微信';
