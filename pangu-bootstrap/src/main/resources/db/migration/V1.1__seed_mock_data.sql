-- ===================================================================
-- V1.1 — 求是小区 mock 数据 (tenant_id=10001)
-- 详见：M1权限体系重构设计.md §附录C
-- 测试隔离：本 seed 使用 tenant_id=10001 + account/user/dept 主键 100xxx 段；
-- 集成测试使用 99001-99003 段，互不干扰。
-- ===================================================================

-- -------------------------------------------------------------------
-- 1. t_account（自然人主体）
--    real_name / id_card_encrypted 用 'MOCK_*' 占位，开发阶段免 SM4 解密；
--    生产由 Sm4EncryptTypeHandler 自动加密。
-- -------------------------------------------------------------------
INSERT INTO t_account (account_id, phone, real_name, id_card_encrypted, real_name_verified, status) VALUES
    -- G 端工作账号
    (999801, '13800000001', 'MOCK_王街道',  'MOCK_ID_999801', 1, 1),
    (999802, '13800000002', 'MOCK_李书记',  'MOCK_ID_999802', 1, 1),
    (999803, '13800000003', 'MOCK_刘主任',  'MOCK_ID_999803', 1, 1),
    (999804, '13800000004', 'MOCK_陈网格员','MOCK_ID_999804', 1, 1),
    -- B 端业委会 / 业主代表 / 志愿者
    (999811, '13800000011', 'MOCK_周主任',  'MOCK_ID_999811', 1, 1),
    (999813, '13800000013', 'MOCK_钱委员',  'MOCK_ID_999813', 1, 1),
    (999812, '13800000012', 'MOCK_张三',    'MOCK_ID_999812', 1, 1),  -- 同时是业主代表 + C 端业主
    (999814, '13800000014', 'MOCK_孙志愿者','MOCK_ID_999814', 1, 1),
    -- S 端物业
    (999821, '13800000021', 'MOCK_赵经理',  'MOCK_ID_999821', 1, 1),
    (999822, '13800000022', 'MOCK_朱员工',  'MOCK_ID_999822', 1, 1),
    -- 纯 C 端业主
    (999913, '13800000113', 'MOCK_李四',    'MOCK_ID_999913', 1, 1);
SELECT setval('t_account_account_id_seq', 1000000, false);

-- -------------------------------------------------------------------
-- 2. sys_dept（部门树）
--    1   ─ 街道办（tenant_id=NULL，跨租户根，G）
--    105 ── 求是党组织（tenant_id=10001, G, 党建引领核心）
--    101 ─── 求是居委会（tenant_id=10001, G）
--    104 ──── 求是第一网格（tenant_id=10001, G）
--    103 ─── 求是业委会（tenant_id=10001, B）
--    106 ──── 求是志愿队（tenant_id=10001, B）
--    110 ─── 求是业主代表团（tenant_id=10001, B，独立于业委会，挂党组织下）
--    102 ─── 求是物业项目部（tenant_id=10001, S）
-- -------------------------------------------------------------------
INSERT INTO sys_dept (dept_id, parent_id, ancestors, dept_name, dept_type, dept_category, tenant_id, order_num) VALUES
    (1,   NULL, '',            '某市某区某街道办',  1,  'G', NULL,  10),
    (105, 1,    '1',           '求是党组织',        6,  'G', 10001, 20),
    (101, 105,  '1,105',       '求是居委会',        2,  'G', 10001, 30),
    (104, 101,  '1,105,101',   '求是第一网格',      5,  'G', 10001, 31),
    (103, 105,  '1,105',       '求是业委会',        4,  'B', 10001, 40),
    (106, 103,  '1,105,103',   '求是志愿队',        10, 'B', 10001, 41),
    (110, 105,  '1,105',       '求是业主代表团',    11, 'B', 10001, 42),
    (102, 105,  '1,105',       '求是物业项目部',    3,  'S', 10001, 50);
SELECT setval('sys_dept_dept_id_seq', 1000, false);

