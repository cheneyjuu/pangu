ALTER TABLE t_repair_local_decision
    DROP CONSTRAINT IF EXISTS chk_repair_local_decision_result;

ALTER TABLE t_repair_local_decision
    ADD CONSTRAINT chk_repair_local_decision_result
        CHECK (result IN ('COLLECTING', 'PAUSED', 'PASSED', 'FAILED', 'DISPUTED'));

COMMENT ON COLUMN t_repair_local_decision.result IS
    'COLLECTING=表决中，PAUSED=已暂停且保留选票，PASSED/FAILED=已结算，DISPUTED=结果争议';
