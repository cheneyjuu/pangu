ALTER TABLE t_repair_local_decision
    ADD COLUMN decision_channel VARCHAR(16) NOT NULL DEFAULT 'WECHAT';

ALTER TABLE t_repair_local_decision
    ADD CONSTRAINT chk_repair_local_decision_channel
        CHECK (decision_channel IN ('ONLINE', 'WECHAT'));

ALTER TABLE t_repair_solitaire_entry
    ADD COLUMN submission_channel VARCHAR(16) NOT NULL DEFAULT 'WECHAT',
    ADD COLUMN submitted_by_account_id BIGINT REFERENCES t_account(account_id),
    ADD COLUMN revision_no INTEGER NOT NULL DEFAULT 1;

ALTER TABLE t_repair_solitaire_entry
    ADD CONSTRAINT chk_repair_solitaire_submission_channel
        CHECK (submission_channel IN ('ONLINE', 'WECHAT')),
    ADD CONSTRAINT chk_repair_solitaire_revision_no
        CHECK (revision_no > 0);

CREATE INDEX idx_repair_online_decision_owner
    ON t_repair_solitaire_entry(decision_id, owner_uid)
    WHERE submission_channel = 'ONLINE';

ALTER TABLE t_repair_attachment
    DROP CONSTRAINT IF EXISTS chk_repair_attachment_kind;

ALTER TABLE t_repair_attachment
    ADD CONSTRAINT chk_repair_attachment_kind CHECK (
        attachment_kind IN (
            'LOCATION_IMAGE', 'SURVEY_IMAGE', 'SURVEY_VIDEO',
            'QUOTE_DOCUMENT', 'SOLITAIRE_SCREENSHOT'
        )
    );

COMMENT ON COLUMN t_repair_local_decision.decision_channel IS '楼栋维修正式表决渠道，ONLINE=C端在线表决，WECHAT=微信接龙后物业核验';
COMMENT ON COLUMN t_repair_solitaire_entry.submitted_by_account_id IS 'C端在线表决的自然人账号；微信接龙核验为空';
