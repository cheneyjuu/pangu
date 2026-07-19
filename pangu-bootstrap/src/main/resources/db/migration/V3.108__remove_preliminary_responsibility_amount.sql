-- 关联业务：责任与资金初判只判断路径和证据；业主决定提案金额由冻结的实施方案预算快照形成。
-- 保留历史列仅为审计和旧版本回滚兼容；新应用不再读写该列，也不得把它解释为责任承诺、合同或付款金额。
ALTER TABLE t_repair_project_responsibility_determination
    DROP CONSTRAINT IF EXISTS chk_repair_responsibility_determination_amount;

ALTER TABLE t_repair_project_responsibility_determination
    ALTER COLUMN approved_amount DROP NOT NULL;

COMMENT ON COLUMN t_repair_project_responsibility_determination.approved_amount IS
    '历史初判金额字段。自 V3.108 起新流程不再读写；相关业主决定提案预算来自冻结的实施方案快照，合同、结算和付款金额在后续环节分别形成。';
