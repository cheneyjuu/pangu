-- Phase 74: enforce ELECTION subject dual-review role separation.
--
-- V3.6 initially granted committee review to GOV_SUPER_ADMIN as well as
-- COMMUNITY_ADMIN. That allowed a street account to perform both review stages
-- if the permission matrix was used alone. Committee review is now reserved for
-- COMMUNITY_ADMIN; street review and handover confirmation remain reserved for
-- GOV_SUPER_ADMIN.

DELETE FROM sys_role_permission
WHERE role_id = 1
  AND permission_key = 'voting:subject:review:committee';
