-- 关联业务：维修报价按整份报价单统一计税，并按材料设备、人工、施工措施等类别记录差异化明细。

ALTER TABLE t_repair_project_supplier_quote
    ADD COLUMN amount_excluding_tax NUMERIC(14, 2),
    ADD COLUMN tax_rate NUMERIC(6, 3),
    ADD COLUMN tax_amount NUMERIC(14, 2);

ALTER TABLE t_repair_project_supplier_quote_line
    ADD COLUMN line_type VARCHAR(32),
    ADD COLUMN work_description VARCHAR(1000),
    ADD COLUMN procurement_method VARCHAR(120),
    ADD COLUMN unit_price_excluding_tax NUMERIC(14, 2),
    ADD COLUMN amount_excluding_tax NUMERIC(14, 2);

-- 旧报价按原逐行税率还原不含税金额；新报价不再写入旧逐行含税字段。
UPDATE t_repair_project_supplier_quote_line
SET line_type = 'OTHER',
    unit_price_excluding_tax = ROUND(
        tax_included_unit_price / (1 + tax_rate / 100), 2),
    amount_excluding_tax = ROUND(
        tax_included_amount / (1 + tax_rate / 100), 2);

WITH line_totals AS (
    SELECT quote_id, SUM(amount_excluding_tax) AS amount_excluding_tax
    FROM t_repair_project_supplier_quote_line
    GROUP BY quote_id
)
UPDATE t_repair_project_supplier_quote quote
SET amount_excluding_tax = COALESCE(line_totals.amount_excluding_tax, quote.quote_amount),
    tax_amount = quote.quote_amount - COALESCE(line_totals.amount_excluding_tax, quote.quote_amount),
    tax_rate = CASE
        WHEN COALESCE(line_totals.amount_excluding_tax, quote.quote_amount) > 0
            THEN ROUND(
                (quote.quote_amount / COALESCE(line_totals.amount_excluding_tax, quote.quote_amount) - 1) * 100,
                3)
        ELSE 0
    END
FROM line_totals
WHERE quote.quote_id = line_totals.quote_id;

UPDATE t_repair_project_supplier_quote
SET amount_excluding_tax = quote_amount,
    tax_rate = 0,
    tax_amount = 0
WHERE amount_excluding_tax IS NULL;

ALTER TABLE t_repair_project_supplier_quote
    ALTER COLUMN amount_excluding_tax SET NOT NULL,
    ALTER COLUMN tax_rate SET NOT NULL,
    ALTER COLUMN tax_amount SET NOT NULL,
    ADD CONSTRAINT chk_repair_project_quote_amount_excluding_tax CHECK (amount_excluding_tax >= 0),
    ADD CONSTRAINT chk_repair_project_quote_tax_rate CHECK (tax_rate BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_repair_project_quote_tax_amount CHECK (tax_amount >= 0);

ALTER TABLE t_repair_project_supplier_quote_line
    ALTER COLUMN line_type SET NOT NULL,
    ALTER COLUMN unit_price_excluding_tax SET NOT NULL,
    ALTER COLUMN amount_excluding_tax SET NOT NULL,
    ALTER COLUMN tax_included_unit_price DROP NOT NULL,
    ALTER COLUMN tax_rate DROP NOT NULL,
    ALTER COLUMN tax_included_amount DROP NOT NULL,
    ADD CONSTRAINT chk_repair_project_quote_line_type CHECK (
        line_type IN (
            'MATERIAL_EQUIPMENT', 'LABOR_SERVICE', 'CONSTRUCTION_MEASURE',
            'TRANSPORT_CLEANUP', 'OTHER'
        )
    ),
    ADD CONSTRAINT chk_repair_project_quote_line_unit_price_excluding_tax
        CHECK (unit_price_excluding_tax >= 0),
    ADD CONSTRAINT chk_repair_project_quote_line_amount_excluding_tax
        CHECK (amount_excluding_tax >= 0);

COMMENT ON COLUMN t_repair_project_supplier_quote.tax_rate IS
    '整份报价单统一适用的税率百分比；一份报价版本只能有一个总税率。';
COMMENT ON COLUMN t_repair_project_supplier_quote.amount_excluding_tax IS
    '报价明细不含税金额合计。';
COMMENT ON COLUMN t_repair_project_supplier_quote.tax_amount IS
    '按整份报价单税率计算的税额。';
COMMENT ON COLUMN t_repair_project_supplier_quote_line.line_type IS
    '明细类别：材料设备、人工服务、施工措施、运输清运或其他。';
COMMENT ON COLUMN t_repair_project_supplier_quote_line.work_description IS
    '项目特征、工作内容或服务说明，按维修内容选填。';
COMMENT ON COLUMN t_repair_project_supplier_quote_line.procurement_method IS
    '材料设备采购方式，报价单涉及采购时选填。';
COMMENT ON COLUMN t_repair_project_supplier_quote_line.tax_included_unit_price IS
    '历史兼容字段；V3.93 起新报价统一记录不含税单价并在报价头计税。';
COMMENT ON COLUMN t_repair_project_supplier_quote_line.tax_rate IS
    '历史兼容字段；V3.93 起税率统一记录在报价头。';
COMMENT ON COLUMN t_repair_project_supplier_quote_line.tax_included_amount IS
    '历史兼容字段；V3.93 起新报价统一记录不含税金额并在报价头计税。';
