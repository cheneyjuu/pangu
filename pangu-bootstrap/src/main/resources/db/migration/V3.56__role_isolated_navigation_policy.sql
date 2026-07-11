-- 外部协作角色可启用隔离导航：启用后只下发 sys_role_menu 显式绑定的菜单，
-- 不继承 required_* 为空的内部通用菜单。
ALTER TABLE sys_role
    ADD COLUMN navigation_isolated SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE sys_role
    ADD CONSTRAINT chk_role_navigation_isolated CHECK (navigation_isolated IN (0, 1));

UPDATE sys_role
SET navigation_isolated = 1
WHERE role_key IN ('SERVICE_PROVIDER_MANAGER', 'SERVICE_PROVIDER_STAFF');

COMMENT ON COLUMN sys_role.navigation_isolated IS
    '1=仅下发 sys_role_menu 显式绑定菜单，适用于供应商等外部协作角色';
