-- ===================================================================
-- 1. C端用户核心表 (c_user)
-- ===================================================================
CREATE TABLE c_user (
    uid BIGSERIAL PRIMARY KEY,
    phone VARCHAR(11) NOT NULL,
    real_name VARCHAR(128),
    id_card_type SMALLINT NOT NULL DEFAULT 1,
    id_card_no VARCHAR(128),
    auth_level SMALLINT NOT NULL DEFAULT 1,
    face_status SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uidx_c_user_phone ON c_user(phone);
COMMENT ON TABLE c_user IS 'C端自然人用户核心表';
COMMENT ON COLUMN c_user.uid IS '全局唯一的自然人ID';
COMMENT ON COLUMN c_user.phone IS '用户手机号（唯一登录凭证）';
COMMENT ON COLUMN c_user.real_name IS '真实姓名（国密SM4加密密文）';
COMMENT ON COLUMN c_user.id_card_type IS '证件类型：1-身份证, 2-港澳通行证, 3-涉外护照';
COMMENT ON COLUMN c_user.id_card_no IS '证件号码（国密SM4加密密文）';
COMMENT ON COLUMN c_user.auth_level IS '认证等级：1-L1基础绑定, 3-L3人脸识别认证, 4-L4司法级法人认证';
COMMENT ON COLUMN c_user.face_status IS '活体特征状态：0-未采集, 1-已采集';

-- ===================================================================
-- 2. C端业主房产映射关系表 (c_owner_property)
-- ===================================================================
CREATE TABLE c_owner_property (
    opid BIGSERIAL PRIMARY KEY,
    uid BIGINT NOT NULL REFERENCES c_user(uid),
    tenant_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    build_area DECIMAL(10,2) NOT NULL,
    is_joint_ownership SMALLINT NOT NULL DEFAULT 0,
    is_voting_delegate SMALLINT NOT NULL DEFAULT 1,
    account_status SMALLINT NOT NULL DEFAULT 1
);

CREATE INDEX idx_c_owner_property_uid ON c_owner_property(uid);
CREATE INDEX idx_c_owner_property_tenant ON c_owner_property(tenant_id);
CREATE UNIQUE INDEX uidx_tenant_room_owner ON c_owner_property(tenant_id, room_id, uid);

COMMENT ON TABLE c_owner_property IS 'C端业主房产绑定关系表';
COMMENT ON COLUMN c_owner_property.opid IS '业主房产实体ID';
COMMENT ON COLUMN c_owner_property.uid IS '自然人ID';
COMMENT ON COLUMN c_owner_property.tenant_id IS '租户ID（关联特定小区SaaS实例）';
COMMENT ON COLUMN c_owner_property.build_area IS '物理房产专有建筑面积';
COMMENT ON COLUMN c_owner_property.is_joint_ownership IS '是否共有产权：0-独立产权, 1-共有产权';
COMMENT ON COLUMN c_owner_property.is_voting_delegate IS '是否指定行使表决权的代表：0-非代表, 1-代表账号';
COMMENT ON COLUMN c_owner_property.account_status IS '状态：1-正常, 2-欠费挂起, 3-冻结';

-- ===================================================================
-- 3. 组织机构部门树表 (sys_dept)
-- ===================================================================
CREATE TABLE sys_dept (
    dept_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    ancestors VARCHAR(500) NOT NULL DEFAULT '',
    dept_name VARCHAR(50) NOT NULL,
    dept_type SMALLINT NOT NULL,
    order_num INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_sys_dept_tenant ON sys_dept(tenant_id);

COMMENT ON TABLE sys_dept IS 'B/G端组织机构树表';
COMMENT ON COLUMN sys_dept.dept_id IS '部门组织ID';
COMMENT ON COLUMN sys_dept.tenant_id IS 'SaaS租户ID';
COMMENT ON COLUMN sys_dept.parent_id IS '父级部门ID';
COMMENT ON COLUMN sys_dept.ancestors IS '祖级列表路径（格式如 0,100,105）';
COMMENT ON COLUMN sys_dept.dept_name IS '组织节点名称（如街道办、物业部）';
COMMENT ON COLUMN sys_dept.dept_type IS '节点类型：1-街道办, 2-居委会, 3-物业公司, 4-业委会, 5-网格片区';

-- ===================================================================
-- 4. B/G端管理用户表 (sys_user)
-- ===================================================================
CREATE TABLE sys_user (
    user_id BIGSERIAL PRIMARY KEY,
    dept_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    nick_name VARCHAR(50) NOT NULL,
    phone VARCHAR(11),
    user_type SMALLINT NOT NULL DEFAULT 1,
    status CHAR(1) NOT NULL DEFAULT '0',
    uid BIGINT REFERENCES c_user(uid)
);

CREATE UNIQUE INDEX uidx_sys_user_username ON sys_user(username);
CREATE INDEX idx_sys_user_dept ON sys_user(dept_id);
CREATE INDEX idx_sys_user_uid ON sys_user(uid);

COMMENT ON TABLE sys_user IS 'B/G端管理端用户表';
COMMENT ON COLUMN sys_user.user_id IS '管理端用户ID';
COMMENT ON COLUMN sys_user.dept_id IS '所属部门组织ID';
COMMENT ON COLUMN sys_user.username IS '登录用户名';
COMMENT ON COLUMN sys_user.password IS '加密后的登录密码';
COMMENT ON COLUMN sys_user.user_type IS '用户类型：1-物业员工, 2-业委会委员, 3-居委会网格员, 4-志愿者';
COMMENT ON COLUMN sys_user.status IS '账号状态：0-正常, 1-停用';
COMMENT ON COLUMN sys_user.uid IS '互联的C端业主自然人ID';

-- ===================================================================
-- 5. 动态角色表 (sys_role)
-- ===================================================================
CREATE TABLE sys_role (
    role_id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    role_key VARCHAR(50) NOT NULL,
    data_scope CHAR(1) NOT NULL DEFAULT '4',
    status CHAR(1) NOT NULL DEFAULT '0'
);

COMMENT ON TABLE sys_role IS 'B/G端动态角色表';
COMMENT ON COLUMN sys_role.data_scope IS '数据权限范围：1-全部, 2-自定部门, 3-本部门, 4-本部门及以下, 5-仅本人, 6-自定义指定楼栋';

-- ===================================================================
-- 6. 网格员/志愿者-楼栋自定义数据范围关联表 (sys_user_building)
-- ===================================================================
CREATE TABLE sys_user_building (
    user_id BIGINT NOT NULL REFERENCES sys_user(user_id) ON DELETE CASCADE,
    building_id BIGINT NOT NULL,
    assigned_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, building_id)
);

COMMENT ON TABLE sys_user_building IS '网格员与管辖楼栋关联关系表';
