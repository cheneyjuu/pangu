-- ===================================================================
-- 1. 维修资金账户树 (t_maintenance_fund_account)
--    单表层级树：account_level + parent_id + ancestors 物化路径 + version 乐观锁
--    动态总分平衡由 ApplicationService 在动账时维护
-- ===================================================================
CREATE TABLE t_maintenance_fund_account (
    account_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    account_level SMALLINT NOT NULL,
    reference_id BIGINT NOT NULL,
    parent_id BIGINT REFERENCES t_maintenance_fund_account(account_id),
    ancestors VARCHAR(500) NOT NULL DEFAULT '',
    total_balance DECIMAL(18,2) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(18,2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fund_acc_level CHECK (account_level IN (1, 2, 3, 4)),
    CONSTRAINT chk_fund_acc_balance_nonneg CHECK (total_balance >= 0 AND frozen_balance >= 0),
    CONSTRAINT chk_fund_acc_frozen_le_total CHECK (frozen_balance <= total_balance),
    CONSTRAINT uk_fund_level_ref UNIQUE (tenant_id, account_level, reference_id)
);

CREATE INDEX idx_fund_acc_ancestors ON t_maintenance_fund_account(ancestors);
CREATE INDEX idx_fund_acc_parent ON t_maintenance_fund_account(parent_id);
CREATE INDEX idx_fund_acc_tenant ON t_maintenance_fund_account(tenant_id);

COMMENT ON TABLE t_maintenance_fund_account IS '维修资金账户树（社区/楼栋/单元/房号 四级层级）';
COMMENT ON COLUMN t_maintenance_fund_account.account_level IS '账户层级：1-COMMUNITY(社区), 2-BUILDING(楼栋), 3-UNIT(单元), 4-ROOM(房号)';
COMMENT ON COLUMN t_maintenance_fund_account.reference_id IS '对应业务实体 ID（社区=tenant_id；楼栋=building_id；单元=单元 ID；房号=room_id）';
COMMENT ON COLUMN t_maintenance_fund_account.ancestors IS '祖级物化路径（格式：0,1001,2003），加速子树聚合';
COMMENT ON COLUMN t_maintenance_fund_account.total_balance IS '账户余额（含冻结部分）';
COMMENT ON COLUMN t_maintenance_fund_account.frozen_balance IS '冻结金额（已立项工程占用）';
COMMENT ON COLUMN t_maintenance_fund_account.version IS '乐观锁版本号';

-- ===================================================================
-- 2. 资金流水（动账审计）t_fund_ledger_entry
-- ===================================================================
CREATE TABLE t_fund_ledger_entry (
    entry_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES t_maintenance_fund_account(account_id),
    business_type SMALLINT NOT NULL,
    business_ref_id BIGINT,
    direction SMALLINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    balance_after DECIMAL(18,2) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operator_id BIGINT,
    audit_hash VARCHAR(64) NOT NULL,
    CONSTRAINT chk_ledger_direction CHECK (direction IN (1, 2)),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_ledger_balance_nonneg CHECK (balance_after >= 0)
);

CREATE INDEX idx_ledger_account ON t_fund_ledger_entry(account_id);
CREATE INDEX idx_ledger_occurred ON t_fund_ledger_entry(occurred_at);

COMMENT ON TABLE t_fund_ledger_entry IS '维修资金动账流水（每笔变更必登）';
COMMENT ON COLUMN t_fund_ledger_entry.business_type IS '业务类型：1-INITIAL_DEPOSIT, 2-OWNER_PAYMENT, 3-PUBLIC_INCOME_TRANSFER, 4-MAINTENANCE_PROJECT, 5-FREEZE, 6-UNFREEZE';
COMMENT ON COLUMN t_fund_ledger_entry.direction IS '方向：1-DEBIT(入账), 2-CREDIT(出账)';
COMMENT ON COLUMN t_fund_ledger_entry.balance_after IS '本次记账后账户余额';
COMMENT ON COLUMN t_fund_ledger_entry.audit_hash IS '行级审计 hash（链入 t_fund_ledger_entry 上一行 audit_hash 形成 hash chain）';
