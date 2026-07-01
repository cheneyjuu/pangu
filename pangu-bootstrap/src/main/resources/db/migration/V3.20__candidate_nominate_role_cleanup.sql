-- Phase 70 follow-up: ELECTION candidate nomination is executed only by G-side GOV_OPERATOR.
--
-- Older V1.4 permissions granted candidate:nominate to GRID_OPERATOR / PARTY_SECRETARY /
-- COMMITTEE_DIRECTOR / COMMITTEE_MEMBER / OWNER_REPRESENTATIVE. Service-level guards now
-- enforce roleKey=GOV_OPERATOR + dept_type IN (2,5); this migration aligns the permission
-- matrix so menus and pre-authorize checks no longer advertise obsolete write access.

DELETE FROM sys_role_permission
WHERE permission_key = 'candidate:nominate'
  AND role_id <> 14;

INSERT INTO sys_role_permission (role_id, permission_key)
VALUES (14, 'candidate:nominate')
ON CONFLICT (role_id, permission_key) DO NOTHING;
