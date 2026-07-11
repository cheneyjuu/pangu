package com.pangu.bootstrap.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M4 楼栋责任田分配端点矩阵测试：
 *
 * <ul>
 *   <li>{@code GET    /api/v1/admin/building-assignments/users?roleKey=...}</li>
 *   <li>{@code GET    /api/v1/admin/building-assignments/buildings}</li>
 *   <li>{@code GET    /api/v1/admin/building-assignments/users/{userId}/buildings}</li>
 *   <li>{@code POST   /api/v1/admin/building-assignments/users/{userId}/buildings}</li>
 *   <li>{@code DELETE /api/v1/admin/building-assignments/users/{userId}/buildings/{buildingId}}</li>
 * </ul>
 *
 * <p>覆盖：
 * <ol>
 *   <li>分配者白名单：业委会主任分配志愿者楼栋（B 端核心用例）→ 200；</li>
 *   <li>非白名单角色（业委会委员）→ 403/42402 FORBIDDEN；</li>
 *   <li>跨租户楼栋 → 403/42404 BUILDING_NOT_IN_SCOPE；</li>
 *   <li>给非可分配角色用户（如物业经理）分配 → 404/42403 USER_NOT_FOUND；</li>
 *   <li>幂等 assign → 重复 200；</li>
 *   <li>revoke 不存在的授予 → 404/42405 ASSIGNMENT_NOT_FOUND；</li>
 *   <li>非法 targetRoleKey → 400 PARAM_ERROR（@Pattern）。</li>
 * </ol>
 *
 * <p>seed 数据（V1.1）：tenant=10001 / 居委会主任 acct=999803 user=800003 / 业委会主任 acct=999811
 * user=800101 / 业委会委员 acct=999813 user=800103 / 网格员 acct=999804 user=800004 /
 * 志愿者 acct=999814 user=800104 / 业主代表 acct=999812 user=800102 / 物业经理 acct=999821 user=800201。
 * 楼栋 30001/30002/30003/30005 同租户 c_owner_property 已存在。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class BuildingAssignmentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final long TENANT_RUSHI = 10001L;

    // 分配者
    private static final long ACC_COMMUNITY_ADMIN = 999803L, USR_COMMUNITY_ADMIN = 800003L; // 居委会主任
    private static final long ACC_DIRECTOR = 999811L, USR_DIRECTOR = 800101L;               // 业委会主任
    private static final long ACC_MEMBER = 999813L, USR_MEMBER = 800103L;                   // 业委会委员（非白名单）
    private static final long ACC_OWNER_REP = 999812L, USR_OWNER_REP = 800102L;             // 业主代表
    // 目标用户
    private static final long USR_GRID = 800004L;       // 网格员
    private static final long USR_LIU_GRID_SHADOW = 800006L; // 刘主任的网格员分身
    private static final long USR_VOLUNTEER = 800104L;  // 志愿者
    private static final long USR_PROPERTY_MGR = 800201L; // 物业经理（非可分配角色）

    // ===== 1. 网格员范围不再走楼栋责任田 =====

    @Test
    public void communityAdmin_assignGridMember_rejectedAsGridManagementOnly() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);

        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_GRID + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(30005L, "GRID_MEMBER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)));
    }

    // ===== 2. 业委会主任分配志愿者（B 端核心）=====

    @Test
    public void committeeDirector_assignVolunteer_happyPath_200() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR, TENANT_RUSHI);
        long bid = 30002L;

        mockMvc.perform(delete("/api/v1/admin/building-assignments/users/" + USR_VOLUNTEER + "/buildings/" + bid)
                .header("Authorization", "Bearer " + token));

        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_VOLUNTEER + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(bid, "VOLUNTEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        // 清理
        mockMvc.perform(delete("/api/v1/admin/building-assignments/users/" + USR_VOLUNTEER + "/buildings/" + bid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    public void committeeDirector_assignGridMember_forbidden() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR, TENANT_RUSHI);

        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_GRID + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(30005L, "GRID_MEMBER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)));
    }

    // ===== 3. 非白名单角色 业委会委员 → 403/42402 =====

    @Test
    public void committeeMember_assign_403_42402() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_MEMBER, "SYS_USER", USR_MEMBER, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_VOLUNTEER + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(30001L, "VOLUNTEER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(42402)));
    }

    // ===== 4. 跨租户楼栋 → 403/42404 =====

    @Test
    public void communityAdmin_assignBuildingNotInTenant_403_42404() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        // 99999 不存在于本租户 c_owner_property
        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_VOLUNTEER + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(99999L, "VOLUNTEER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(42404)));
    }

    // ===== 5. 目标用户非可分配角色（物业经理） → 404/42403 =====

    @Test
    public void communityAdmin_assignNonAssignableUser_404_42403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        // 给物业经理(800201) 分配 VOLUNTEER 楼栋 → 该用户角色不是 VOLUNTEER
        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_PROPERTY_MGR + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(30001L, "VOLUNTEER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(42403)));
    }

    // ===== 6. 幂等 assign：重复仍返回 200 =====

    @Test
    public void communityAdmin_assignIdempotent_200() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        // 800104 已生效 30005（V1.2 seed）—— 再 assign 一次应幂等成功
        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_VOLUNTEER + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(30005L, "VOLUNTEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    // ===== 7. revoke 不存在的授予 → 404/42405 =====

    @Test
    public void communityAdmin_revokeNonExisting_404_42405() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        // 800004 不持有楼栋 99999 的授予
        mockMvc.perform(delete("/api/v1/admin/building-assignments/users/" + USR_GRID + "/buildings/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(42405)));
    }

    // ===== 8. 非法 targetRoleKey → 400 PARAM_ERROR =====

    @Test
    public void communityAdmin_assignInvalidTargetRoleKey_400() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_GRID + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(30001L, "COMMITTEE_DIRECTOR")))
                .andExpect(status().isBadRequest());
    }

    // ===== 9. listUsers / listBuildings 通路 =====

    @Test
    public void communityAdmin_listVolunteers_200() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/admin/building-assignments/users")
                        .param("roleKey", "VOLUNTEER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data[0].roleKey", is("VOLUNTEER")));
    }

    @Test
    public void communityAdmin_listBuildings_200() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/admin/building-assignments/buildings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    public void ownerRepresentative_listMyBuildings_returnsMobileSummary() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_OWNER_REP, "SYS_USER", USR_OWNER_REP, TENANT_RUSHI);

        mockMvc.perform(get("/api/v1/admin/building-assignments/users/me/buildings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data[0].buildingId", is(30001)))
                .andExpect(jsonPath("$.data[0].unitCount", greaterThanOrEqualTo(1)));
    }

    // ===== 10. 搜索 / 占用 / 合规 / 转移 =====

    @Test
    public void search_byNickName_returnsMatching() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        // seed: 孙志愿者 nick_name 中文，搜「志愿」命中
        mockMvc.perform(get("/api/v1/admin/building-assignments/search")
                        .param("keyword", "志愿")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data[?(@.userId==" + USR_VOLUNTEER + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.userId==" + USR_VOLUNTEER + ")].roleKey", hasItem("VOLUNTEER")));
    }

    @Test
    public void search_byPhoneTail_returnsMatching() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        // 孙志愿者 phone=13800000014，尾号 0014
        mockMvc.perform(get("/api/v1/admin/building-assignments/search")
                        .param("keyword", "0014")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId==" + USR_VOLUNTEER + ")]").exists());
    }

    @Test
    public void search_byFullPhone_returnsMatching() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/admin/building-assignments/search")
                        .param("keyword", "13800000014")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId==" + USR_VOLUNTEER + ")]").exists());
    }

    @Test
    public void search_excludesNonAssignableRoles() throws Exception {
        // 业委会主任不在可分配 3 角色，搜「周主任」应 0 命中。
        // 刘主任已在 D-mini 具备 GRID_MEMBER 分身，因此不能再作为此负例。
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/admin/building-assignments/search")
                        .param("keyword", "周主任")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()", is(0)));
    }

    @Test
    public void listOccupants_returnsAllRoles() throws Exception {
        // seed: 30001 被 800004(GRID_MEMBER) + 800102(OWNER_REPRESENTATIVE) 同占
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/admin/building-assignments/buildings/30001/occupants")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.buildingId", is(30001)))
                .andExpect(jsonPath("$.data.occupants[?(@.userId==" + USR_GRID + ")].roleKey", hasItem("GRID_MEMBER")))
                .andExpect(jsonPath("$.data.occupants[?(@.userId==800102)].roleKey", hasItem("OWNER_REPRESENTATIVE")));
    }

    @Test
    public void assignGridMemberViaPersonalResponsibilityRejected_400() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_LIU_GRID_SHADOW + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(30001L, "GRID_MEMBER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)));
    }

    @Test
    public void assign_buildingOccupiedByDifferentRole_200() throws Exception {
        // 30001 已被 800004 GRID_MEMBER 占；给志愿者 800104 分 30001 不冲突（不同角色可共享）
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        // 先清场（防上轮残留）
        mockMvc.perform(delete("/api/v1/admin/building-assignments/users/" + USR_VOLUNTEER + "/buildings/30001")
                .header("Authorization", "Bearer " + token));

        mockMvc.perform(post("/api/v1/admin/building-assignments/users/" + USR_VOLUNTEER + "/buildings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(30001L, "VOLUNTEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        // 清理
        mockMvc.perform(delete("/api/v1/admin/building-assignments/users/" + USR_VOLUNTEER + "/buildings/30001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    public void search_resultHasPhoneAndVerifiedFields() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMMUNITY_ADMIN, "SYS_USER", USR_COMMUNITY_ADMIN, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/admin/building-assignments/search")
                        .param("keyword", "0014")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].phone").exists())
                .andExpect(jsonPath("$.data[0].realNameVerified").exists())
                .andExpect(jsonPath("$.data[0].complianceIssues").isArray());
    }

    // ===== 帮助器 =====

    private String assignBody(long buildingId, String targetRoleKey) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "buildingId", buildingId,
                "targetRoleKey", targetRoleKey));
    }
}
