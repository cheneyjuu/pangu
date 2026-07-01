package com.pangu.bootstrap.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    public void testLoginAndRolesGridManager() throws Exception {
        // V1.1 seed: 陈网格员 phone=13800000004 / account_id=999804 / sys_user.user_id=800004 / role=GRID_OPERATOR
        Map<String, Object> request = Map.of(
                "username", "13800000004",
                "smsCode", "123456"
        );

        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.access_token", notNullValue()))
                .andExpect(jsonPath("$.data.user_info.active_identity_id", is(800004)))
                .andExpect(jsonPath("$.data.user_info.identity_type", is("SYS_USER")))
                .andReturn().getResponse().getContentAsString();

        // M1 RBAC 重构后：JWT 不嵌 roles / permissions，仅含 accountId / identityType / activeIdentityId / tenantId
        Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        String token = (String) data.get("access_token");

        assert jwtTokenProvider.validateToken(token);
        Claims claims = jwtTokenProvider.parseToken(token);
        // token claims 只该有这四个语义字段，不再含 roles / permissions
        assert claims.get("identityType", String.class).equals("SYS_USER");
        assert claims.get("activeIdentityId", Number.class).longValue() == 800004L;

        // permissions 出现在 response.user_info（来自 UserContextLoader 实时反查）
        Map<String, Object> userInfo = (Map<String, Object>) data.get("user_info");
        assert userInfo.get("dept_type").equals(5);
        List<String> permissions = (List<String>) userInfo.get("permissions");
        assert permissions != null && permissions.contains("voting:subject:publish");
        // V3.20 起 ELECTION 候选人提名只授 GOV_OPERATOR；网格员仅保留催票/责任田相关能力。
        assert !permissions.contains("candidate:nominate");
        assert permissions.contains("waiver:read");
    }

    @Test
    public void testLoginWithNormalUser() throws Exception {
        // V1.1 seed: 李四纯业主 phone=13800000113 / account_id=999913 / c_user.uid=70002（无 sys_user 分身）
        Map<String, Object> request = Map.of(
                "username", "13800000113",
                "smsCode", "123456"
        );

        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.access_token", notNullValue()))
                .andExpect(jsonPath("$.data.user_info.active_identity_id", is(70002)))
                .andExpect(jsonPath("$.data.user_info.identity_type", is("C_USER")))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        String token = (String) data.get("access_token");

        assert jwtTokenProvider.validateToken(token);
        Claims claims = jwtTokenProvider.parseToken(token);
        assert claims.get("identityType", String.class).equals("C_USER");
        assert claims.get("activeIdentityId", Number.class).longValue() == 70002L;

        // C 端业主无 sys_role；permissions 集为空（业主侧能力来自 ABAC L 等级，不走 sys_role_permission）
        Map<String, Object> userInfo = (Map<String, Object>) data.get("user_info");
        List<String> permissions = (List<String>) userInfo.get("permissions");
        assert permissions == null || permissions.isEmpty();
    }

    @Test
    public void testLoginWithUnregisteredUser() throws Exception {
        Map<String, Object> request = Map.of(
                "username", "18888888888",
                "smsCode", "123456"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized()) // 401
                .andExpect(jsonPath("$.code", is(401)))
                .andExpect(jsonPath("$.msg", containsString("未注册")));
    }

    @Test
    public void testLoginWithInvalidSmsCode() throws Exception {
        Map<String, Object> request = Map.of(
                "username", "13800000004",
                "smsCode", "invalid_code"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized()) // 401
                .andExpect(jsonPath("$.code", is(401)))
                .andExpect(jsonPath("$.msg", containsString("验证码")));
    }

    @Test
    public void testCheckQualificationSuccess() throws Exception {
        // M1 RBAC 后改用 V1.1 seed 中的张三：c_user.uid=70001 / account_id=999812 / tenant=10001
        String token = jwtTokenProvider.generateToken(999812L, "C_USER", 70001L, 10001L);

        mockMvc.perform(get("/api/v1/election/candidates/me/eligibility")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.msg", is("资格校验完成")))
                .andExpect(jsonPath("$.data.eligible", is(true)));
    }

    @Test
    @org.junit.jupiter.api.Disabled("V1.1 seed 暂无欠费业主 fixture；Task #21 RBAC 测试矩阵补齐后启用")
    public void testCheckQualificationFailedForArrears() throws Exception {
        // SCHEME_C 限制下欠费业主应被拦截 → 403 Forbidden
        String token = jwtTokenProvider.generateToken(999913L, "C_USER", 70002L, 10001L);

        mockMvc.perform(get("/api/v1/election/candidates/me/eligibility")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)))
                .andExpect(jsonPath("$.data.policy_type", is("SCHEME_C")))
                .andExpect(jsonPath("$.data.restriction_target", is("LIMIT_ELECTION_RIGHT")))
                .andExpect(jsonPath("$.data.is_voting_rights_retained", is(true)));
    }
}
