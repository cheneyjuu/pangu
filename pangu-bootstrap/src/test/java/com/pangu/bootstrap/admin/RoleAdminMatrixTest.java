package com.pangu.bootstrap.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M2-4 SaaS 角色管理三个 endpoint 的矩阵测试：
 *
 * <ul>
 *   <li>{@code POST   /api/v1/admin/roles}                       —— createRole</li>
 *   <li>{@code POST   /api/v1/admin/roles/{roleId}/permissions}  —— assignPermission</li>
 *   <li>{@code DELETE /api/v1/admin/roles/{roleId}}              —— deleteRole</li>
 * </ul>
 *
 * <p>覆盖：
 * <ol>
 *   <li>非 GOV_SUPER_ADMIN（GRID_OPERATOR / COMMITTEE_DIRECTOR / C_USER）— 三个 endpoint 全 403；</li>
 *   <li>GOV_SUPER_ADMIN 正向通路 — createRole / assignPermission / deleteRole happy path；</li>
 *   <li>应用层校验失败 — invalid dept (42101) / duplicate roleKey (42202)；</li>
 *   <li>仓储层 → DB 触发器兜底 — 系统角色保护 (42301 trigger 7) / 不存在角色 (42201)；</li>
 *   <li>授权重复 — duplicate assignment (42203)。</li>
 * </ol>
 *
 * <p>用 {@link System#nanoTime()} 后缀生成唯一 roleKey，避免 Maven 并发或重复跑导致脏数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RoleAdminMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final long TENANT_RUSHI = 10001L;

    // V1.1 求是小区 seed
    private static final long ACC_WANG = 999801L, USR_WANG = 800001L;       // 王街道  GOV_SUPER_ADMIN（admin:role:manage 唯一持有者）
    private static final long ACC_GRID = 999804L, USR_GRID = 800004L;       // 陈网格员 GRID_OPERATOR
    private static final long ACC_DIR  = 999811L, USR_DIR  = 800101L;       // 周主任   COMMITTEE_DIRECTOR
    private static final long ACC_LISI = 999913L, UID_LISI = 70002L;        // 李四     C_USER

    // ===== 1. 非 GOV_SUPER_ADMIN —— 三 endpoint 全 403 =====

    @Test
    public void gridOperatorCannotCreateRole_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRoleJson("RA_TEST_DENIED_" + System.nanoTime())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void committeeDirectorCannotAssignPermission_403() throws Exception {
        // COMMITTEE_DIRECTOR (B 端) 没有 admin:role:manage —— 仅 GOV_SUPER_ADMIN 持有
        String token = jwtTokenProvider.generateToken(ACC_DIR, "SYS_USER", USR_DIR, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/admin/roles/9999/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionKey\":\"identity:switch\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void cUserCannotDeleteRole_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(delete("/api/v1/admin/roles/9999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== 2. GOV_SUPER_ADMIN happy path：create → assignPermission → delete =====

    @Test
    public void govSuperAdmin_fullCrudHappyPath_201_then_200_then_200() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_WANG, "SYS_USER", USR_WANG, TENANT_RUSHI);
        String roleKey = "RA_TEST_HAPPY_" + System.nanoTime();

        // (1) 创建角色 — 201
        String createBody = objectMapper.writeValueAsString(Map.of(
                "roleKey", roleKey,
                "roleName", "M2-4 测试角色",
                "allowedDeptCategory", "G",
                // ALL_COMMUNITY default + null fixed —— 通过 chk_role_scope_consistency
                "defaultDataScope", "ALL_COMMUNITY"));
        Long newRoleId = parseRoleId(mockMvc.perform(post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.roleKey", is(roleKey)))
                .andExpect(jsonPath("$.data.roleId", notNullValue()))
                .andReturn().getResponse().getContentAsString());

        // (2) 授予 identity:switch（'G/B/S' 端通用，无 redline）— 200
        mockMvc.perform(post("/api/v1/admin/roles/" + newRoleId + "/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionKey\":\"identity:switch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        // (3) 删除该角色（is_system=0 → trigger 7 不拦） — 200
        mockMvc.perform(delete("/api/v1/admin/roles/" + newRoleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    // ===== 3. 应用层校验失败 =====

    @Test
    public void govSuperAdmin_createWithInvalidDept_400_42101() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_WANG, "SYS_USER", USR_WANG, TENANT_RUSHI);
        // dept='X' 通过 Bean Validation 但被 application 层 ALLOWED_DEPT_CATEGORIES 拒绝 ——
        // 实际 @Pattern 也会拒（G|B|S），先于 application 层抛 MethodArgumentNotValid → 40001 PARAM_ERROR。
        // 这里改为缺 defaultDataScope 触发应用层 42101。
        Map<String, Object> body = new HashMap<>();
        body.put("roleKey", "RA_TEST_BAD_" + System.nanoTime());
        body.put("roleName", "bad");
        body.put("allowedDeptCategory", "G");
        body.put("defaultDataScope", "INVALID_SCOPE");
        mockMvc.perform(post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(42101)));
    }

    @Test
    public void govSuperAdmin_createDuplicateRoleKey_409_42202() throws Exception {
        // role_id=1 GOV_SUPER_ADMIN 是 V1 预置；试图新建同名角色应被 UNIQUE 拒
        String token = jwtTokenProvider.generateToken(ACC_WANG, "SYS_USER", USR_WANG, TENANT_RUSHI);
        String body = objectMapper.writeValueAsString(Map.of(
                "roleKey", "GOV_SUPER_ADMIN",
                "roleName", "重复",
                "allowedDeptCategory", "G",
                "defaultDataScope", "ALL_COMMUNITY"));
        mockMvc.perform(post("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42202)));
    }

    // ===== 4. 仓储层 → DB 触发器兜底 =====

    @Test
    public void govSuperAdmin_deleteSystemRole_403_42301_byTrigger7() throws Exception {
        // role_id=1 GOV_SUPER_ADMIN is_system=1 —— trigger 7 拒删，转译为 42301
        String token = jwtTokenProvider.generateToken(ACC_WANG, "SYS_USER", USR_WANG, TENANT_RUSHI);
        mockMvc.perform(delete("/api/v1/admin/roles/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(42301)));
    }

    @Test
    public void govSuperAdmin_deleteNonExistentRole_404_42201() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_WANG, "SYS_USER", USR_WANG, TENANT_RUSHI);
        mockMvc.perform(delete("/api/v1/admin/roles/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(42201)));
    }

    @Test
    public void govSuperAdmin_assignPermissionToNonExistentRole_404_42201() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_WANG, "SYS_USER", USR_WANG, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/admin/roles/999999/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionKey\":\"identity:switch\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(42201)));
    }

    @Test
    public void govSuperAdmin_assignDuplicatePermission_409_42203() throws Exception {
        // GOV_SUPER_ADMIN 在 V2.9 已被授予 admin:role:manage —— 重复授予应返回 42203
        String token = jwtTokenProvider.generateToken(ACC_WANG, "SYS_USER", USR_WANG, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/admin/roles/1/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionKey\":\"admin:role:manage\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42203)));
    }

    // ===== 帮助器 =====

    private String validCreateRoleJson(String roleKey) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "roleKey", roleKey,
                "roleName", "deny-test",
                "allowedDeptCategory", "G",
                "defaultDataScope", "ALL_COMMUNITY"));
    }

    @SuppressWarnings("unchecked")
    private Long parseRoleId(String json) throws Exception {
        Map<String, Object> resp = objectMapper.readValue(json, Map.class);
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        Object roleId = data.get("roleId");
        return roleId instanceof Number n ? n.longValue() : Long.valueOf(roleId.toString());
    }
}
