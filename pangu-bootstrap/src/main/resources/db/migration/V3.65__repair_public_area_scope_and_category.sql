-- V3.65: 公共报修位置范围与维修专业分类规范化。

ALTER TABLE t_repair_work_order
    ADD COLUMN IF NOT EXISTS public_area_scope VARCHAR(16);

UPDATE t_repair_work_order
SET public_area_scope = 'BUILDING'
WHERE space_scope = 'PUBLIC'
  AND building_id IS NOT NULL
  AND public_area_scope IS NULL;

UPDATE t_repair_work_order
SET category = CASE category
    WHEN 'ELECTRIC' THEN 'ELECTRICAL'
    WHEN 'FIRE' THEN 'FIRE_PROTECTION'
    WHEN 'PUBLIC_PIPE' THEN 'PLUMBING'
    WHEN 'WALL_LEAK' THEN 'WATERPROOFING'
    WHEN 'PUBLIC_FACILITY' THEN 'PUBLIC_AREA_FACILITY'
    ELSE category
END
WHERE category IN ('ELECTRIC', 'FIRE', 'PUBLIC_PIPE', 'WALL_LEAK', 'PUBLIC_FACILITY');

UPDATE t_supplier_tenant_relation
SET service_category = CASE service_category
    WHEN 'ELECTRIC' THEN 'ELECTRICAL'
    WHEN 'FIRE' THEN 'FIRE_PROTECTION'
    WHEN 'PUBLIC_PIPE' THEN 'PLUMBING'
    WHEN 'WALL_LEAK' THEN 'WATERPROOFING'
    WHEN 'PUBLIC_FACILITY' THEN 'PUBLIC_AREA_FACILITY'
    ELSE service_category
END
WHERE service_category IN ('ELECTRIC', 'FIRE', 'PUBLIC_PIPE', 'WALL_LEAK', 'PUBLIC_FACILITY');

ALTER TABLE t_repair_work_order
    DROP CONSTRAINT IF EXISTS chk_repair_public_area_scope;

ALTER TABLE t_repair_work_order
    ADD CONSTRAINT chk_repair_public_area_scope CHECK (
        public_area_scope IS NULL
        OR (
            space_scope = 'PUBLIC'
            AND public_area_scope IN ('BUILDING', 'COMMUNITY')
        )
    );

COMMENT ON COLUMN t_repair_work_order.public_area_scope IS
    '公共维修位置范围：BUILDING=楼栋公共部位，COMMUNITY=小区公共区域；NULL 表示私有维修或尚待现场确定';
