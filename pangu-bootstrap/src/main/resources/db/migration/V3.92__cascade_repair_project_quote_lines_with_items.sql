-- 关联业务：删除维修工程草稿或方案工程项时同步清理其报价明细，避免双重外键级联顺序冲突。

ALTER TABLE t_repair_project_supplier_quote_line
    DROP CONSTRAINT t_repair_project_supplier_quote_line_project_item_id_fkey,
    ADD CONSTRAINT fk_repair_project_quote_line_item
        FOREIGN KEY (project_item_id) REFERENCES t_repair_project_item(item_id) ON DELETE CASCADE;
