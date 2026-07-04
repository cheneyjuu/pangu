ALTER TABLE t_voting_subject
    ADD COLUMN IF NOT EXISTS content_html TEXT;

COMMENT ON COLUMN t_voting_subject.content_html IS
    '议题正文，存储小程序 RichText 友好的受限 HTML；GENERAL/MAJOR 立项必填由 application 层校验';
