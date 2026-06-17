-- ===================================================================
-- 司法链 Outbox 事件表 (t_outbox_event)
--    本期不真实出链，仅落 outbox + 返回 STUB-{eventId}；
--    异步消费器（后续阶段）从 PENDING 起读取，调用 JudicialChainPort 真实实现，
--    成功后回填 blockchain_tx_hash 并将状态推进到 CONFIRMED。
-- ===================================================================
CREATE TABLE t_outbox_event (
    event_id BIGSERIAL PRIMARY KEY,
    event_type SMALLINT NOT NULL,
    business_ref_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    payload_json JSONB NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    CONSTRAINT chk_outbox_status CHECK (status IN (1, 2, 3, 4)),
    CONSTRAINT chk_outbox_event_type CHECK (event_type IN (1, 2, 3))
);

CREATE INDEX idx_outbox_status_created ON t_outbox_event(status, create_time);
CREATE INDEX idx_outbox_business ON t_outbox_event(event_type, business_ref_id);

COMMENT ON TABLE t_outbox_event IS '司法链存证 Outbox（事件可靠投递）';
COMMENT ON COLUMN t_outbox_event.event_type IS '事件类型：1-VOTING_RESULT_ATTEST, 2-WAIVER_APPROVED_ATTEST, 3-FUND_LEDGER_ATTEST';
COMMENT ON COLUMN t_outbox_event.status IS '状态：1-PENDING, 2-SUBMITTED, 3-CONFIRMED, 4-FAILED';
COMMENT ON COLUMN t_outbox_event.payload_json IS '存证 payload JSON（含业务关键字段+本地 hash）';
COMMENT ON COLUMN t_outbox_event.attempts IS '重试次数（指数退避）';
