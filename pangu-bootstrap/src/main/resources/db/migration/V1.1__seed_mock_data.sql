-- ===================================================================
-- 导入基础 Mock 种子数据
-- ===================================================================

-- 1. 写入 C端自然人数据 (真实姓名和身份证号均采用国密 SM4 加密后的密文占位，开发阶段可用明文解密匹配)
-- 密码/证书加密密钥使用 0123456789abcdeffedcba9876543210 模拟
INSERT INTO c_user (uid, phone, real_name, id_card_type, id_card_no, auth_level, face_status, create_time)
VALUES 
(101, '13800138000', '4fe67ee27f4da7e743d5483253b27bcf', 1, '5064e4517b6294715f5c404618e4ee64db555be9d21b76426ff0fb5c4cb03e48', 3, 1, CURRENT_TIMESTAMP), -- 张三, L3 活体已认证
(102, '13900139000', 'de251c6b1625946cf6177b94924b17bd', 1, 'e064e4517b6294715f5c404618e4ee64db555be9d21b76426ff0fb5c4cb03e48', 1, 0, CURRENT_TIMESTAMP), -- 李四, L1 基础绑定
(103, '15000150000', 'df172ea27f4da7e743d5483253b27bcf', 1, 'a064e4517b6294715f5c404618e4ee64db555be9d21b76426ff0fb5c4cb03e48', 4, 1, CURRENT_TIMESTAMP); -- 王五(法人代表), L4 级认证

-- 重设 Serial 自增主键起始值
ALTER SEQUENCE c_user_uid_seq RESTART WITH 104;

-- 2. 写入房产专有部分与绑定关系 (租户小区 ID 设为 9001)
-- 物理单元及面积数据
INSERT INTO c_owner_property (opid, uid, tenant_id, building_id, room_id, build_area, is_joint_ownership, is_voting_delegate, account_status)
VALUES 
(5001, 101, 9001, 10001, 10001101, 120.50, 0, 1, 1), -- 张三拥有 1栋101室 (独立产权, 代表, 状态正常)
(5002, 102, 9001, 10001, 10001102, 89.30, 1, 1, 1),  -- 李四拥有 1栋102室 (共有产权, 被推选为投票代表)
(5003, 103, 9001, 10002, 10002201, 350.00, 0, 1, 2); -- 王五拥有 2栋201室 (独立产权, 欠费状态, 用于测试方案C拦截)

ALTER SEQUENCE c_owner_property_opid_seq RESTART WITH 5004;

-- 3. 写入 B/G端管理部门树 (支持五级监管：1-街道办, 2-居委会, 3-物业公司, 4-业委会, 5-网格)
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, dept_type, order_num)
VALUES 
(100, 9001, 0, '0', '西湖街道办事处', 1, 1),
(101, 9001, 100, '0,100', '求是社区居委会', 2, 1),
(102, 9001, 101, '0,100,101', '求是小区物业公司', 3, 2),
(103, 9001, 101, '0,100,101', '求是小区第一届业委会', 4, 3),
(104, 9001, 101, '0,100,101', '求是小区第一网格片区', 5, 4);

ALTER SEQUENCE sys_dept_dept_id_seq RESTART WITH 105;

-- 4. 写入 B/G端管理用户 (密码均为 BCrypt 加密密文，明文为 "admin123")
INSERT INTO sys_user (user_id, dept_id, username, password, nick_name, phone, user_type, status, uid)
VALUES 
(201, 102, 'property_admin', '$2a$10$vEpx.cW7eO2H8i/R.mSveun9zS.GZ0yG1R3M2Wp2qR3KqFvT.5S.G', '物业管理员', '13500135000', 1, '0', NULL), -- 物业管理员
(202, 104, 'grid_wang', '$2a$10$vEpx.cW7eO2H8i/R.mSveun9zS.GZ0yG1R3M2Wp2qR3KqFvT.5S.G', '网格员王小二', '13800138000', 3, '0', 101); -- 网格员王小二，互联张三的自然人UID

ALTER SEQUENCE sys_user_user_id_seq RESTART WITH 203;

-- 5. 写入角色权限 (网格员角色，具有楼栋自定义数据过滤权限 data_scope = '6')
INSERT INTO sys_role (role_id, role_name, role_key, data_scope, status)
VALUES 
(1, '超级管理员', 'admin', '1', '0'),
(2, '求是小区网格员', 'grid_manager', '6', '0');

ALTER SEQUENCE sys_role_role_id_seq RESTART WITH 3;

-- 6. 分配网格员管辖楼栋 (王小二管辖 10001 与 10002 两个物理楼栋)
INSERT INTO sys_user_building (user_id, building_id, assigned_time)
VALUES 
(202, 10001, CURRENT_TIMESTAMP),
(202, 10002, CURRENT_TIMESTAMP);
