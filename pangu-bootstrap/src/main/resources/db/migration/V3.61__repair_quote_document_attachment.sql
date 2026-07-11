ALTER TABLE t_repair_attachment
    DROP CONSTRAINT IF EXISTS chk_repair_attachment_kind;

ALTER TABLE t_repair_attachment
    ADD CONSTRAINT chk_repair_attachment_kind CHECK (
        attachment_kind IN ('LOCATION_IMAGE', 'SURVEY_IMAGE', 'SURVEY_VIDEO', 'QUOTE_DOCUMENT')
    );

ALTER TABLE t_repair_supplier_quote
    ADD COLUMN attachment_id BIGINT REFERENCES t_repair_attachment(attachment_id);

CREATE INDEX idx_repair_supplier_quote_attachment
    ON t_repair_supplier_quote(attachment_id)
    WHERE attachment_id IS NOT NULL;

COMMENT ON COLUMN t_repair_supplier_quote.attachment_id IS '报价原件 OSS 附件记录；attachment_hash 由服务端依据该附件 ETag 生成';
