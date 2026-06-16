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
        // 13800138000 是网格员王小二绑定的自然人手机号
        Map<String, Object> request = Map.of(
                "username", "13800138000",
                "smsCode", "123456"
        );

        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.access_token", notNullValue()))
                .andExpect(jsonPath("$.data.user_info.uid", is(101)))
                .andReturn().getResponse().getContentAsString();

        // 解析并校验 Token 内部的角色和权限
        Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        String token = (String) data.get("access_token");

        assert jwtTokenProvider.validateToken(token);
        Claims claims = jwtTokenProvider.parseToken(token);
        List<String> roles = claims.get("roles", List.class);
        List<String> permissions = claims.get("permissions", List.class);

        // 网格员王小二 (uid=101) 从数据库关联 sys_user 查询其角色为 grid_manager
        assert roles != null && roles.contains("grid_manager");
        assert permissions != null && permissions.contains("repair:view");
        assert permissions.contains("election:vote");
    }

    @Test
    public void testLoginWithNormalUser() throws Exception {
        // 13900139000 是普通业主李四的手机号，名下无任何管理端 sys_user 绑定
        Map<String, Object> request = Map.of(
                "username", "13900139000",
                "smsCode", "123456"
        );

        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.access_token", notNullValue()))
                .andExpect(jsonPath("$.data.user_info.uid", is(102)))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        String token = (String) data.get("access_token");

        assert jwtTokenProvider.validateToken(token);
        Claims claims = jwtTokenProvider.parseToken(token);
        List<String> roles = claims.get("roles", List.class);
        List<String> permissions = claims.get("permissions", List.class);

        // 普通业主李四，没有管理端角色
        assert roles == null || roles.isEmpty();
        assert permissions != null && permissions.contains("election:vote");
        assert !permissions.contains("repair:view");
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
                "username", "13800138000",
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
        // 张三 (uid=101) 状态正常，校验应该通过
        String token = jwtTokenProvider.generateToken(101L, 9001L, List.of("grid_manager"), List.of("election:vote", "repair:view"), 1);

        mockMvc.perform(get("/api/v1/election/candidates/me/eligibility")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.msg", is("资格校验完成")))
                .andExpect(jsonPath("$.data.eligible", is(true)));
    }

    @Test
    public void testCheckQualificationFailedForArrears() throws Exception {
        // 王五 (uid=103) 名下有欠费房产，在 SCHEME_C 限制下应该被拦截并返回 403 Forbidden
        String token = jwtTokenProvider.generateToken(103L, 9001L, List.of(), List.of("election:vote"), 1);

        mockMvc.perform(get("/api/v1/election/candidates/me/eligibility")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()) // 403
                .andExpect(jsonPath("$.code", is(403)))
                .andExpect(jsonPath("$.data.policy_type", is("SCHEME_C")))
                .andExpect(jsonPath("$.data.restriction_target", is("LIMIT_ELECTION_RIGHT")))
                .andExpect(jsonPath("$.data.is_voting_rights_retained", is(true)));
    }
}
