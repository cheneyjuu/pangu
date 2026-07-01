-- V3.8: 信托制双签动账锁类型
--
-- t_governance_lock 由 V2.5 建表并用 CHECK 约束固定 entity_type。
-- 梯度 C 需要把信托制付款指令接入同一套“业委会主任初签 + 街道办终签”机制，
-- 因此追加 TRUST_FUND_PAYMENT。

ALTER TABLE t_governance_lock
    DROP CONSTRAINT chk_lock_entity_type;

ALTER TABLE t_governance_lock
    ADD CONSTRAINT chk_lock_entity_type CHECK (
        entity_type IN (
            'FINANCE_DISCLOSURE',
            'ELECTION_DISCLOSURE',
            'FUND_LEDGER_PUBLISH',
            'TRUST_FUND_PAYMENT'
        )
    );

COMMENT ON COLUMN t_governance_lock.entity_type IS
    '锁定实体类型：FINANCE_DISCLOSURE / ELECTION_DISCLOSURE / FUND_LEDGER_PUBLISH / TRUST_FUND_PAYMENT';
