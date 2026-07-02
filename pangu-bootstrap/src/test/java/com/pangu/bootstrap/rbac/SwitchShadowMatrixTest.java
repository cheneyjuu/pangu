package com.pangu.bootstrap.rbac;

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

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端 SYS_USER 工作分身切卡矩阵覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class SwitchShadowMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TENANT_RUSHI = 10001L;
    private static final long ACC_LIU = 999803L;
    private static final long USR_LIU_COMMUNITY = 800003L;
    private static final long USR_LIU_GRID = 800006L;
    private static final long USR_CHEN_GRID = 800004L;

    @BeforeEach
    @AfterEach
    public void resetLiuDefaultShadow() {
        jdbcTemplate.update("""
                UPDATE t_account
                SET last_active_identity_id = ?, last_active_identity_type = 'SYS_USER'
                WHERE account_id = ?
                """, USR_LIU_COMMUNITY, ACC_LIU);
    }

    @Test
    public void listSysUserShadows_returnsAllShadowsForCurrentAccount() throws Exception {
        String token = jwtTokenProvider.generateToken(
                ACC_LIU, "SYS_USER", USR_LIU_COMMUNITY, TENANT_RUSHI);

        mockMvc.perform(get("/api/v1/auth/shadows")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.shadows", hasSize(2)))
                .andExpect(jsonPath("$.data.shadows[0].user_id", is((int) USR_LIU_COMMUNITY)))
                .andExpect(jsonPath("$.data.shadows[0].role_key", is("COMMUNITY_ADMIN")))
                .andExpect(jsonPath("$.data.shadows[0].active", is(true)))
                .andExpect(jsonPath("$.data.shadows[1].user_id", is((int) USR_LIU_GRID)))
                .andExpect(jsonPath("$.data.shadows[1].role_key", is("GRID_MEMBER")))
                .andExpect(jsonPath("$.data.shadows[1].active", is(false)));
    }

    @Test
    public void switchToOwnedSysUserShadow_succeedsAndReturnsGridToken() throws Exception {
        String token = jwtTokenProvider.generateToken(
                ACC_LIU, "SYS_USER", USR_LIU_COMMUNITY, TENANT_RUSHI);
        Map<String, Object> body = Map.of("targetUserId", USR_LIU_GRID);

        String response = mockMvc.perform(post("/api/v1/auth/switch-shadow")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.new_access_token", notNullValue()))
                .andExpect(jsonPath("$.data.user_info.account_id", is((int) ACC_LIU)))
                .andExpect(jsonPath("$.data.user_info.identity_type", is("SYS_USER")))
                .andExpect(jsonPath("$.data.user_info.active_identity_id", is((int) USR_LIU_GRID)))
                .andExpect(jsonPath("$.data.user_info.role_key", is("GRID_MEMBER")))
                .andExpect(jsonPath("$.data.active_shadow.active", is(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String newToken = objectMapper.readTree(response)
                .path("data")
                .path("new_access_token")
                .asText();
        assertEquals(ACC_LIU, jwtTokenProvider.getAccountIdFromToken(newToken));
        assertEquals("SYS_USER", jwtTokenProvider.getIdentityTypeFromToken(newToken));
        assertEquals(USR_LIU_GRID, jwtTokenProvider.getActiveIdentityIdFromToken(newToken));
    }

    @Test
    public void switchToOtherAccountsSysUserShadow_forbidden() throws Exception {
        String token = jwtTokenProvider.generateToken(
                ACC_LIU, "SYS_USER", USR_LIU_COMMUNITY, TENANT_RUSHI);
        Map<String, Object> body = Map.of("targetUserId", USR_CHEN_GRID);

        mockMvc.perform(post("/api/v1/auth/switch-shadow")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }
}
