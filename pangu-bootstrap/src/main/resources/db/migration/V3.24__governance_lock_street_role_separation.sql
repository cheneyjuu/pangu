-- V2.5 initially granted lock:unlock:street to GOV_SUPER_ADMIN and
-- COMMUNITY_ADMIN. Governance lock dual sign requires committee initial sign
-- by COMMITTEE_DIRECTOR and street final sign by GOV_SUPER_ADMIN only.
-- Service-level role guards also protect against future permission drift.

DELETE FROM sys_role_permission
WHERE role_id = 2
  AND permission_key = 'lock:unlock:street';
