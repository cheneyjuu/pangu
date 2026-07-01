-- Add chain attestation fields to fund ledger entries so trust-fund
-- installments can require the previous installment's txHash to be confirmed.
ALTER TABLE t_fund_ledger_entry
    ADD COLUMN blockchain_tx_hash VARCHAR(128),
    ADD COLUMN chain_attest_status SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN chain_confirmed_at TIMESTAMP,
    ADD CONSTRAINT chk_fund_ledger_chain_attest
        CHECK (chain_attest_status IN (1, 2, 3, 4));

CREATE INDEX idx_fund_ledger_chain_attest
    ON t_fund_ledger_entry(chain_attest_status, blockchain_tx_hash);

COMMENT ON COLUMN t_fund_ledger_entry.blockchain_tx_hash IS '链上交易 hash；信托制分期付款前置确认要求非空';
COMMENT ON COLUMN t_fund_ledger_entry.chain_attest_status IS '链上存证状态：1-PENDING, 2-SUBMITTED, 3-CONFIRMED, 4-FAILED';
COMMENT ON COLUMN t_fund_ledger_entry.chain_confirmed_at IS '链上确认时间';
