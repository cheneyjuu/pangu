-- V3.2 kept GOV_SUPER_ADMIN as an override for both candidate review gates.
-- The election closed-loop now keeps normal candidate review split by role:
-- PARTY_SECRETARY performs party pre-review, COMMUNITY_ADMIN performs committee
-- qualification review. Service-level guards also protect future permission drift.

DELETE FROM sys_role_permission
WHERE role_id = 1
  AND permission_key IN ('candidate:review:party', 'candidate:approve');
