-- 关联业务：修复已开通小区的业委会注册人仍默认进入 C 端身份，导致管理端菜单权限为空的问题。
-- 审核通过且已创建业委会工作身份时，只覆盖空值或遗留的 C_USER 默认身份；已主动切换到工作身份的账号不受影响。

UPDATE t_account account
SET last_active_identity_id = workspace.applicant_work_user_id,
    last_active_identity_type = 'SYS_USER',
    update_time = CURRENT_TIMESTAMP
FROM t_community_onboarding_workspace workspace
JOIN t_community_registration_application application
    ON application.application_id = workspace.application_id
WHERE account.account_id = application.applicant_account_id
  AND application.status = 'APPROVED'
  AND application.claimed_identity IN (
      'COMMITTEE_DIRECTOR',
      'COMMITTEE_VICE_DIRECTOR',
      'COMMITTEE_MEMBER'
  )
  AND workspace.applicant_work_user_id IS NOT NULL
  AND (
      account.last_active_identity_id IS NULL
      OR account.last_active_identity_type = 'C_USER'
  );
