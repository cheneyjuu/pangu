-- 关联业务：业主委员会换届由街镇、居委会、居民区党组织及业主自治组织协同办理，
-- 物业服务企业可以提供资料与服务配合，但物业经理工作身份不负责换届流程管理。

UPDATE sys_menu
SET required_permission = NULL,
    required_any_permissions = NULL,
    required_role_keys =
        'GOV_SUPER_ADMIN,GOV_OPERATOR,COMMUNITY_ADMIN,PARTY_SECRETARY,COMMITTEE_DIRECTOR,COMMITTEE_MEMBER,COMMITTEE_SECRETARY'
WHERE route_id = 'term-management';

-- V3.39 曾依据 waiver:read 生成静态角色菜单授权；同步清理不符合新职责边界的历史授权，
-- 避免 sys_role_menu 的显式授权绕过 required_role_keys 动态规则。
DELETE FROM sys_role_menu role_menu
USING sys_role role, sys_menu menu
WHERE role_menu.role_id = role.role_id
  AND role_menu.menu_id = menu.menu_id
  AND menu.route_id = 'term-management'
  AND role.role_key NOT IN (
      'GOV_SUPER_ADMIN',
      'GOV_OPERATOR',
      'COMMUNITY_ADMIN',
      'PARTY_SECRETARY',
      'COMMITTEE_DIRECTOR',
      'COMMITTEE_MEMBER',
      'COMMITTEE_SECRETARY'
  );
