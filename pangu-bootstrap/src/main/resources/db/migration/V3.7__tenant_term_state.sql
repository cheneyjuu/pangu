-- 梯度 C：租户任期状态。当前库没有独立 sys_tenant 表，先用专用状态表承载 HANDOVER_LOCK。
CREATE TABLE IF NOT EXISTS t_tenant_term_state (
    tenant_id BIGINT PRIMARY KEY,
    term_status SMALLINT NOT NULL DEFAULT 1 CHECK (term_status IN (1, 2)),
    term_locked_at TIMESTAMP,
    term_locked_by_subject_id BIGINT REFERENCES t_voting_subject(subject_id),
    term_unlocked_at TIMESTAMP,
    term_unlocked_by_user_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_tenant_term_state IS '租户任期状态：NORMAL / HANDOVER_LOCK';
COMMENT ON COLUMN t_tenant_term_state.term_status IS '1=NORMAL, 2=HANDOVER_LOCK';
COMMENT ON COLUMN t_tenant_term_state.term_locked_by_subject_id IS '触发换届锁的 ELECTION 议题';
