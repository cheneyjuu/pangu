ALTER TABLE t_repair_local_decision
    DROP CONSTRAINT IF EXISTS t_repair_local_decision_work_order_id_key;

CREATE INDEX IF NOT EXISTS idx_repair_local_decision_order_round
    ON t_repair_local_decision(work_order_id, tenant_id, decision_id DESC);

ALTER TABLE t_repair_assembly_decision
    DROP CONSTRAINT IF EXISTS t_repair_assembly_decision_work_order_id_key;

CREATE INDEX IF NOT EXISTS idx_repair_assembly_decision_order_round
    ON t_repair_assembly_decision(work_order_id, tenant_id, repair_assembly_decision_id DESC);

COMMENT ON TABLE t_repair_local_decision IS
    '楼栋维修表决轮次；方案修订后新建轮次，历史轮次不覆盖';

COMMENT ON TABLE t_repair_assembly_decision IS
    '跨楼栋或小区整体维修关联的业主大会表决轮次；方案修订后保留历史轮次';
