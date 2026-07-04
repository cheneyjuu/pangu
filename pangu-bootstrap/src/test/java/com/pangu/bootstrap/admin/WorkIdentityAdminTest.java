package com.pangu.bootstrap.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工作身份授权端到端矩阵。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class WorkIdentityAdminTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TENANT_RUSHI = 10001L;
    private static final long ACC_SUPER = 999801L;
    private static final long USR_SUPER = 800001L;
    private static final long ACC_COMMUNITY = 999803L;
    private static final long USR_COMMUNITY = 800003L;
    private static final long ACC_DIRECTOR = 999811L;
    private static final long USR_DIRECTOR = 800101L;
    private static final long ACC_WU = 999805L;
    private static final long USR_WU_GOV = 800005L;
    private static final long DEPT_COMMUNITY = 101L;
    private static final long DEPT_GRID = 104L;
    private static final long BUILDING_30005 = 30005L;
    private static final long TENANT_CROSS = 10002L;
    private static final long CROSS_ROOM = 10002300101L;
    private static final long CROSS_OWNER_UID = 70001L;
    private static final String NEW_GRID_MEMBER_PHONE = "13900009997";

    @BeforeEach
    @AfterEach
    public void cleanupGeneratedGridShadow() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE account_id = ? AND dept_id = ?",
                ACC_WU, DEPT_GRID);
        jdbcTemplate.update("""
                DELETE FROM c_owner_property
                WHERE uid = ? AND tenant_id = ? AND room_id = ?
                """, CROSS_OWNER_UID, TENANT_CROSS, CROSS_ROOM);
        jdbcTemplate.update("""
                DELETE FROM sys_dept_tenant_scope
                WHERE dept_id = ?
                  AND tenant_id = ?
                """, DEPT_COMMUNITY, TENANT_CROSS);
        cleanupGeneratedGridAssignments();
        cleanupGeneratedGridNodes();
        resetGridDeptScope();
        jdbcTemplate.update("""
                UPDATE t_account
                SET last_active_identity_id = ?, last_active_identity_type = 'SYS_USER'
                WHERE account_id = ?
                """, USR_WU_GOV, ACC_WU);
        jdbcTemplate.update("""
                DELETE FROM sys_user
                WHERE account_id IN (
                    SELECT account_id FROM t_account WHERE phone = ?
                )
                """, NEW_GRID_MEMBER_PHONE);
        jdbcTemplate.update("DELETE FROM t_account WHERE phone = ?", NEW_GRID_MEMBER_PHONE);
    }

    @Test
    public void communityAdminEnsureGridNodes_createsFiveStaticGridDeptNodes() throws Exception {
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        mockMvc.perform(post("/api/v1/admin/work-identities/depts/" + DEPT_COMMUNITY + "/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.deptName=='1号网格')].deptType", hasItem(5)))
                .andExpect(jsonPath("$.data[?(@.deptName=='2号网格')].deptType", hasItem(5)))
                .andExpect(jsonPath("$.data[?(@.deptName=='3号网格')].deptType", hasItem(5)))
                .andExpect(jsonPath("$.data[?(@.deptName=='4号网格')].deptType", hasItem(5)))
                .andExpect(jsonPath("$.data[?(@.deptName=='5号网格')].deptType", hasItem(5)))
                .andExpect(jsonPath("$.data[?(@.deptName=='1号网格')].parentId", hasItem((int) DEPT_COMMUNITY)))
                .andExpect(jsonPath("$.data[?(@.deptName=='1号网格')].deptCategory", hasItem("G")))
                .andExpect(jsonPath("$.data[?(@.deptName=='1号网格')].tenantId", hasItem((int) TENANT_RUSHI)));

        mockMvc.perform(post("/api/v1/admin/work-identities/depts/" + DEPT_COMMUNITY + "/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.deptName=='1号网格')]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.deptName=='5号网格')]", hasSize(1)));
    }

    @Test
    public void authMenus_returnBackendSortedNavigationAndSystemManagementLast() throws Exception {
        String superToken = token(ACC_SUPER, USR_SUPER, null);

        String response = mockMvc.perform(get("/api/v1/auth/menus")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id", is("dashboard")))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode menus = objectMapper.readTree(response).path("data");
        JsonNode systemMenu = menus.get(menus.size() - 1);
        assertEquals("users", systemMenu.path("id").asText());
        assertEquals("系统管理", systemMenu.path("label").asText());
        assertEquals("owners", systemMenu.path("pages").get(0).path("id").asText());
        assertEquals("grid-management", systemMenu.path("pages").get(2).path("id").asText());

        for (JsonNode menu : menus) {
            assertNotEquals("building-assignment", menu.path("id").asText());
            for (JsonNode page : menu.path("pages")) {
                assertNotEquals("building-assignment", page.path("id").asText());
            }
        }

        mockMvc.perform(post("/api/v1/auth/switch-shadow")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetUserId", USR_SUPER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.menu_permissions", hasItem("grid-management")))
                .andExpect(jsonPath("$.data.user_info.menu_permissions", hasItem("rbac")));
    }

    @Test
    public void communityAdminCreateGridNode_usesRequestedDeptNameUnderCommunity() throws Exception {
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        mockMvc.perform(post("/api/v1/admin/work-identities/depts/" + DEPT_COMMUNITY + "/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deptName", "1号网格"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].deptName", is("1号网格")))
                .andExpect(jsonPath("$.data[0].parentId", is((int) DEPT_COMMUNITY)))
                .andExpect(jsonPath("$.data[0].deptType", is(5)))
                .andExpect(jsonPath("$.data[0].deptCategory", is("G")))
                .andExpect(jsonPath("$.data[0].tenantId", is((int) TENANT_RUSHI)));
    }

    @Test
    public void communityAdminCreateGridNode_usesCurrentCommunityWithoutClientParentSelection() throws Exception {
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);
        String name = "临时网格-" + System.nanoTime();

        mockMvc.perform(post("/api/v1/admin/work-identities/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deptName", name))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptName", is(name)))
                .andExpect(jsonPath("$.data.parentId", is((int) DEPT_COMMUNITY)))
                .andExpect(jsonPath("$.data.deptType", is(5)))
                .andExpect(jsonPath("$.data.deptCategory", is("G")))
                .andExpect(jsonPath("$.data.tenantId", is((int) TENANT_RUSHI)));
    }

    @Test
    public void communityAdminCanListRenameAndDeleteUnusedGridNode() throws Exception {
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);
        String name = "临时网格-" + System.nanoTime();
        String renamed = name + "-改";

        String created = mockMvc.perform(post("/api/v1/admin/work-identities/depts/" + DEPT_COMMUNITY + "/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deptName", name))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deptName", is(name)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long gridDeptId = objectMapper.readTree(created).path("data").path(0).path("deptId").asLong();

        mockMvc.perform(get("/api/v1/admin/work-identities/depts/" + DEPT_COMMUNITY + "/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.deptName=='" + name + "')].deptId").exists());

        mockMvc.perform(patch("/api/v1/admin/work-identities/depts/" + gridDeptId + "/grid-node")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deptName", renamed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptName", is(renamed)));

        mockMvc.perform(delete("/api/v1/admin/work-identities/depts/" + gridDeptId + "/grid-node")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk());

        Integer activeRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_dept
                WHERE dept_id = ?
                  AND status = '0'
                """, Integer.class, gridDeptId);
        assertEquals(0, activeRows);
    }

    @Test
    public void communityAdminCannotDeleteGridNodeWithActiveGridMember() throws Exception {
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        mockMvc.perform(delete("/api/v1/admin/work-identities/depts/" + DEPT_GRID + "/grid-node")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42510)));
    }

    @Test
    public void accountSearchCanFilterGridMembersForDataScopeAssignment() throws Exception {
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        mockMvc.perform(get("/api/v1/admin/work-identities/accounts")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.accountId==999804)]").exists())
                .andExpect(jsonPath("$.data[?(@.accountId==999803)]").exists());

        mockMvc.perform(get("/api/v1/admin/work-identities/accounts")
                        .param("roleKey", "GRID_MEMBER")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].shadows[0].roleKey", is("GRID_MEMBER")));

        mockMvc.perform(get("/api/v1/admin/work-identities/accounts/search")
                        .param("keyword", "13800000004")
                        .param("roleKey", "GRID_MEMBER")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].accountId", is(999804)))
                .andExpect(jsonPath("$.data[0].shadows[0].roleKey", is("GRID_MEMBER")));

        mockMvc.perform(get("/api/v1/admin/work-identities/accounts/search")
                        .param("keyword", "陈网格员")
                        .param("roleKey", "GRID_MEMBER")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].accountId", is(999804)));

        mockMvc.perform(get("/api/v1/admin/work-identities/accounts/search")
                        .param("keyword", "13800000004")
                        .param("roleKey", "VOLUNTEER")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    public void createGridShadowForGovOperatorAccount_thenAuthShadowSwitchWorks() throws Exception {
        String superToken = token(ACC_SUPER, USR_SUPER);
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        mockMvc.perform(get("/api/v1/admin/work-identities/accounts/search")
                        .param("keyword", "13800000005")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].accountId", is((int) ACC_WU)))
                .andExpect(jsonPath("$.data[0].shadows", hasSize(1)))
                .andExpect(jsonPath("$.data[0].shadows[0].roleKey", is("GOV_OPERATOR")));

        mockMvc.perform(get("/api/v1/admin/work-identities/dept-options")
                        .param("roleKey", "GRID_MEMBER")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.deptId==" + DEPT_GRID + ")].deptType", hasItem(5)));

        mockMvc.perform(put("/api/v1/admin/work-identities/depts/" + DEPT_GRID + "/building-scope")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "buildingIds", List.of(30001L, 30002L, BUILDING_30005)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.buildingId==" + BUILDING_30005 + ")]").exists());

        mockMvc.perform(get("/api/v1/admin/work-identities/depts/" + DEPT_GRID + "/building-scope")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.buildingId==" + BUILDING_30005 + ")]").exists());

        Map<String, Object> body = Map.of(
                "deptId", DEPT_GRID,
                "roleKey", "GRID_MEMBER",
                "nickName", "吴经办员(网格)");
        String response = mockMvc.perform(post("/api/v1/admin/work-identities/accounts/" + ACC_WU + "/shadows")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.userId", notNullValue()))
                .andExpect(jsonPath("$.data.roleKey", is("GRID_MEMBER")))
                .andExpect(jsonPath("$.data.effectiveDataScope", is("OWNER_GROUP")))
                .andExpect(jsonPath("$.data.buildingIds", hasItem((int) BUILDING_30005)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        long gridUserId = root.path("data").path("userId").asLong();
        Integer userBuildingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_building WHERE user_id = ?",
                Integer.class,
                gridUserId);
        assertEquals(0, userBuildingRows);

        mockMvc.perform(get("/api/v1/admin/work-identities/accounts/" + ACC_WU)
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shadows", hasSize(2)))
                .andExpect(jsonPath("$.data.shadows[?(@.roleKey=='GOV_OPERATOR')]").exists())
                .andExpect(jsonPath("$.data.shadows[?(@.roleKey=='GRID_MEMBER')]").exists());

        String wuToken = token(ACC_WU, USR_WU_GOV);
        mockMvc.perform(get("/api/v1/auth/shadows")
                        .header("Authorization", "Bearer " + wuToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shadows", hasSize(2)))
                .andExpect(jsonPath("$.data.shadows[?(@.role_key=='GOV_OPERATOR')]").exists())
                .andExpect(jsonPath("$.data.shadows[?(@.role_key=='GRID_MEMBER')]").exists());

        mockMvc.perform(post("/api/v1/auth/switch-shadow")
                        .header("Authorization", "Bearer " + wuToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetUserId", gridUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.role_key", is("GRID_MEMBER")))
                .andExpect(jsonPath("$.data.user_info.active_identity_id", is((int) gridUserId)));
    }

    @Test
    public void communityAdminCreateGridMemberAccount_thenPhoneLoginUsesGridIdentity() throws Exception {
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        String response = mockMvc.perform(post("/api/v1/admin/work-identities/accounts")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", NEW_GRID_MEMBER_PHONE,
                                "realName", "张大妈",
                                "deptId", DEPT_GRID,
                                "roleKey", "GRID_MEMBER",
                                "nickName", "张大妈(1号网格员)"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone", is(NEW_GRID_MEMBER_PHONE)))
                .andExpect(jsonPath("$.data.realName", is("张大妈")))
                .andExpect(jsonPath("$.data.shadows", hasSize(1)))
                .andExpect(jsonPath("$.data.shadows[0].deptId", is((int) DEPT_GRID)))
                .andExpect(jsonPath("$.data.shadows[0].roleKey", is("GRID_MEMBER")))
                .andExpect(jsonPath("$.data.shadows[0].gridNodes[0].deptId", is((int) DEPT_GRID)))
                .andExpect(jsonPath("$.data.shadows[0].buildingIds", hasItem(30001)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        long accountId = root.path("data").path("accountId").asLong();
        long userId = root.path("data").path("shadows").path(0).path("userId").asLong();

        Integer activeIdentityRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_account
                WHERE account_id = ?
                  AND last_active_identity_id = ?
                  AND last_active_identity_type = 'SYS_USER'
                """, Integer.class, accountId, userId);
        assertEquals(1, activeIdentityRows);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", NEW_GRID_MEMBER_PHONE,
                                "smsCode", "123456",
                                "clientPortal", "G"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.role_key", is("GRID_MEMBER")))
                .andExpect(jsonPath("$.data.user_info.active_identity_id", is((int) userId)));
    }

    @Test
    public void communityAdminCanAssignGridMemberToMultipleGridNodesAndAggregateScope() throws Exception {
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);
        long extraGridDeptId = createTemporaryGridNodeWithScope(communityToken, BUILDING_30005);

        String response = mockMvc.perform(post("/api/v1/admin/work-identities/accounts")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", NEW_GRID_MEMBER_PHONE,
                                "realName", "张大妈",
                                "deptId", DEPT_GRID,
                                "roleKey", "GRID_MEMBER",
                                "nickName", "张大妈(网格员)"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shadows[0].buildingIds", hasItem(30001)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        long accountId = root.path("data").path("accountId").asLong();
        long userId = root.path("data").path("shadows").path(0).path("userId").asLong();

        mockMvc.perform(put("/api/v1/admin/work-identities/users/" + userId + "/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "gridDeptIds", List.of(DEPT_GRID, extraGridDeptId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.deptId==" + DEPT_GRID + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.deptId==" + extraGridDeptId + ")]").exists());

        mockMvc.perform(get("/api/v1/admin/work-identities/users/" + userId + "/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/v1/admin/work-identities/accounts/" + accountId)
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shadows[0].gridNodes", hasSize(2)))
                .andExpect(jsonPath("$.data.shadows[0].gridNodes[?(@.deptId==" + DEPT_GRID + ")]").exists())
                .andExpect(jsonPath("$.data.shadows[0].gridNodes[?(@.deptId==" + extraGridDeptId + ")]").exists())
                .andExpect(jsonPath("$.data.shadows[0].buildingIds", hasItem(30001)))
                .andExpect(jsonPath("$.data.shadows[0].buildingIds", hasItem((int) BUILDING_30005)));
    }

    @Test
    public void communityAdminCannotAssignNonGridMemberToGridNode() throws Exception {
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        mockMvc.perform(put("/api/v1/admin/work-identities/users/" + USR_WU_GOV + "/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "gridDeptIds", List.of(DEPT_GRID)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42507)));
    }

    @Test
    public void communityAdminCanConfigureGridScopeAcrossTenantBuildingPairs() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO c_owner_property
                    (uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status)
                VALUES (?, ?, 30001, ?, 88.00, 0, 1)
                ON CONFLICT DO NOTHING
                """, CROSS_OWNER_UID, TENANT_CROSS, CROSS_ROOM);
        grantCrossTenantToCommunity();
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        mockMvc.perform(put("/api/v1/admin/work-identities/depts/" + DEPT_GRID + "/building-scope")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "buildingScopes", List.of(
                                        Map.of("tenantId", TENANT_RUSHI, "buildingId", 30001L),
                                        Map.of("tenantId", TENANT_CROSS, "buildingId", 30001L))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.tenantId==" + TENANT_RUSHI
                        + " && @.buildingId==30001)]").exists())
                .andExpect(jsonPath("$.data[?(@.tenantId==" + TENANT_CROSS
                        + " && @.buildingId==30001)]").exists());

        Integer scopedRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_dept_building_scope
                WHERE dept_id = ?
                  AND building_id = 30001
                  AND tenant_id IN (?, ?)
                  AND status = 1
                """, Integer.class, DEPT_GRID, TENANT_RUSHI, TENANT_CROSS);
        assertEquals(2, scopedRows);
    }

    @Test
    public void communityAdminBuildingOptionsOnlyUseCommunityTenantScope() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO c_owner_property
                    (uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status)
                VALUES (?, ?, 30001, ?, 88.00, 0, 1)
                ON CONFLICT DO NOTHING
                """, CROSS_OWNER_UID, TENANT_CROSS, CROSS_ROOM);
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        mockMvc.perform(get("/api/v1/admin/work-identities/building-options")
                        .param("deptId", String.valueOf(DEPT_GRID))
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.tenantId==" + TENANT_RUSHI
                        + " && @.buildingId==30001)]").exists())
                .andExpect(jsonPath("$.data[?(@.tenantId==" + TENANT_CROSS
                        + " && @.buildingId==30001)]").doesNotExist());

        grantCrossTenantToCommunity();

        mockMvc.perform(get("/api/v1/admin/work-identities/building-options")
                        .param("deptId", String.valueOf(DEPT_GRID))
                        .header("Authorization", "Bearer " + communityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.tenantId==" + TENANT_CROSS
                        + " && @.buildingId==30001)]").exists());
    }

    @Test
    public void communityAdminCannotAssignGridScopeOutsideCommunityTenantScope() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO c_owner_property
                    (uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status)
                VALUES (?, ?, 30001, ?, 88.00, 0, 1)
                ON CONFLICT DO NOTHING
                """, CROSS_OWNER_UID, TENANT_CROSS, CROSS_ROOM);
        String communityToken = token(ACC_COMMUNITY, USR_COMMUNITY);

        mockMvc.perform(put("/api/v1/admin/work-identities/depts/" + DEPT_GRID + "/building-scope")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "buildingScopes", List.of(
                                        Map.of("tenantId", TENANT_RUSHI, "buildingId", 30001L),
                                        Map.of("tenantId", TENANT_CROSS, "buildingId", 30001L))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(42502)));
    }

    @Test
    public void streetSuperAdminWithoutTenant_cannotAssignGridMemberBuildingScope() throws Exception {
        String adminToken = token(ACC_SUPER, USR_SUPER, null);

        mockMvc.perform(get("/api/v1/admin/work-identities/building-options")
                        .param("deptId", String.valueOf(DEPT_GRID))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.buildingId==" + BUILDING_30005 + ")]").exists());

        Map<String, Object> body = Map.of(
                "deptId", DEPT_GRID,
                "roleKey", "GRID_MEMBER",
                "nickName", "吴经办员(网格)",
                "buildingIds", List.of(BUILDING_30005));
        mockMvc.perform(post("/api/v1/admin/work-identities/accounts/" + ACC_WU + "/shadows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(42502)));
    }

    @Test
    public void gridRoleWithoutBuilding_rejectedBeforeInsert() throws Exception {
        jdbcTemplate.update("UPDATE sys_dept_building_scope SET status = 2 WHERE dept_id = ?", DEPT_GRID);
        String adminToken = token(ACC_COMMUNITY, USR_COMMUNITY);
        Map<String, Object> body = Map.of(
                "deptId", DEPT_GRID,
                "roleKey", "GRID_MEMBER");

        mockMvc.perform(post("/api/v1/admin/work-identities/accounts/" + ACC_WU + "/shadows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(42508)));
    }

    @Test
    public void nonBuildingRoleWithBuildings_rejected() throws Exception {
        String adminToken = token(ACC_SUPER, USR_SUPER);
        Map<String, Object> body = Map.of(
                "deptId", DEPT_GRID,
                "roleKey", "GOV_OPERATOR",
                "buildingIds", List.of(BUILDING_30005));

        mockMvc.perform(post("/api/v1/admin/work-identities/accounts/" + ACC_WU + "/shadows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(42509)));
    }

    @Test
    public void duplicateDeptIdentity_rejected() throws Exception {
        String adminToken = token(ACC_SUPER, USR_SUPER);
        Map<String, Object> body = Map.of(
                "deptId", DEPT_COMMUNITY,
                "roleKey", "GOV_OPERATOR");

        mockMvc.perform(post("/api/v1/admin/work-identities/accounts/" + ACC_WU + "/shadows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42506)));
    }

    @Test
    public void roleDeptTypeMismatch_rejected() throws Exception {
        String adminToken = token(ACC_COMMUNITY, USR_COMMUNITY);
        Map<String, Object> body = Map.of(
                "deptId", DEPT_COMMUNITY,
                "roleKey", "GRID_MEMBER",
                "buildingIds", List.of(BUILDING_30005));

        mockMvc.perform(post("/api/v1/admin/work-identities/accounts/" + ACC_WU + "/shadows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42507)));
    }

    @Test
    public void committeeDirectorCannotCreateGridMemberShadow() throws Exception {
        String token = token(ACC_DIRECTOR, USR_DIRECTOR);
        Map<String, Object> body = Map.of(
                "deptId", DEPT_GRID,
                "roleKey", "GRID_MEMBER",
                "buildingIds", List.of(BUILDING_30005));

        mockMvc.perform(post("/api/v1/admin/work-identities/accounts/" + ACC_WU + "/shadows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    private void resetGridDeptScope() {
        jdbcTemplate.update("DELETE FROM sys_dept_building_scope WHERE dept_id = ?", DEPT_GRID);
        jdbcTemplate.update("""
                INSERT INTO sys_dept_building_scope (dept_id, tenant_id, building_id, assigned_by, status)
                VALUES
                    (?, ?, 30001, ?, 1),
                    (?, ?, 30002, ?, 1)
                ON CONFLICT (dept_id, tenant_id, building_id)
                DO UPDATE SET status = 1, assigned_by = EXCLUDED.assigned_by, updated_at = now()
                """, DEPT_GRID, TENANT_RUSHI, USR_COMMUNITY,
                DEPT_GRID, TENANT_RUSHI, USR_COMMUNITY);
    }

    private void grantCrossTenantToCommunity() {
        jdbcTemplate.update("""
                INSERT INTO sys_dept_tenant_scope (dept_id, tenant_id, assigned_by, status)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (dept_id, tenant_id)
                DO UPDATE SET status = 1, assigned_by = EXCLUDED.assigned_by, updated_at = now()
                """, DEPT_COMMUNITY, TENANT_CROSS, USR_COMMUNITY);
    }

    private long createTemporaryGridNodeWithScope(String communityToken, long buildingId) throws Exception {
        String name = "临时网格-" + System.nanoTime();
        String created = mockMvc.perform(post("/api/v1/admin/work-identities/depts/" + DEPT_COMMUNITY + "/grid-nodes")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deptName", name))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long gridDeptId = objectMapper.readTree(created).path("data").path(0).path("deptId").asLong();

        mockMvc.perform(put("/api/v1/admin/work-identities/depts/" + gridDeptId + "/building-scope")
                        .header("Authorization", "Bearer " + communityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "buildingIds", List.of(buildingId)))))
                .andExpect(status().isOk());
        return gridDeptId;
    }

    private void cleanupGeneratedGridAssignments() {
        jdbcTemplate.update("""
                DELETE FROM sys_user_grid_dept_scope
                WHERE user_id IN (
                    SELECT user_id
                    FROM sys_user
                    WHERE account_id IN (
                        SELECT account_id FROM t_account WHERE phone = ?
                    )
                )
                   OR grid_dept_id IN (
                    SELECT dept_id
                    FROM sys_dept
                    WHERE parent_id = ?
                      AND dept_type = 5
                      AND (
                          dept_name IN ('1号网格', '2号网格', '3号网格', '4号网格', '5号网格')
                          OR dept_name LIKE '临时网格-%'
                      )
                )
                """, NEW_GRID_MEMBER_PHONE, DEPT_COMMUNITY);
        jdbcTemplate.update("""
                DELETE FROM sys_dept_building_scope
                WHERE dept_id IN (
                    SELECT dept_id
                    FROM sys_dept
                    WHERE parent_id = ?
                      AND dept_type = 5
                      AND (
                          dept_name IN ('1号网格', '2号网格', '3号网格', '4号网格', '5号网格')
                          OR dept_name LIKE '临时网格-%'
                      )
                )
                """, DEPT_COMMUNITY);
    }

    private void cleanupGeneratedGridNodes() {
        jdbcTemplate.update("""
                DELETE FROM sys_dept
                WHERE parent_id = ?
                  AND dept_type = 5
                  AND (
                      dept_name IN ('1号网格', '2号网格', '3号网格', '4号网格', '5号网格')
                      OR dept_name LIKE '临时网格-%'
                  )
                """, DEPT_COMMUNITY);
    }

    private String token(long accountId, long userId) {
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, TENANT_RUSHI);
    }

    private String token(long accountId, long userId, Long tenantId) {
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, tenantId);
    }
}
