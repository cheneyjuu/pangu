-- Clock Suspend 自引用外键删除策略修正。
-- 测试/运维清理触发换届议题时，不应因为其他议题记录了暂停来源而阻塞删除；
-- 保留被暂停议题本身，来源 subject_id 删除时置空即可。
ALTER TABLE t_voting_subject
    DROP CONSTRAINT IF EXISTS t_voting_subject_clock_suspended_by_subject_id_fkey;

ALTER TABLE t_voting_subject
    ADD CONSTRAINT t_voting_subject_clock_suspended_by_subject_id_fkey
    FOREIGN KEY (clock_suspended_by_subject_id)
    REFERENCES t_voting_subject(subject_id)
    ON DELETE SET NULL;
