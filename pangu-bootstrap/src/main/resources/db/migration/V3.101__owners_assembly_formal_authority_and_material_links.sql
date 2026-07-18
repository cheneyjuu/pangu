-- 关联业务：限制业主大会正式环节由主任或副主任办理，并保存已锁定表决包实际选用的公开材料。

CREATE TABLE t_owners_assembly_package_material (
    package_id BIGINT NOT NULL REFERENCES t_owners_assembly_package(package_id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES t_tenant_community(tenant_id),
    material_id BIGINT NOT NULL REFERENCES t_owners_assembly_material(material_id) ON DELETE RESTRICT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_owners_assembly_package_material PRIMARY KEY (package_id, material_id)
);

CREATE INDEX idx_owners_assembly_package_material_package
    ON t_owners_assembly_package_material(package_id, tenant_id, material_id);

COMMENT ON TABLE t_owners_assembly_package_material IS
    '正式表决包锁定时实际选用的公告、方案附件和盖章纸质选票模板；C 端只能披露此清单中的材料';

INSERT INTO sys_permission (
    permission_key, description, permission_group, allowed_dept_categories, is_legal_redline
) VALUES (
    'owners-assembly:formal:manage',
    '以主任或副主任身份确认安排、发布公示、开启纸质投票并形成业主大会结果',
    'VOTING', 'B', 1
)
ON CONFLICT (permission_key) DO UPDATE SET
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    allowed_dept_categories = EXCLUDED.allowed_dept_categories,
    is_legal_redline = EXCLUDED.is_legal_redline;

-- 副主任在现有身份模型中使用 COMMITTEE_MEMBER 角色；服务层还会复核其当前届期职务。
INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role.role_id, 'owners-assembly:formal:manage'
FROM sys_role role
WHERE role.role_key IN ('COMMITTEE_DIRECTOR', 'COMMITTEE_MEMBER')
ON CONFLICT (role_id, permission_key) DO NOTHING;
