-- V3.34: 网格员可查看矛盾调解工作台，但只能看到关联房产落在本网格楼栋范围内的异议。

ALTER TABLE t_owner_dispute
    ADD COLUMN related_property_opid BIGINT;

CREATE INDEX idx_dispute_related_property
    ON t_owner_dispute(related_property_opid)
    WHERE related_property_opid IS NOT NULL;

COMMENT ON COLUMN t_owner_dispute.related_property_opid IS
    '异议关联房产 opid；网格员矛盾调解数据范围按该房产 tenant_id + building_id 过滤';

CREATE OR REPLACE FUNCTION fn_owner_dispute_property_scope() RETURNS TRIGGER AS $$
DECLARE
    v_owner_uid BIGINT;
    v_tenant_id BIGINT;
BEGIN
    IF NEW.related_property_opid IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT uid, tenant_id
      INTO v_owner_uid, v_tenant_id
      FROM c_owner_property
     WHERE opid = NEW.related_property_opid
     LIMIT 1;

    IF v_owner_uid IS NULL THEN
        RAISE EXCEPTION
            '[trigger dispute property] 关联房产不存在，related_property_opid=%',
            NEW.related_property_opid;
    END IF;

    IF v_owner_uid <> NEW.raised_by_owner_id OR v_tenant_id <> NEW.tenant_id THEN
        RAISE EXCEPTION
            '[trigger dispute property] 关联房产必须属于当前业主与租户，tenant_id=%, raised_by_owner_id=%, related_property_opid=%',
            NEW.tenant_id, NEW.raised_by_owner_id, NEW.related_property_opid;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_owner_dispute_property_scope
    BEFORE INSERT OR UPDATE ON t_owner_dispute
    FOR EACH ROW EXECUTE FUNCTION fn_owner_dispute_property_scope();

INSERT INTO sys_role_permission (role_id, permission_key)
SELECT role_id, 'dispute:audit'
FROM sys_role
WHERE role_key = 'GRID_MEMBER'
ON CONFLICT (role_id, permission_key) DO NOTHING;
