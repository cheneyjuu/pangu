-- 关联业务：允许业主在小程序发起报修时上传并绑定现场照片，作为工单受理与后续治理的原始证据。

ALTER TABLE t_repair_attachment
    DROP CONSTRAINT IF EXISTS chk_repair_attachment_kind;

ALTER TABLE t_repair_attachment
    ADD CONSTRAINT chk_repair_attachment_kind CHECK (
        attachment_kind IN (
            'OWNER_REPORT_IMAGE', 'INTAKE_ATTACHMENT', 'LOCATION_IMAGE', 'SURVEY_IMAGE', 'SURVEY_VIDEO',
            'QUOTE_DOCUMENT', 'APPROVAL_DOCUMENT', 'SOLITAIRE_SCREENSHOT',
            'GOVERNANCE_SEALED_DOCUMENT'
        )
    );

COMMENT ON COLUMN t_repair_attachment.attachment_kind IS
    'OWNER_REPORT_IMAGE=业主报修现场照片；INTAKE_ATTACHMENT=登记工单附件；LOCATION_IMAGE=位置证据；SURVEY_IMAGE/SURVEY_VIDEO=初勘证据；QUOTE_DOCUMENT=报价原件；APPROVAL_DOCUMENT=物业正式报审文件；SOLITAIRE_SCREENSHOT=微信接龙截图；GOVERNANCE_SEALED_DOCUMENT=业委会盖章结果文件';
