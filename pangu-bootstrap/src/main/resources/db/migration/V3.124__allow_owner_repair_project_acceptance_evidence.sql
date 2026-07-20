-- 关联业务：允许受影响业主本人上传工程验收现场证据，同时继续以账号标识保留上传人审计记录。
ALTER TABLE t_repair_project_attachment
    ALTER COLUMN uploaded_by_user_id DROP NOT NULL;
