package com.pangu.bootstrap.admin;

import com.pangu.application.admin.RoleAdminApplicationException;
import com.pangu.application.admin.RoleAdminQueryService;
import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.dto.RoleQuery;
import com.pangu.domain.model.role.PermissionCatalog;
import com.pangu.domain.model.role.RoleListItem;
import com.pangu.domain.model.role.RolePermissionDetail;
import com.pangu.domain.repository.SysPermissionRepository;
import com.pangu.domain.repository.SysRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link RoleAdminQueryService} 单元测试（Mockito）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code pageRoles} 透传 repository 的 {@code Page<RoleListItem>}（page/size/total + permissionCount 保留）；</li>
 *   <li>{@code listPermissionsByRole} 角色 not found → {@link RoleAdminApplicationException}（ROLE_NOT_FOUND）；</li>
 *   <li>{@code listPermissionsByRole} 命中 → 返 join 明细列表；</li>
 *   <li>{@code listAllPermissions} 透传全量权限清单。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RoleAdminQueryServiceTest {

    private static final Long ROLE_ID = 1L;

    @Mock
    private SysRoleRepository roleRepository;

    @Mock
    private SysPermissionRepository permissionRepository;

    private RoleAdminQueryService service() {
        return new RoleAdminQueryService(roleRepository, permissionRepository);
    }

    @Test
    void pageRoles_passes_through_paging_and_keeps_permission_count() {
        RoleListItem item = new RoleListItem(
                ROLE_ID, "GOV_SUPER_ADMIN", "街道办超管", "G",
                null, "ALL_COMMUNITY", 1, "0", 13L,
                Instant.parse("2025-01-01T00:00:00Z"));
        Page<RoleListItem> raw = new Page<>(List.of(item), 1L, 1, 20);
        when(roleRepository.pageRoles(any(RoleQuery.class))).thenReturn(raw);

        Page<RoleListItem> result = service().pageRoles(
                new RoleQuery(null, null, null, null, 1, 20));

        assertEquals(1L, result.total());
        assertEquals(1, result.page());
        assertEquals(20, result.size());
        assertEquals(1, result.items().size());
        RoleListItem row = result.items().get(0);
        assertEquals(ROLE_ID, row.roleId());
        assertEquals(13L, row.permissionCount());
        assertEquals(1, row.isSystem());
    }

    @Test
    void listPermissionsByRole_throws_when_role_not_found() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.empty());

        RoleAdminApplicationException ex = assertThrows(RoleAdminApplicationException.class,
                () -> service().listPermissionsByRole(ROLE_ID));
        assertEquals(RoleAdminApplicationException.Reason.ROLE_NOT_FOUND, ex.getReason());
    }

    @Test
    void listPermissionsByRole_returns_joined_details_when_role_exists() {
        RolePermissionDetail detail = new RolePermissionDetail(
                "owner:list", "业主名册分页查询", "OWNER", "GB", 0,
                800001L, Instant.parse("2025-06-22T00:00:00Z"));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(
                new com.pangu.domain.model.role.SysRole(
                        ROLE_ID, "GOV_SUPER_ADMIN", "街道办超管", "G",
                        null, "ALL_COMMUNITY", 1)));
        when(roleRepository.listPermissionsByRole(ROLE_ID)).thenReturn(List.of(detail));

        List<RolePermissionDetail> result = service().listPermissionsByRole(ROLE_ID);

        assertEquals(1, result.size());
        RolePermissionDetail d = result.get(0);
        assertEquals("owner:list", d.permissionKey());
        assertEquals("OWNER", d.permissionGroup());
        assertEquals(800001L, d.grantedBy());
    }

    @Test
    void listAllPermissions_passes_through_catalog() {
        PermissionCatalog p1 = new PermissionCatalog(
                "owner:list", "业主名册分页查询", "OWNER", "GB", 0);
        PermissionCatalog p2 = new PermissionCatalog(
                "admin:role:read", "查看角色", "ADMIN", "G", 0);
        when(permissionRepository.listAll()).thenReturn(List.of(p2, p1));

        List<PermissionCatalog> result = service().listAllPermissions();

        assertEquals(2, result.size());
        assertTrue(result.contains(p1));
        assertTrue(result.contains(p2));
        // verify the service did not reorder / drop — repository owns ordering
        assertEquals(p2, result.get(0));
    }
}
