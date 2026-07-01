-- 梯度 E4：Clock Suspend。
-- HANDOVER_LOCK 期间，已公示/投票中的非选举议题倒计时物理暂停；
-- NORMAL 恢复时按暂停时长顺延 vote_start_at / vote_end_at。
ALTER TABLE t_voting_subject
    ADD COLUMN IF NOT EXISTS clock_suspended_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS clock_suspended_by_subject_id BIGINT REFERENCES t_voting_subject(subject_id);

COMMENT ON COLUMN t_voting_subject.clock_suspended_at IS
    'Clock Suspend 生效时间；非 NULL 表示该议题投票倒计时因 HANDOVER_LOCK 暂停';
COMMENT ON COLUMN t_voting_subject.clock_suspended_by_subject_id IS
    '触发本次 Clock Suspend 的换届选举议题 subject_id';

CREATE INDEX IF NOT EXISTS idx_voting_subject_clock_suspended
    ON t_voting_subject(tenant_id, clock_suspended_at)
    WHERE clock_suspended_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_voting_subject_status_start_active
    ON t_voting_subject(status, vote_start_at)
    WHERE clock_suspended_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_voting_subject_status_end_active
    ON t_voting_subject(status, vote_end_at)
    WHERE clock_suspended_at IS NULL;