-- -------------------------------------------------------------------
-- 3. sys_user（管理端工作账号 / 影子分身）
-- -------------------------------------------------------------------
INSERT INTO sys_user (user_id, account_id, dept_id, user_name, nick_name, status) VALUES
    (800001, 999801, 1,   'wang_street',     '王街道',     '0'),
    (800002, 999802, 105, 'li_secretary',    '李书记',     '0'),
    (800003, 999803, 101, 'liu_director',    '刘主任',     '0'),
    (800004, 999804, 104, 'chen_grid',       '陈网格员',   '0'),
    (800101, 999811, 103, 'zhou_director',   '周主任',     '0'),
    (800103, 999813, 103, 'qian_member',     '钱委员',     '0'),
    (800102, 999812, 110, 'zhang_san_rep',   '张三(代表)', '0'),
    (800104, 999814, 106, 'sun_volunteer',   '孙志愿者',   '0'),
    (800201, 999821, 102, 'zhao_manager',    '赵经理',     '0'),
    (800202, 999822, 102, 'zhu_staff',       '朱员工',     '0');
SELECT setval('sys_user_user_id_seq', 1000000, false);

-- -------------------------------------------------------------------
-- 4. c_user（C 端业主身份）
--    张三既是业主代表（sys_user=800102）又是业主自然人（c_user=70001）
--    李四纯业主，无 sys_user 分身
-- -------------------------------------------------------------------
INSERT INTO c_user (uid, account_id, auth_level, last_active_tenant_id) VALUES
    (70001, 999812, 3, 10001),
    (70002, 999913, 2, 10001);
SELECT setval('c_user_uid_seq', 100000, false);

-- -------------------------------------------------------------------
-- 5. t_account.last_active_identity 回填（指向默认登录身份）
-- -------------------------------------------------------------------
UPDATE t_account SET last_active_identity_id = 800001, last_active_identity_type = 'SYS_USER' WHERE account_id = 999801;
UPDATE t_account SET last_active_identity_id = 800002, last_active_identity_type = 'SYS_USER' WHERE account_id = 999802;
UPDATE t_account SET last_active_identity_id = 800003, last_active_identity_type = 'SYS_USER' WHERE account_id = 999803;
UPDATE t_account SET last_active_identity_id = 800004, last_active_identity_type = 'SYS_USER' WHERE account_id = 999804;
UPDATE t_account SET last_active_identity_id = 800101, last_active_identity_type = 'SYS_USER' WHERE account_id = 999811;
UPDATE t_account SET last_active_identity_id = 800103, last_active_identity_type = 'SYS_USER' WHERE account_id = 999813;
UPDATE t_account SET last_active_identity_id = 800102, last_active_identity_type = 'SYS_USER' WHERE account_id = 999812;  -- 张三默认走业主代表分身
UPDATE t_account SET last_active_identity_id = 800104, last_active_identity_type = 'SYS_USER' WHERE account_id = 999814;
UPDATE t_account SET last_active_identity_id = 800201, last_active_identity_type = 'SYS_USER' WHERE account_id = 999821;
UPDATE t_account SET last_active_identity_id = 800202, last_active_identity_type = 'SYS_USER' WHERE account_id = 999822;
UPDATE t_account SET last_active_identity_id = 70002,  last_active_identity_type = 'C_USER'   WHERE account_id = 999913;

-- -------------------------------------------------------------------
-- 6. c_owner_property（业主房产）— 求是 3 栋 101（张三）/ 1 栋 502 + 5 栋 201（李四）
-- -------------------------------------------------------------------
INSERT INTO c_owner_property (uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status) VALUES
    (70001, 10001, 30001, 30001101, 100.00, 1, 1),  -- 张三 3 栋 101
    (70002, 10001, 30002, 30002502,  85.00, 1, 1),  -- 李四 1 栋 502
    (70002, 10001, 30005, 30005201,  90.00, 1, 1);  -- 李四 5 栋 201
