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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CommunitySettingsTest {

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
    private static final long ACC_DIRECTOR = 999811L;
    private static final long USR_DIRECTOR = 800101L;
    private static final long ACC_PROPERTY_MANAGER = 999821L;
    private static final long USR_PROPERTY_MANAGER = 800201L;
    private static final long ACC_PROPERTY_STAFF = 999822L;
    private static final long USR_PROPERTY_STAFF = 800202L;
    private static final String TEST_REASON_PREFIX = "IT-community-settings-";

    @BeforeEach
    @AfterEach
    public void cleanup() {
        jdbcTemplate.update("""
                UPDATE t_tenant_community
                SET repair_estimate_required = 0,
                    building_repair_default_decision_channel = 'WECHAT'
                WHERE tenant_id = ?
                """, TENANT_RUSHI);
        jdbcTemplate.update("""
                DELETE FROM t_tenant_community_settings_audit
                WHERE tenant_id = ?
                  AND (
                    operation_type IN ('SUBMIT_DENOMINATOR_REVIEW', 'RECALCULATE_DENOMINATOR')
                    OR (
                      operation_type = 'UPDATE_RULES'
                      AND jsonb_exists(payload_json, 'buildingRepairDefaultDecisionChannel')
                    )
                  )
                """, TENANT_RUSHI);
        jdbcTemplate.update("""
                DELETE FROM t_tenant_denominator_review_request
                WHERE tenant_id = ?
                  AND reason LIKE ?
                """, TENANT_RUSHI, TEST_REASON_PREFIX + "%");
    }

    @Test
    public void govCanReadCommunitySettingsAndMenuContainsCommunitySettings() throws Exception {
        String token = token(ACC_SUPER, USR_SUPER, null);

        mockMvc.perform(get("/api/v1/admin/community-settings")
                        .param("tenantId", String.valueOf(TENANT_RUSHI))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.header.tenantId", is((int) TENANT_RUSHI)))
                .andExpect(jsonPath("$.data.permissions.government", is(true)))
                .andExpect(jsonPath("$.data.organization.communityName", is("求是居民委员会")))
                .andExpect(jsonPath("$.data.assetLedger.liveLedgerStats.buildingCount", is(7)))
                .andExpect(jsonPath("$.data.assetLedger.buildings.length()", is(7)))
                .andExpect(jsonPath("$.data.assetLedger.buildings[6].buildingId", is(30007)))
                .andExpect(jsonPath("$.data.assetLedger.buildings[6].buildingName", is("冷启楼")))
                .andExpect(jsonPath("$.data.rules.buildingRepairDefaultDecisionChannel", is("WECHAT")))
                .andExpect(jsonPath("$.data.rules.currentPolicy.policyCode", is("SH_DEFAULT_MAJORITY_2026")));

        String response = mockMvc.perform(get("/api/v1/auth/menus")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode menus = objectMapper.readTree(response).path("data");
        List<String> pageIds = new ArrayList<>();
        for (JsonNode menu : menus) {
            for (JsonNode page : menu.path("pages")) {
                pageIds.add(page.path("id").asText());
            }
        }
        assertTrue(pageIds.contains("community-settings"));
    }

    @Test
    public void propertyStaffCannotSeeOrganizationOrRules() throws Exception {
        String token = token(ACC_PROPERTY_STAFF, USR_PROPERTY_STAFF, TENANT_RUSHI);

        mockMvc.perform(get("/api/v1/admin/community-settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organization").doesNotExist())
                .andExpect(jsonPath("$.data.rules").doesNotExist())
                .andExpect(jsonPath("$.data.assetLedger.propertyAreaName").isNotEmpty())
                .andExpect(jsonPath("$.data.permissions.propertyRole", is(true)))
                .andExpect(jsonPath("$.data.permissions.canViewRules", is(false)));
    }

    @Test
    public void propertyManagerCannotChangeLegalArea() throws Exception {
        String token = token(ACC_PROPERTY_MANAGER, USR_PROPERTY_MANAGER, TENANT_RUSHI);

        mockMvc.perform(patch("/api/v1/admin/community-settings/asset-ledger")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "propertyAreaName", "求是花园物业管理区域",
                                "totalExclusiveArea", "999.00"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(42602)));
    }

    @Test
    public void govCanRequireInternalRepairEstimateBeforeInvitation() throws Exception {
        String token = token(ACC_SUPER, USR_SUPER, null);

        mockMvc.perform(patch("/api/v1/admin/community-settings/rules")
                        .param("tenantId", String.valueOf(TENANT_RUSHI))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("repairEstimateRequired", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules.repairEstimateRequired", is(true)));
    }

    @Test
    public void govCanSetBuildingRepairDefaultDecisionChannel() throws Exception {
        String token = token(ACC_SUPER, USR_SUPER, null);

        mockMvc.perform(patch("/api/v1/admin/community-settings/rules")
                        .param("tenantId", String.valueOf(TENANT_RUSHI))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "buildingRepairDefaultDecisionChannel", "ONLINE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules.buildingRepairDefaultDecisionChannel", is("ONLINE")));

        mockMvc.perform(get("/api/v1/admin/community-settings")
                        .param("tenantId", String.valueOf(TENANT_RUSHI))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rules.buildingRepairDefaultDecisionChannel", is("ONLINE")));
    }

    @Test
    public void committeeDirectorCanSubmitDenominatorReview() throws Exception {
        String token = token(ACC_DIRECTOR, USR_DIRECTOR, TENANT_RUSHI);
        String reason = TEST_REASON_PREFIX + System.nanoTime();

        mockMvc.perform(post("/api/v1/admin/community-settings/denominator/review-requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "requestedTotalArea", "376.50",
                                "requestedOwnerCount", 3,
                                "requestedUnitCount", 4,
                                "reason", reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.denominator.pendingReviewRequests[0].reason", is(reason)))
                .andExpect(jsonPath("$.data.permissions.canRequestDenominatorReview", is(true)));
    }

    @Test
    public void govRecalculateIncrementsStatisticsVersion() throws Exception {
        String token = token(ACC_SUPER, USR_SUPER, null);
        Long before = jdbcTemplate.queryForObject("""
                SELECT statistics_version
                FROM t_tenant_community
                WHERE tenant_id = ?
                """, Long.class, TENANT_RUSHI);

        mockMvc.perform(post("/api/v1/admin/community-settings/denominator/recalculate")
                        .param("tenantId", String.valueOf(TENANT_RUSHI))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.header.tenantId", is((int) TENANT_RUSHI)));

        Long after = jdbcTemplate.queryForObject("""
                SELECT statistics_version
                FROM t_tenant_community
                WHERE tenant_id = ?
                """, Long.class, TENANT_RUSHI);
        assertTrue(after != null && before != null && after > before);
    }

    private String token(long accountId, long userId, Long tenantId) {
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, tenantId);
    }
}
