ALTER TABLE t_repair_supplier_quote
    ADD COLUMN quote_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN revision_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN superseded_by_quote_id BIGINT;

WITH ranked AS (
    SELECT quote.quote_id,
           ROW_NUMBER() OVER (
               PARTITION BY quote.work_order_id, quote.supplier_dept_id
               ORDER BY quote.create_time, quote.quote_id
           ) AS revision_no,
           FIRST_VALUE(quote.quote_id) OVER (
               PARTITION BY quote.work_order_id, quote.supplier_dept_id
               ORDER BY CASE WHEN EXISTS (
                            SELECT 1
                            FROM t_repair_supplier_recommendation recommendation
                            WHERE recommendation.quote_id = quote.quote_id
                        ) THEN 0 ELSE 1 END,
                        quote.create_time DESC,
                        quote.quote_id DESC
           ) AS active_quote_id
    FROM t_repair_supplier_quote quote
    WHERE quote.supplier_dept_id IS NOT NULL
)
UPDATE t_repair_supplier_quote quote
SET revision_no = ranked.revision_no,
    quote_status = CASE WHEN quote.quote_id = ranked.active_quote_id THEN 'ACTIVE' ELSE 'SUPERSEDED' END,
    superseded_by_quote_id = CASE
        WHEN quote.quote_id = ranked.active_quote_id THEN NULL
        ELSE ranked.active_quote_id
    END
FROM ranked
WHERE quote.quote_id = ranked.quote_id;

ALTER TABLE t_repair_supplier_quote
    ADD CONSTRAINT fk_repair_quote_superseded_by
        FOREIGN KEY (superseded_by_quote_id) REFERENCES t_repair_supplier_quote(quote_id),
    ADD CONSTRAINT chk_repair_quote_status
        CHECK (quote_status IN ('ACTIVE', 'SUPERSEDED')),
    ADD CONSTRAINT chk_repair_quote_revision_no
        CHECK (revision_no > 0);

CREATE UNIQUE INDEX uk_repair_supplier_active_quote
    ON t_repair_supplier_quote(work_order_id, supplier_dept_id)
    WHERE quote_status = 'ACTIVE' AND supplier_dept_id IS NOT NULL;

CREATE INDEX idx_repair_supplier_quote_history
    ON t_repair_supplier_quote(work_order_id, supplier_dept_id, revision_no DESC);

COMMENT ON COLUMN t_repair_supplier_quote.quote_status IS '报价版本状态；每个工单、供应商仅允许一个 ACTIVE 报价';
COMMENT ON COLUMN t_repair_supplier_quote.revision_no IS '同一工单、同一供应商的报价修订版本号';
COMMENT ON COLUMN t_repair_supplier_quote.superseded_by_quote_id IS '替代当前历史报价的新报价记录';
