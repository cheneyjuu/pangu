-- 关联业务：将小区物业管理模式从前端展示项提升为经审核生效、可追溯的租户运行事实。

ALTER TABLE t_community_registration_application
    ADD COLUMN declared_property_mode VARCHAR(32);

ALTER TABLE t_community_registration_application
    ADD CONSTRAINT chk_community_registration_declared_property_mode CHECK (
        declared_property_mode IS NULL
        OR declared_property_mode IN ('LUMP_SUM', 'FUND_RAISING', 'TRUST')
    );

COMMENT ON COLUMN t_community_registration_application.declared_property_mode IS
    '注册人申报的互斥物业管理模式；历史申请允许为空，新的提交申请必须声明';

ALTER TABLE t_tenant_community
    ADD COLUMN property_mode VARCHAR(32),
    ADD COLUMN property_mode_history JSONB NOT NULL DEFAULT '[]'::JSONB;

ALTER TABLE t_tenant_community
    ADD CONSTRAINT chk_tenant_community_property_mode CHECK (
        property_mode IS NULL OR property_mode IN ('LUMP_SUM', 'FUND_RAISING', 'TRUST')
    ),
    ADD CONSTRAINT chk_tenant_community_property_mode_history CHECK (
        jsonb_typeof(property_mode_history) = 'array'
    );

COMMENT ON COLUMN t_tenant_community.property_mode IS
    '当前已生效的互斥物业管理模式；历史租户未配置时为空，禁止以信托制等前端默认值替代';
COMMENT ON COLUMN t_tenant_community.property_mode_history IS
    '物业管理模式生效与变更的不可变审计摘要；完整材料和审核过程由模式变更申请记录保存';
