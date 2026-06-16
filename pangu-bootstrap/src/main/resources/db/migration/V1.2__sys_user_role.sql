-- ===================================================================
-- 7. B/G端管理用户与角色关联表 (sys_user_role)
-- ===================================================================
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL REFERENCES sys_user(user_id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(role_id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE sys_user_role IS 'B/G端用户与角色关联表';
COMMENT ON COLUMN sys_user_role.user_id IS '管理端用户ID';
COMMENT ON COLUMN sys_user_role.role_id IS '角色ID';

-- 导入关联关系种子数据
INSERT INTO sys_user_role (user_id, role_id)
VALUES
(201, 1), -- 物业管理员(201) -> 超级管理员(1)
(202, 2); -- 网格员王小二(202) -> 求是小区网格员(2)
