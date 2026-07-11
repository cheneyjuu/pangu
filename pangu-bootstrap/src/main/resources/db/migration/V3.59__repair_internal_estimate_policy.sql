-- V3.59: separate the property company's internal estimate from supplier-visible ceiling pricing.

ALTER TABLE t_tenant_community
    ADD COLUMN IF NOT EXISTS repair_estimate_required SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE t_tenant_community
    ADD CONSTRAINT chk_tenant_repair_estimate_required
        CHECK (repair_estimate_required IN (0, 1));

COMMENT ON COLUMN t_tenant_community.repair_estimate_required
    IS 'Whether an internal property estimate is required before supplier invitation';

ALTER TABLE t_repair_work_order
    ADD COLUMN IF NOT EXISTS public_ceiling_price NUMERIC(14, 2);

ALTER TABLE t_repair_work_order
    ADD CONSTRAINT chk_repair_public_ceiling_price
        CHECK (public_ceiling_price IS NULL OR public_ceiling_price > 0);

COMMENT ON COLUMN t_repair_work_order.plan_budget
    IS 'Property internal estimate; never exposed through supplier APIs';
COMMENT ON COLUMN t_repair_work_order.public_ceiling_price
    IS 'Optional maximum price explicitly disclosed to invited suppliers';
