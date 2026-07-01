-- V1.4 seeded fund:disclosure:publish as a broad FUND permission. V2.7
-- replaced the M2-3 disclosure flow with the scoped disclosure:* permission
-- family, and no controller uses the legacy key. Remove it from role grants
-- and the permission catalog so RBAC management cannot reassign a stale
-- capability.

DELETE FROM sys_role_permission
WHERE permission_key = 'fund:disclosure:publish';

DELETE FROM sys_permission
WHERE permission_key = 'fund:disclosure:publish';
