// 关联业务：验证房屋产权名册导入、空间汇总和业主房产绑定审核的冷启动闭环。
package com.pangu.bootstrap.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PropertyBindingFlowTest {

    private static final Long TENANT_ID = 10001L;
    private static final String ADMIN_PHONE = "13800000003";
    private static final String AUTO_PHONE = "13900009991";
    private static final String MANUAL_PHONE = "13900009992";
    private static final String REJECT_PHONE = "13900009993";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("""
                DELETE FROM c_owner_property_claim
                 WHERE applicant_phone IN (?, ?, ?)
                    OR room_name IN ('冷启1101', '冷启1102', '冷启1103')
                """, AUTO_PHONE, MANUAL_PHONE, REJECT_PHONE);
        jdbcTemplate.update("""
                DELETE FROM c_owner_property
                 WHERE room_id IN (
                     SELECT room_id
                     FROM c_property_roster
                     WHERE room_name IN ('冷启1101', '冷启1102', '冷启1103')
                 )
                """);
        jdbcTemplate.update("DELETE FROM c_property_roster WHERE room_name IN ('冷启1101', '冷启1102', '冷启1103')");
        jdbcTemplate.update("""
                DELETE FROM c_user
                 WHERE account_id IN (
                     SELECT account_id FROM t_account WHERE phone IN (?, ?, ?)
                 )
                """, AUTO_PHONE, MANUAL_PHONE, REJECT_PHONE);
        jdbcTemplate.update("DELETE FROM t_account WHERE phone IN (?, ?, ?)", AUTO_PHONE, MANUAL_PHONE, REJECT_PHONE);
    }

    @Test
    void coldStartExactRosterMatch_autoBindsProperty() throws Exception {
        String adminToken = login(ADMIN_PHONE, "B");
        String ownerToken = login(AUTO_PHONE, "AUTO");

        mockMvc.perform(get("/api/v1/me/properties")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));

        verifyL2(ownerToken, "冷启业主", "310101199001011234");

        importRoster(adminToken, "冷启楼", "一单元", "冷启1101", "冷启业主", AUTO_PHONE);

        mockMvc.perform(get("/api/v1/admin/property-roster/topology")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // 10001 复用系统种子名册，汇总断言必须定位本用例创建的楼栋，不能假定全租户总数。
                .andExpect(jsonPath("$.data.buildings[*].buildingName", hasItem("冷启楼")))
                .andExpect(jsonPath("$.data.buildings[?(@.buildingName == '冷启楼')].householdCount", hasItem(1)))
                .andExpect(jsonPath("$.data.buildings[?(@.buildingName == '冷启楼')].totalArea", hasItem(88.66)))
                .andExpect(jsonPath("$.data.buildings[*].units[*].unitName", hasItem("一单元")));

        mockMvc.perform(get("/api/v1/admin/property-roster/registered-owners")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.registeredOwnerName == '冷启业主')].registeredOwnerPhone",
                        hasItem(AUTO_PHONE)))
                .andExpect(jsonPath("$.data[?(@.registeredOwnerName == '冷启业主')].propertyCount",
                        hasItem(1)))
                .andExpect(jsonPath("$.data[?(@.registeredOwnerName == '冷启业主')].properties[0].roomName",
                        hasItem("冷启1101")));

        mockMvc.perform(post("/api/v1/me/property-bindings/claims")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rosterId", rosterId("冷启1101"),
                                "jointOwnership", false,
                                "votingDelegate", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("BOUND")))
                .andExpect(jsonPath("$.data.opid", notNullValue()));

        mockMvc.perform(get("/api/v1/me/properties")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].roomName", is("冷启1101")))
                .andExpect(jsonPath("$.data[0].verifyType", is("ROSTER_AUTO")));
    }

    @Test
    void coldStartMismatchClaim_canBeManuallyApprovedWithManualProvenance() throws Exception {
        String adminToken = login(ADMIN_PHONE, "B");
        String ownerToken = login(MANUAL_PHONE, "AUTO");
        verifyL2(ownerToken, "申报业主", "31010119900102123X");
        importRoster(adminToken, "冷启楼", "一单元", "冷启1102", "名册父母", "13900009998");

        mockMvc.perform(post("/api/v1/me/property-bindings/claims")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rosterId", rosterId("冷启1102"),
                                "jointOwnership", true,
                                "votingDelegate", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("NEED_EVIDENCE")));

        String pendingJson = mockMvc.perform(post("/api/v1/me/property-bindings/claims")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rosterId", rosterId("冷启1102"),
                                "jointOwnership", true,
                                "votingDelegate", true,
                                "proofType", "PROPERTY_CERT",
                                "proofImagesBase64", List.of("data:image/jpeg;base64,TEST")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_VERIFY")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> pendingRoot = objectMapper.readValue(pendingJson, Map.class);
        Map<?, ?> pendingData = (Map<?, ?>) pendingRoot.get("data");
        Long claimId = ((Number) pendingData.get("claimId")).longValue();

        mockMvc.perform(get("/api/v1/admin/property-binding-claims?status=PENDING_VERIFY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].claimId", hasItem(claimId.intValue())));

        mockMvc.perform(post("/api/v1/admin/property-binding-claims/{claimId}/approve", claimId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimStatus", is("APPROVED")))
                .andExpect(jsonPath("$.data.boundOpid", notNullValue()));

        Integer manualCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM c_owner_property
                WHERE uid = (SELECT uid FROM c_user cu JOIN t_account a ON a.account_id = cu.account_id WHERE a.phone = ?)
                  AND verify_type = 'MANUAL'
                  AND account_status = 1
                """, Integer.class, MANUAL_PHONE);
        org.junit.jupiter.api.Assertions.assertEquals(1, manualCount);
    }

    @Test
    void coldStartMismatchClaim_rejectRequiresConcreteReasonAndDoesNotBind() throws Exception {
        String adminToken = login(ADMIN_PHONE, "B");
        String ownerToken = login(REJECT_PHONE, "AUTO");
        verifyL2(ownerToken, "驳回业主", "310101199001031235");
        importRoster(adminToken, "冷启楼", "一单元", "冷启1103", "旧业主", "13900009999");

        String pendingJson = mockMvc.perform(post("/api/v1/me/property-bindings/claims")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rosterId", rosterId("冷启1103"),
                                "jointOwnership", false,
                                "votingDelegate", true,
                                "proofType", "PROPERTY_CERT",
                                "proofImagesBase64", List.of("data:image/jpeg;base64,BLURRY")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_VERIFY")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> pendingRoot = objectMapper.readValue(pendingJson, Map.class);
        Map<?, ?> pendingData = (Map<?, ?>) pendingRoot.get("data");
        Long claimId = ((Number) pendingData.get("claimId")).longValue();

        mockMvc.perform(post("/api/v1/admin/property-binding-claims/{claimId}/reject", claimId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reasonCode", "MATERIAL_BLURRY"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/property-binding-claims/{claimId}/reject", claimId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reasonCode", "MATERIAL_BLURRY",
                                "reason", "照片不清晰，无法核对姓名与房号"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimStatus", is("REJECTED")))
                .andExpect(jsonPath("$.data.rejectReason", is("照片不清晰，无法核对姓名与房号")));

        Integer boundCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM c_owner_property
                WHERE uid = (SELECT uid FROM c_user cu JOIN t_account a ON a.account_id = cu.account_id WHERE a.phone = ?)
                  AND account_status = 1
                """, Integer.class, REJECT_PHONE);
        org.junit.jupiter.api.Assertions.assertEquals(0, boundCount);
    }

    @Test
    void adminNavigation_ordersModulesAndKeepsBindingReviewUnderSystemSettings() {
        List<String> topMenus = jdbcTemplate.queryForList("""
                SELECT menu_name
                FROM sys_menu
                WHERE parent_id = 0
                  AND visible = 1
                  AND status = '0'
                  AND menu_id IN (1000, 2000, 5000, 8000, 4000, 3000, 6000, 9000)
                ORDER BY order_num ASC, menu_id ASC
                """, String.class);
        org.junit.jupiter.api.Assertions.assertEquals(List.of(
                "工作台",
                "物业管理",
                "投票管理",
                "公告管理",
                "选举管理",
                "委员会操作",
                "财务监督",
                "系统管理"
        ), topMenus);

        Long reviewParent = jdbcTemplate.queryForObject("""
                SELECT parent_id
                FROM sys_menu
                WHERE route_id = 'property-binding-review'
                """, Long.class);
        Long rosterParent = jdbcTemplate.queryForObject("""
                SELECT parent_id
                FROM sys_menu
                WHERE route_id = 'property-roster-import'
                """, Long.class);
        Integer communitySpaceVisible = jdbcTemplate.queryForObject("""
                SELECT visible
                FROM sys_menu
                WHERE route_id = 'community-space'
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(9000L, reviewParent);
        org.junit.jupiter.api.Assertions.assertEquals(9000L, rosterParent);
        org.junit.jupiter.api.Assertions.assertEquals(0, communitySpaceVisible);
    }

    private String login(String phone, String clientPortal) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", phone,
                                "smsCode", "123456",
                                "loginType", 1,
                                "clientPortal", clientPortal
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token", notNullValue()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> root = objectMapper.readValue(json, Map.class);
        Map<?, ?> data = (Map<?, ?>) root.get("data");
        return (String) data.get("access_token");
    }

    private void verifyL2(String token, String realName, String idCard) throws Exception {
        mockMvc.perform(post("/api/v1/me/auth/l2")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "realName", realName,
                                "idCardNumber", idCard
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.auth_level", is(2)));
    }

    private void importRoster(String token,
                              String building,
                              String unit,
                              String room,
                              String ownerName,
                              String ownerPhone) throws Exception {
        mockMvc.perform(post("/api/v1/admin/property-roster/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantId", TENANT_ID,
                                "rows", List.of(Map.of(
                                        "buildingName", building,
                                        "unitName", unit,
                                        "roomName", room,
                                        "buildArea", 88.66,
                                        "registeredOwnerName", ownerName,
                                        "registeredOwnerPhone", ownerPhone
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount", is(1)));
    }

    private Long rosterId(String room) {
        return jdbcTemplate.queryForObject("""
                SELECT roster_id
                FROM c_property_roster
                WHERE room_name = ?
                """, Long.class, room);
    }
}
