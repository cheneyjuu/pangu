-- 关联业务：为维修工程项目报价增加工期、质保、原件一致性确认和不可变结构化报价明细。

ALTER TABLE t_repair_project_supplier_quote
    ADD COLUMN construction_period_days INTEGER,
    ADD COLUMN warranty_days INTEGER,
    ADD COLUMN original_amount_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_repair_project_quote_construction_period
        CHECK (construction_period_days IS NULL OR construction_period_days BETWEEN 1 AND 3650),
    ADD CONSTRAINT chk_repair_project_quote_warranty_days
        CHECK (warranty_days IS NULL OR warranty_days BETWEEN 0 AND 3650);

CREATE TABLE t_repair_project_supplier_quote_line (
    quote_line_id BIGSERIAL PRIMARY KEY,
    quote_id BIGINT NOT NULL REFERENCES t_repair_project_supplier_quote(quote_id) ON DELETE CASCADE,
    project_item_id BIGINT NOT NULL REFERENCES t_repair_project_item(item_id),
    line_no INTEGER NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    specification_model VARCHAR(200),
    brand VARCHAR(120),
    quantity NUMERIC(14, 3) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    tax_included_unit_price NUMERIC(14, 2) NOT NULL,
    tax_rate NUMERIC(6, 3) NOT NULL,
    tax_included_amount NUMERIC(14, 2) NOT NULL,
    remark VARCHAR(500),
    CONSTRAINT uk_repair_project_quote_line_order
        UNIQUE (quote_id, project_item_id, line_no),
    CONSTRAINT chk_repair_project_quote_line_no CHECK (line_no > 0),
    CONSTRAINT chk_repair_project_quote_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_repair_project_quote_line_unit_price CHECK (tax_included_unit_price >= 0),
    CONSTRAINT chk_repair_project_quote_line_tax_rate CHECK (tax_rate BETWEEN 0 AND 100),
    CONSTRAINT chk_repair_project_quote_line_amount CHECK (tax_included_amount >= 0)
);

CREATE INDEX idx_repair_project_quote_line_quote
    ON t_repair_project_supplier_quote_line(quote_id, project_item_id, line_no);

COMMENT ON COLUMN t_repair_project_supplier_quote.original_amount_confirmed IS
    '提交人确认结构化明细含税合计与所上传报价原件总额一致；不以平台估算替代原件。';
COMMENT ON TABLE t_repair_project_supplier_quote_line IS
    '维修工程供应商报价版本下的材料、人工、运输等结构化明细；每行必须绑定当前方案工程项。';
