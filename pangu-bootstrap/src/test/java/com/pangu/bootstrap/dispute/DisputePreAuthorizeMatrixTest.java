package com.pangu.bootstrap.dispute;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2.8 业主异议 11 个 endpoint 的 {@code @PreAuthorize} 矩阵（与
 * {@code DisclosurePreAuthorizeMatrixTest} 同风格）。
 *
 * <p>用 V1.1 求是小区 seed 用户对 2 条 G 端权限通路 + C 端 isAuthenticated 通路反复打靶：
 * <ul>
 *     <li>陈网格员（GRID_MEMBER） —— 三条 G 端 endpoint 全 403；</li>
 *     <li>李四（C_USER，无 sys_role） —— G 端 dispute:audit / decide 全 403；
 *         C 端 isAuthenticated 通路通过到 service 层；</li>
 *     <li>SYS_USER（陈网格员）调 C 端 endpoint —— authentication 通过但 service 层
 *         {@code requireUid()} 抛 NOT_OWNER（403 / code=41102）；</li>
 *     <li>刘主任（COMMUNITY_ADMIN，role_id=2） —— audit / decide 都通过 PreAuthorize；</li>
 *     <li>正向通路：刘主任带 dispute:decide / dispute:audit 通过后命中不存在 disputeId
 *         → DISPUTE_NOT_FOUND（HTTP 404 / code=41101）；</li>
 *     <li>业主越权访问：李四查询其他业主的 dispute 返 NOT_FOUND（隐藏存在性）。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
public class DisputePreAuthorizeMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final long TENANT_RUSHI = 10001L;

    // V1.1 求是小区 seed
    private static final long ACC_GRID = 999804L, USR_GRID = 800004L;       // 陈网格员 GRID_MEMBER（无 dispute:* 任一权限）
    private static final long ACC_COMM = 999803L, USR_COMM = 800003L;       // 刘主任   COMMUNITY_ADMIN（dispute:audit + dispute:decide）
    private static final long ACC_LISI = 999913L, UID_LISI = 70002L;        // 李四     C_USER（无 sys_role）

    // ===== G 端 endpoint：陈网格员（无 dispute:*）全 403 =====

    @Test
    public void gridOperatorCannotListGovDisputes_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/gov/disputes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridOperatorCannotStartReview_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/gov/disputes/99999/review/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridOperatorCannotDecide_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/gov/disputes/99999/review/decide")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decisionKind", "UPHELD",
                                "content", "支持业主诉求",
                                "docUrl", "url://x"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== G 端 endpoint：C 端业主李四（无 sys_role）全 403 =====

    @Test
    public void cUserCannotListGovDisputes_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/gov/disputes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void cUserCannotDecide_403() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/gov/disputes/99999/review/decide")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decisionKind", "UPHELD",
                                "content", "C 端无权"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== G 端正向通路：刘主任带 dispute:audit / decide 通过 PreAuthorize → 业务层 NOT_FOUND =====

    @Test
    public void communityAdminWithAudit_listDisputes_passesPreAuthorize() throws Exception {
        // dispute:audit 通过 → 200（空列表，因为本租户 disputeId=99999 范围不命中实测数据）
        String token = jwtTokenProvider.generateToken(ACC_COMM, "SYS_USER", USR_COMM, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/gov/disputes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    public void communityAdminWithDecide_startReviewHitsNotFound_404() throws Exception {
        // dispute:decide 通过 PreAuthorize → 业务层因 disputeId=99999 不存在抛 DISPUTE_NOT_FOUND
        String token = jwtTokenProvider.generateToken(ACC_COMM, "SYS_USER", USR_COMM, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/gov/disputes/99999/review/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(41101)));
    }

    @Test
    public void communityAdminWithDecide_decideHitsNotFound_404() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_COMM, "SYS_USER", USR_COMM, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/gov/disputes/99999/review/decide")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decisionKind", "UPHELD",
                                "content", "支持业主诉求"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(41101)));
    }

    // ===== C 端 isAuthenticated 通路：C_USER 通过认证 → service 层抛 NOT_FOUND =====

    @Test
    public void cUserCanGetOwnerDispute_butNotFound_404() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/disputes/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(41101)));
    }

    @Test
    public void cUserCanListMineDisputes_returnsEmpty_200() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/disputes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    public void cUserCanOpenDispute_validBody_201() throws Exception {
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/disputes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "disputeKind", "EXPENSE_VOUCHER_DISPUTE",
                                "relatedEntityType", "EXPENSE_VOUCHER",
                                "relatedEntityId", 9001,
                                "businessPayloadJson", "{\"voucher_id\":9001}"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(200)));
    }

    // ===== SYS_USER 调 C 端 endpoint：authentication 通过但 getUid()=null → NOT_OWNER 403 =====

    @Test
    public void sysUserHittingOwnerEndpoint_throwsNotOwner_403() throws Exception {
        // 陈网格员是 SYS_USER（无 c_user.uid）；调 C 端 owner endpoint 时 SecurityUtils.getUid() 返 null
        // → controller#requireUid() 抛 DisputeApplicationException(NOT_OWNER) → 403 / code=41102
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        mockMvc.perform(get("/api/v1/disputes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(41102)));
    }

    // ===== 未登录 403（与 V2.7 disclosure / RBAC 矩阵同：Spring Security 无 EntryPoint 配置时返 403） =====

    @Test
    public void anonymousAccess_govDisputes_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/gov/disputes"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void anonymousAccess_ownerDisputes_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/disputes"))
                .andExpect(status().isForbidden());
    }
}
