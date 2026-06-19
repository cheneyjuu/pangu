package com.pangu.bootstrap.rbac;

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
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C 端业主跨小区 switch-tenant 鉴权矩阵覆盖。
 *
 * <p>核心契约：
 * <ul>
 *     <li>仅 {@code identityType=C_USER} 可调用，否则 403 FORBIDDEN；</li>
 *     <li>目标 tenant 下当前 uid 必须有 c_owner_property 绑定，否则 403 UNAUTHORIZED_TENANT；</li>
 *     <li>缺失 / 损坏 Authorization header → 401 TOKEN_MISSING / UNAUTHORIZED；</li>
 *     <li>合法切换返回 new_access_token + active_tenant_id + active_opid_list。</li>
 * </ul>
 *
 * <p>使用 V1.1 求是小区 seed：李四 (account_id=999913 / uid=70002 / 在 tenant=10001 有 2 处房产)。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class SwitchTenantMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final long TENANT_RUSHI = 10001L;
    private static final long TENANT_NONEXISTENT = 999999L;

    private static final long ACC_LISI = 999913L, UID_LISI = 70002L;
    private static final long ACC_GRID = 999804L, USR_GRID = 800004L;  // 陈网格员 SYS_USER

    @Test
    public void cUserSwitchToValidTenant_succeeds() throws Exception {
        // 李四在 10001 有房产 → 即使是当前 tenant 也算合法（rebind + 重发 token）
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        Map<String, Object> body = Map.of("targetTenantId", TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/auth/switch-tenant")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.new_access_token", notNullValue()))
                .andExpect(jsonPath("$.data.active_tenant_id", is((int) TENANT_RUSHI)))
                .andExpect(jsonPath("$.data.active_opid_list", notNullValue()));
    }

    @Test
    public void cUserSwitchToTenantWithoutOwnership_403() throws Exception {
        // 李四在 999999 无房产
        String token = jwtTokenProvider.generateToken(ACC_LISI, "C_USER", UID_LISI, TENANT_RUSHI);
        Map<String, Object> body = Map.of("targetTenantId", TENANT_NONEXISTENT);
        mockMvc.perform(post("/api/v1/auth/switch-tenant")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void sysUserCannotSwitchTenant_403() throws Exception {
        // SYS_USER 身份 token，调 switch-tenant → 应被业务层拒（FORBIDDEN）
        String token = jwtTokenProvider.generateToken(ACC_GRID, "SYS_USER", USR_GRID, TENANT_RUSHI);
        Map<String, Object> body = Map.of("targetTenantId", TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/auth/switch-tenant")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void switchTenantWithoutAuthHeader_rejected() throws Exception {
        // 没带 Authorization → JwtAuthenticationFilter 直接拒绝（无 SecurityContext），返回 4xx
        Map<String, Object> body = Map.of("targetTenantId", TENANT_RUSHI);
        mockMvc.perform(post("/api/v1/auth/switch-tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());
    }
}
