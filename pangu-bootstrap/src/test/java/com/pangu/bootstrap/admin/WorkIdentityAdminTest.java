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

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @BeforeEach
    @AfterEach
    public void cleanupGeneratedGridShadow() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE account_id = ? AND dept_id = ?",
                ACC_WU, DEPT_GRID);
        cleanupGeneratedGridNodes();
        resetGridDeptScope();
        jdbcTemplate.update("""
                UPDATE t_account
                SET last_active_identity_id = ?, last_active_identity_type = 'SYS_USER'
                WHERE account_id = ?
                """, USR_WU_GOV, ACC_WU);
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

    private void cleanupGeneratedGridNodes() {
        jdbcTemplate.update("""
                DELETE FROM sys_dept
                WHERE parent_id = ?
                  AND dept_type = 5
                  AND dept_name IN ('1号网格', '2号网格', '3号网格', '4号网格', '5号网格')
                """, DEPT_COMMUNITY);
    }

    private String token(long accountId, long userId) {
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, TENANT_RUSHI);
    }

    private String token(long accountId, long userId, Long tenantId) {
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, tenantId);
    }
}
