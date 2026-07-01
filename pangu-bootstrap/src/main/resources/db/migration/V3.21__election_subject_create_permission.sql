-- Phase 73: split ELECTION subject creation from the generic subject create permission.
--
-- Generic voting:subject:create remains for GENERAL/MAJOR daily proposals. ELECTION
-- proposal and submit-for-review are legal-redline write paths and are only granted
-- to the G-side GOV_OPERATOR role; service-level role/dept_type guards remain as
-- the final defense.

INSERT INTO sys_permission (
    permission_key,
    description,
    permission_group,
    allowed_dept_categories,
    is_legal_redline
) VALUES (
    'voting:subject:create:election',
    '创建/提交选举议题',
    'VOTING',
    'G',
    0
) ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_key)
VALUES (14, 'voting:subject:create:election')
ON CONFLICT (role_id, permission_key) DO NOTHING;
