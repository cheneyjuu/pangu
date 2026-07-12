ALTER TABLE t_repair_attachment
    DROP CONSTRAINT IF EXISTS chk_repair_attachment_kind;

ALTER TABLE t_repair_attachment
    ADD CONSTRAINT chk_repair_attachment_kind CHECK (
        attachment_kind IN (
            'INTAKE_ATTACHMENT', 'LOCATION_IMAGE', 'SURVEY_IMAGE', 'SURVEY_VIDEO',
            'QUOTE_DOCUMENT', 'SOLITAIRE_SCREENSHOT'
        )
    );

COMMENT ON COLUMN t_repair_attachment.attachment_kind IS
    'INTAKE_ATTACHMENT=登记工单附件；LOCATION_IMAGE=位置证据；SURVEY_IMAGE/SURVEY_VIDEO=初勘证据；QUOTE_DOCUMENT=报价原件；SOLITAIRE_SCREENSHOT=微信接龙截图';
