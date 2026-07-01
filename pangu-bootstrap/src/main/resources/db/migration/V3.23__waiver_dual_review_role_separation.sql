-- Phase 75: enforce Waiver dual-review role separation.
--
-- V1.4 granted waiver:approve:committee to GOV_SUPER_ADMIN and COMMUNITY_ADMIN.
-- Waiver committee review belongs to COMMUNITY_ADMIN; street review remains with
-- GOV_SUPER_ADMIN. Service-level role guards also protect against future
-- permission misconfiguration.

DELETE FROM sys_role_permission
WHERE role_id = 1
  AND permission_key = 'waiver:approve:committee';
