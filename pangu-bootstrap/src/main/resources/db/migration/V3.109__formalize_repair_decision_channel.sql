-- 关联业务：新发起的维修事项表决必须逐户记录记名选择；历史表决记录仍按原渠道保留。
UPDATE t_tenant_community
SET building_repair_default_decision_channel = 'ONLINE',
    update_time = CURRENT_TIMESTAMP
WHERE building_repair_default_decision_channel <> 'ONLINE';

ALTER TABLE t_tenant_community
    ALTER COLUMN building_repair_default_decision_channel SET DEFAULT 'ONLINE';

ALTER TABLE t_tenant_community
    DROP CONSTRAINT IF EXISTS chk_tenant_building_repair_default_decision_channel;

ALTER TABLE t_tenant_community
    ADD CONSTRAINT chk_tenant_building_repair_default_decision_channel
        CHECK (building_repair_default_decision_channel = 'ONLINE');

COMMENT ON COLUMN t_tenant_community.building_repair_default_decision_channel
    IS '新发起维修事项的系统内表决收集方式；当前仅支持逐户记名的线上实名投票';
