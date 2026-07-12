ALTER TABLE t_tenant_community
    ADD COLUMN IF NOT EXISTS building_repair_default_decision_channel VARCHAR(16) NOT NULL DEFAULT 'WECHAT';

ALTER TABLE t_tenant_community
    DROP CONSTRAINT IF EXISTS chk_tenant_building_repair_default_decision_channel;

ALTER TABLE t_tenant_community
    ADD CONSTRAINT chk_tenant_building_repair_default_decision_channel
        CHECK (building_repair_default_decision_channel IN ('ONLINE', 'WECHAT'));

COMMENT ON COLUMN t_tenant_community.building_repair_default_decision_channel
    IS '楼栋维修新表决的社区默认渠道；具体工单可在启动表决前覆盖，启动后渠道锁定';
