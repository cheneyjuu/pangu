-- 关联业务：为每次维修正式表决锁定实际使用的纸质表决票模板原件及内容摘要。

ALTER TABLE t_repair_project_voting
    ADD COLUMN paper_ballot_template_attachment_id BIGINT
        REFERENCES t_repair_project_attachment(attachment_id),
    ADD COLUMN paper_ballot_template_hash CHAR(64);

ALTER TABLE t_repair_project_voting
    ADD CONSTRAINT chk_repair_voting_template_pair CHECK (
        (paper_ballot_template_attachment_id IS NULL AND paper_ballot_template_hash IS NULL)
        OR (paper_ballot_template_attachment_id IS NOT NULL
            AND paper_ballot_template_hash ~ '^[0-9a-f]{64}$')
    );

COMMENT ON COLUMN t_repair_project_voting.paper_ballot_template_attachment_id IS
    '本次表决发布前选定的纸质表决票空白模板原件；历史记录可为空';
COMMENT ON COLUMN t_repair_project_voting.paper_ballot_template_hash IS
    '本次纸质表决票模板原件 SHA-256；纸票回收和录入均以此摘要核对版本';
