ALTER TABLE t_repair_quote_invitation
    ADD COLUMN invitation_round INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN invitation_type VARCHAR(16) NOT NULL DEFAULT 'INITIAL',
    ADD COLUMN revision_reason VARCHAR(500);

ALTER TABLE t_repair_quote_invitation
    DROP CONSTRAINT IF EXISTS uk_repair_quote_invitation;

ALTER TABLE t_repair_quote_invitation
    ADD CONSTRAINT uk_repair_quote_invitation_round
        UNIQUE (work_order_id, supplier_dept_id, invitation_round),
    ADD CONSTRAINT chk_repair_quote_invitation_round
        CHECK (invitation_round > 0),
    ADD CONSTRAINT chk_repair_quote_invitation_type
        CHECK (invitation_type IN ('INITIAL', 'REVISION')),
    ADD CONSTRAINT chk_repair_quote_revision_reason
        CHECK (invitation_type = 'INITIAL' OR revision_reason IS NOT NULL);

ALTER TABLE t_repair_supplier_quote
    DROP CONSTRAINT IF EXISTS chk_repair_quote_status;

ALTER TABLE t_repair_supplier_quote
    ADD CONSTRAINT chk_repair_quote_status
        CHECK (quote_status IN ('ACTIVE', 'REVISION_REQUESTED', 'SUPERSEDED'));

CREATE INDEX idx_repair_quote_invitation_round
    ON t_repair_quote_invitation(work_order_id, supplier_dept_id, invitation_round DESC);

COMMENT ON COLUMN t_repair_quote_invitation.invitation_round IS '同一工单与供应商的邀价轮次';
COMMENT ON COLUMN t_repair_quote_invitation.invitation_type IS 'INITIAL=首次邀价，REVISION=方案变更后的修订邀价';
COMMENT ON COLUMN t_repair_quote_invitation.revision_reason IS '修订邀价原因，用于供应商和审计理解变更背景';
COMMENT ON COLUMN t_repair_supplier_quote.quote_status IS 'ACTIVE=当前有效，REVISION_REQUESTED=已要求修订，SUPERSEDED=已被新版替代';
