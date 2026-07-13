// 关联业务：约束管理端预置角色与真实组织类型的合法绑定关系。
// 关联业务：约束管理端预置角色与真实组织类型的合法绑定关系。
package com.pangu.application.admin;

import java.util.Map;
import java.util.Set;

/**
 * 预置角色与真实组织类型的匹配规则。
 */
final class WorkIdentityRoleRules {

    static final String GRID_MEMBER = "GRID_MEMBER";
    static final String COMMUNITY_ADMIN = "COMMUNITY_ADMIN";

    static final Set<String> BUILDING_SCOPED_ROLES =
            Set.of(GRID_MEMBER, "VOLUNTEER", "OWNER_REPRESENTATIVE");

    static final Set<String> PROPERTY_SERVICE_ROLES =
            Set.of("PROPERTY_MANAGER", "PROPERTY_STAFF");

    private static final Map<String, Set<Integer>> ROLE_DEPT_TYPES = Map.ofEntries(
            Map.entry("GOV_SUPER_ADMIN", Set.of(1)),
            Map.entry("PLATFORM_OPERATOR", Set.of(1)),
            Map.entry(COMMUNITY_ADMIN, Set.of(2)),
            Map.entry("PARTY_SECRETARY", Set.of(6)),
            Map.entry(GRID_MEMBER, Set.of(5)),
            Map.entry("GOV_OPERATOR", Set.of(2, 5)),
            Map.entry("COMMITTEE_DIRECTOR", Set.of(4)),
            Map.entry("COMMITTEE_MEMBER", Set.of(4)),
            Map.entry("COMMITTEE_SECRETARY", Set.of(4)),
            Map.entry("OWNER_REPRESENTATIVE", Set.of(11)),
            Map.entry("VOLUNTEER", Set.of(10)),
            Map.entry("PROPERTY_MANAGER", Set.of(3)),
            Map.entry("PROPERTY_STAFF", Set.of(3)),
            Map.entry("SERVICE_PROVIDER_MANAGER", Set.of(7, 8, 9)),
            Map.entry("SERVICE_PROVIDER_STAFF", Set.of(7, 8, 9)));

    private WorkIdentityRoleRules() {
    }

    static boolean needsBuildings(String roleKey) {
        return BUILDING_SCOPED_ROLES.contains(roleKey);
    }

    static boolean isGridMember(String roleKey) {
        return GRID_MEMBER.equals(roleKey);
    }

    static boolean isPropertyServiceRole(String roleKey) {
        return PROPERTY_SERVICE_ROLES.contains(roleKey);
    }

    static boolean matchesDeptType(String roleKey, Integer deptType) {
        Set<Integer> allowed = ROLE_DEPT_TYPES.get(roleKey);
        return allowed == null || deptType != null && allowed.contains(deptType);
    }
}
