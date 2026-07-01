-- ============================================================================
-- V3.5: 选举闭环角色矩阵对齐（梯度 A）
-- ============================================================================
--
-- 依据：docs/选举闭环对齐路线图.md §3
--
-- 业务目标：
--   1) 新增 GOV_OPERATOR 角色（基层经办员，G 端）——选举立项的唯一执行人；
--   2) 街道办 GOV_SUPER_ADMIN 补 voting:subject:publish——选举公示的全网唯一权限拥有者；
--   3) seed 一个 GOV_OPERATOR 测试账号 13800000005 吴经办员（dept_id=101 求是居委会）。
--
-- 边界（service 层另行护栏，本迁移不涉及）：
--   - 业委会 5/6/7 保留 voting:subject:create 但 ELECTION 类型立项由 service 层拒绝
--     （护栏在 ProposalLifecycleService.propose；详见路线图 §3.2 A.2）；
--   - 业委会主任 5 保留 voting:subject:publish 但 ELECTION 类型公示由 service 层拒绝
--     （护栏在 ProposalLifecycleService.publish；详见路线图 §3.2 A.3）；
--   - 一般决议（GENERAL/MAJOR）的立项/公示权限矩阵保持现状，不动。
--
-- 校验：
--   1) sys_role 出现 role_id=14 GOV_OPERATOR；
--   2) GOV_SUPER_ADMIN (1) 出现 voting:subject:publish；
--   3) 13800000005 登录可获得 GOV_OPERATOR 角色 + ALL_COMMUNITY 数据范围。
-- ============================================================================

-- ===== 1. 新增 GOV_OPERATOR 角色 =====
INSERT INTO sys_role (role_id, role_name, role_key, allowed_dept_category,
                     fixed_data_scope, default_data_scope, is_system) VALUES
    (14, '基层经办员', 'GOV_OPERATOR', 'G', 'ALL_COMMUNITY', 'ALL_COMMUNITY', 1)
ON CONFLICT (role_id) DO NOTHING;

-- ===== 2. GOV_OPERATOR 授予最小可用能力点 =====
-- 选举立项（type=ELECTION 在 service 层强校验）/ 候选人提名（业委会非利益相关方）/
-- 议题读（管理端工作台）/ Waiver 提交（党员比例放宽申请）/ 切卡
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (14, 'voting:subject:create'),
    (14, 'voting:subject:audit'),
    (14, 'candidate:nominate'),
    (14, 'waiver:submit'),
    (14, 'identity:switch')
ON CONFLICT (role_id, permission_key) DO NOTHING;

-- ===== 3. 街道办 GOV_SUPER_ADMIN (1) 补 voting:subject:publish =====
-- 设计稿：街道办专管员是全网唯一【发布选举公示】权（service 层强校验仅 ELECTION 时启用）。
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
    (1, 'voting:subject:publish')
ON CONFLICT (role_id, permission_key) DO NOTHING;

-- ===== 4. seed 测试账号：13800000005 吴经办员 → GOV_OPERATOR =====
-- t_account：自然人母表
INSERT INTO t_account (account_id, phone, real_name, id_card_encrypted,
                      real_name_verified, status) VALUES
    (999805, '13800000005', 'MOCK_吴经办员', 'MOCK_ID_999805', 1, 1)
ON CONFLICT (account_id) DO NOTHING;

-- sys_user：G 端工作账号，挂载在求是居委会（dept_id=101，dept_type=2）下
INSERT INTO sys_user (user_id, account_id, dept_id, user_name, nick_name, status) VALUES
    (800005, 999805, 101, 'wu_operator', '吴经办员', '0')
ON CONFLICT (user_id) DO NOTHING;

-- sys_user_role：唯一角色绑定（M1 模型一人一岗）
INSERT INTO sys_user_role (user_id, role_id, effective_data_scope, granted_by) VALUES
    (800005, 14, 'ALL_COMMUNITY', 800001)
ON CONFLICT (user_id) DO NOTHING;

-- t_account.last_active_identity_id：让首次登录默认进入 sys_user 分身
UPDATE t_account SET last_active_identity_id = 800005,
                    last_active_identity_type = 'SYS_USER'
WHERE account_id = 999805;
