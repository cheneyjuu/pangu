package com.pangu.bootstrap.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testLoginAndRolesGridManager() throws Exception {
        // V1.1 seed: 陈网格员 phone=13800000004 / account_id=999804 / sys_user.user_id=800004 / role=GRID_MEMBER
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
    public void govOperatorCanSeeSubjectProposalMenuFromDynamicPermissionRules() throws Exception {
        Integer existed = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_role_menu WHERE role_id = 14 AND menu_id = 5010",
                Integer.class);
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = 14 AND menu_id = 5010");

        try {
            Map<String, Object> request = Map.of(
                    "username", "13800000005",
                    "smsCode", "123456"
            );

            String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user_info.role_key", is("GOV_OPERATOR")))
                    .andExpect(jsonPath("$.data.user_info.dept_type", is(2)))
                    .andExpect(jsonPath("$.data.user_info.permissions", hasItem("voting:subject:create:election")))
                    .andExpect(jsonPath("$.data.user_info.menu_permissions", hasItem("subject-proposal")))
                    .andReturn().getResponse().getContentAsString();

            Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            String token = (String) data.get("access_token");

            String menusJson = mockMvc.perform(get("/api/v1/auth/menus")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            Map<String, Object> menusResponse = objectMapper.readValue(menusJson, Map.class);
            List<Map<String, Object>> modules = (List<Map<String, Object>>) menusResponse.get("data");
            Map<String, Object> votingModule = modules.stream()
                    .filter(module -> "governance".equals(module.get("id")))
                    .findFirst()
                    .orElseThrow();
            assertEquals("投票管理", votingModule.get("label"));
            List<Map<String, Object>> votingPages = (List<Map<String, Object>>) votingModule.get("pages");
            Map<String, Object> votingPage = votingPages.stream()
                    .filter(page -> "voting".equals(page.get("id")))
                    .findFirst()
                    .orElseThrow();
            assertEquals("议题投票看板", votingPage.get("label"));
            boolean hasSubjectProposal = modules.stream()
                    .flatMap(module -> ((List<Map<String, Object>>) module.get("pages")).stream())
                    .anyMatch(page -> "subject-proposal".equals(page.get("id")));
            assertTrue(hasSubjectProposal, "GOV_OPERATOR should see subject-proposal via dynamic permission rules");
        } finally {
            if (existed != null && existed > 0) {
                jdbcTemplate.update("""
                        INSERT INTO sys_role_menu(role_id, menu_id)
                        VALUES (14, 5010)
                        ON CONFLICT (role_id, menu_id) DO NOTHING
                        """);
            } else {
                jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = 14 AND menu_id = 5010");
            }
        }
    }

    @Test
    public void streetAdminLoginCarriesDefaultTenantForTenantBoundMenus() throws Exception {
        Map<String, Object> request = Map.of(
                "username", "13800000001",
                "smsCode", "123456"
        );

        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.role_key", is("GOV_SUPER_ADMIN")))
                .andExpect(jsonPath("$.data.user_info.tenant_id", is(10001)))
                .andExpect(jsonPath("$.data.user_info.permissions", not(hasItem("repair:workorder:field"))))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        String token = (String) data.get("access_token");

        Claims claims = jwtTokenProvider.parseToken(token);
        assertEquals(10001L, claims.get("tenantId", Number.class).longValue());

        mockMvc.perform(get("/api/v1/voting-subjects")
                        .param("page", "1")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        mockMvc.perform(get("/api/v1/gov/disputes")
                        .param("limit", "1")
                        .param("offset", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    public void propertyManagerNavigationGroupsRepairsUnderPropertyAndRevenueUnderFinance() throws Exception {
        Map<String, Object> request = Map.of(
                "username", "13800000021",
                "smsCode", "123456"
        );

        String responseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.role_key", is("PROPERTY_MANAGER")))
                .andExpect(jsonPath("$.data.user_info.permissions", hasItem("repair:workorder:read")))
                .andExpect(jsonPath("$.data.user_info.permissions", hasItem("fund:account:read")))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
        Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
        String token = (String) data.get("access_token");

        String menusJson = mockMvc.perform(get("/api/v1/auth/menus")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> menusResponse = objectMapper.readValue(menusJson, Map.class);
        List<Map<String, Object>> modules = (List<Map<String, Object>>) menusResponse.get("data");

        assertTrue(modules.stream().noneMatch(module -> "assets".equals(module.get("id"))),
                "旧资产与维修一级菜单不应继续下发");

        Map<String, Object> propertyModule = modules.stream()
                .filter(module -> "property".equals(module.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("物业管理", propertyModule.get("label"));
        List<Map<String, Object>> propertyPages = (List<Map<String, Object>>) propertyModule.get("pages");
        assertTrue(propertyPages.stream().anyMatch(page -> "assets".equals(page.get("id"))));
        assertTrue(propertyPages.stream().anyMatch(page -> "work-orders".equals(page.get("id"))));
        assertTrue(propertyPages.stream().anyMatch(page -> "engineering".equals(page.get("id"))));
        assertTrue(propertyPages.stream().noneMatch(page -> "property-mgmt".equals(page.get("id"))));

        Map<String, Object> financeModule = modules.stream()
                .filter(module -> "finance".equals(module.get("id")))
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> financePages = (List<Map<String, Object>>) financeModule.get("pages");
        Map<String, Object> revenueEntry = financePages.stream()
                .filter(page -> "property-mgmt".equals(page.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("收益与开支录入", revenueEntry.get("label"));
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
    public void testOwnerFaceAuthContextRejectsMockIdentity() throws Exception {
        String token = jwtTokenProvider.generateToken(999913L, "C_USER", 70002L, 10001L);

        mockMvc.perform(post("/api/v1/me/auth/face/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.eligible", is(false)))
                .andExpect(jsonPath("$.data.realName", nullValue()))
                .andExpect(jsonPath("$.data.idCardNumber", nullValue()))
                .andExpect(jsonPath("$.data.reason", containsString("测试或占位数据")));
    }

    @Test
    public void testOwnerFaceAuthContextUsesRegisteredIdentity() throws Exception {
        jdbcTemplate.update("""
                UPDATE t_account
                SET real_name = ?, id_card_encrypted = ?
                WHERE account_id = 999913
                """, "李四", "110101199003070011");

        try {
            String token = jwtTokenProvider.generateToken(999913L, "C_USER", 70002L, 10001L);

            mockMvc.perform(post("/api/v1/me/auth/face/context")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is(200)))
                    .andExpect(jsonPath("$.data.eligible", is(true)))
                    .andExpect(jsonPath("$.data.realName", nullValue()))
                    .andExpect(jsonPath("$.data.idCardNumber", nullValue()))
                    .andExpect(jsonPath("$.data.maskedRealName", is("李*")))
                    .andExpect(jsonPath("$.data.maskedIdCardNumber", is("110***********0011")))
                    .andExpect(jsonPath("$.data.provider", is("TENCENT_FACEID")))
                    .andExpect(jsonPath("$.data.bizToken", startsWith("mock-face-biz-token-")))
                    .andExpect(jsonPath("$.data.providerUrl", startsWith("weixin://mock-face-auth/")))
                    .andExpect(jsonPath("$.data.expiresInSeconds", is(7200)))
                    .andExpect(jsonPath("$.data.reason", nullValue()));
        } finally {
            jdbcTemplate.update("""
                    UPDATE t_account
                    SET real_name = ?, id_card_encrypted = ?
                    WHERE account_id = 999913
                    """, "MOCK_李四", "MOCK_ID_999913");
        }
    }

    @Test
    public void testSysUserCannotPrepareOwnerFaceAuthContext() throws Exception {
        String token = jwtTokenProvider.generateToken(999804L, "SYS_USER", 800004L, 10001L);

        mockMvc.perform(post("/api/v1/me/auth/face/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void testOwnerFaceAuthMockRecordsTestCaptureWithoutUpgradingCUser() throws Exception {
        String providerRequestId = "mock-face-biz-token-controller-test-70002";
        jdbcTemplate.update("DELETE FROM t_owner_face_auth_attestation WHERE provider = ? AND provider_request_id = ?",
                "TENCENT_FACEID", providerRequestId);
        jdbcTemplate.update("UPDATE c_user SET auth_level = 2 WHERE uid = 70002");
        jdbcTemplate.update("""
                UPDATE t_account
                SET real_name = ?, id_card_encrypted = ?
                WHERE account_id = 999913
                """, "李四", "110101199003070011");

        try {
            String token = jwtTokenProvider.generateToken(999913L, "C_USER", 70002L, 10001L);
            Map<String, Object> request = Map.of(
                    "provider", "TENCENT_FACEID",
                    "bizToken", providerRequestId
            );

            mockMvc.perform(post("/api/v1/me/auth/face")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code", is(200)))
                    .andExpect(jsonPath("$.data.verified", is(false)))
                    .andExpect(jsonPath("$.data.testOnly", is(true)))
                    .andExpect(jsonPath("$.data.attestationId", is("TENCENT_FACEID:" + providerRequestId)))
                    .andExpect(jsonPath("$.data.newAuthLevel", is(2)))
                    .andExpect(jsonPath("$.data.message", containsString("测试摄像头采集")));

            Integer authLevel = jdbcTemplate.queryForObject(
                    "SELECT auth_level FROM c_user WHERE uid = 70002", Integer.class);
            Integer attestationCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM t_owner_face_auth_attestation
                    WHERE uid = 70002
                      AND provider = 'TENCENT_FACEID'
                      AND provider_request_id = ?
                      AND verified = 0
                      AND auth_level_after = 2
                    """, Integer.class, providerRequestId);
            assert authLevel != null && authLevel == 2;
            assert attestationCount != null && attestationCount == 1;
        } finally {
            jdbcTemplate.update("UPDATE c_user SET auth_level = 2 WHERE uid = 70002");
            jdbcTemplate.update("DELETE FROM t_owner_face_auth_attestation WHERE provider = ? AND provider_request_id = ?",
                    "TENCENT_FACEID", providerRequestId);
            jdbcTemplate.update("""
                    UPDATE t_account
                    SET real_name = ?, id_card_encrypted = ?
                    WHERE account_id = 999913
                    """, "MOCK_李四", "MOCK_ID_999913");
        }
    }

    @Test
    public void testSysUserCannotSubmitOwnerFaceAuth() throws Exception {
        String token = jwtTokenProvider.generateToken(999804L, "SYS_USER", 800004L, 10001L);
        Map<String, Object> request = Map.of(
                "provider", "wechat",
                "bizToken", "mock-face-biz-token-sys-user-forbidden"
        );

        mockMvc.perform(post("/api/v1/me/auth/face")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void testOwnerFaceAuthRejectsUnsupportedProvider() throws Exception {
        String token = jwtTokenProvider.generateToken(999913L, "C_USER", 70002L, 10001L);
        Map<String, Object> request = Map.of(
                "provider", "aliyun",
                "bizToken", "mock-face-biz-token-not-enabled"
        );

        mockMvc.perform(post("/api/v1/me/auth/face")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.msg", containsString("TENCENT_FACEID")));
    }

    @Test
    public void testOwnerIdCardOcrRecognizesFrontSide() throws Exception {
        String token = jwtTokenProvider.generateToken(999913L, "C_USER", 70002L, 10001L);
        Map<String, Object> request = Map.of(
                "imageBase64", "MOCK:李四:110101199003070011",
                "cardSide", "FRONT"
        );

        mockMvc.perform(post("/api/v1/me/auth/l2/ocr")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.recognized", is(true)))
                .andExpect(jsonPath("$.data.provider", is("MOCK")))
                .andExpect(jsonPath("$.data.realName", is("李四")))
                .andExpect(jsonPath("$.data.idCardNumber", is("110101199003070011")))
                .andExpect(jsonPath("$.data.maskedIdCardNumber", is("110***********0011")));
    }

    @Test
    public void testOwnerRealNameRejectsInvalidIdChecksum() throws Exception {
        String token = jwtTokenProvider.generateToken(999913L, "C_USER", 70002L, 10001L);
        Map<String, Object> request = Map.of(
                "realName", "李四",
                "idCardNumber", "110101199003070012"
        );

        mockMvc.perform(post("/api/v1/me/auth/l2")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.msg", containsString("校验码")));
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
