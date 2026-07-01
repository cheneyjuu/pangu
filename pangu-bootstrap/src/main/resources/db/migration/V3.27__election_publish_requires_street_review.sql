-- V3.5 granted GOV_SUPER_ADMIN the generic voting:subject:publish permission
-- to model ELECTION final publication. The ELECTION flow now publishes only
-- through voting:subject:review:street, which also appends review_history.
-- Keep voting:subject:publish for GENERAL/MAJOR daily publication roles, but
-- remove the street role's direct publish bypass.
DELETE FROM sys_role_permission
WHERE role_id = 1
  AND permission_key = 'voting:subject:publish';
