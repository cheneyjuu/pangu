// 关联业务：验证业主大会议事规则须由原件支撑、逐项核对，并仅可由当前届主任或副主任启用。
package com.pangu.bootstrap.assembly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OwnersAssemblyRuleFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final long ACCOUNT_DIRECTOR = 999811L;
    private static final long USER_DIRECTOR = 800101L;
    private static final String RULE_NAME_PREFIX = "IT-业主大会议事规则-";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private Long previousActiveRuleId;

    @BeforeEach
    void setUp() {
        previousActiveRuleId = jdbcTemplate.query(
                "SELECT rule_id FROM t_owners_assembly_rule WHERE tenant_id = ? AND status = 'ACTIVE'",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                TENANT);
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2),
                        "owners-assembly-rule-etag"));
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("""
                DELETE FROM t_owners_assembly_rule_field_confirmation
                WHERE rule_id IN (
                    SELECT rule_id FROM t_owners_assembly_rule WHERE rule_name LIKE ?
                )
                """, RULE_NAME_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_owners_assembly_rule_audit
                WHERE rule_id IN (
                    SELECT rule_id FROM t_owners_assembly_rule WHERE rule_name LIKE ?
                )
                """, RULE_NAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_owners_assembly_rule WHERE rule_name LIKE ?", RULE_NAME_PREFIX + "%");
        if (previousActiveRuleId != null) {
            jdbcTemplate.update("""
                    UPDATE t_owners_assembly_rule
                    SET status = 'ACTIVE', update_time = CURRENT_TIMESTAMP
                    WHERE rule_id = ?
                    """, previousActiveRuleId);
        }
    }

    @Test
    void propertyManagerCanPrepareButOnlyDirectorCanConfirmEachFieldAndActivate() throws Exception {
        String propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        String directorToken = token(ACCOUNT_DIRECTOR, USER_DIRECTOR);
        String ruleName = RULE_NAME_PREFIX + System.nanoTime();

        long firstRuleId = createDraft(propertyToken, ruleName, "2026-IT-1", completeConfiguration());
        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + firstRuleId + "/submit")
                        .header("Authorization", "Bearer " + propertyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING_CONFIRMATION")));

        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + firstRuleId + "/activate")
                        .header("Authorization", "Bearer " + propertyToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + firstRuleId + "/activate")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/admin/owners-assembly-rules/" + firstRuleId + "/field-confirmations")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(
                        OwnersAssemblyRuleConfiguration.RuleConfigurationField.values().length)))
                .andExpect(jsonPath("$.data[0].status", is("PENDING")));

        for (OwnersAssemblyRuleConfiguration.RuleConfigurationField field
                : OwnersAssemblyRuleConfiguration.RuleConfigurationField.values()) {
            mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + firstRuleId
                            + "/field-confirmations/" + field.name() + "/confirm")
                            .header("Authorization", "Bearer " + directorToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.field", is(field.name())))
                    .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                    .andExpect(jsonPath("$.data.confirmedByCommitteePosition", is("DIRECTOR")));
        }

        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + firstRuleId + "/activate")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));

        String auditResponse = mockMvc.perform(get("/api/v1/admin/owners-assembly-rules/" + firstRuleId + "/audits")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode activatedAudit = null;
        for (JsonNode audit : objectMapper.readTree(auditResponse).path("data")) {
            if ("ACTIVATED".equals(audit.path("eventType").asText())) {
                activatedAudit = audit;
                break;
            }
        }
        assertNotNull(activatedAudit);
        assertEquals("DIRECTOR", activatedAudit.path("actorCommitteePosition").asText());

        long secondRuleId = createDraft(propertyToken, ruleName, "2026-IT-2", completeConfiguration());
        submitAndConfirmAll(propertyToken, directorToken, secondRuleId);
        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + secondRuleId + "/activate")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));

        String firstStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM t_owners_assembly_rule WHERE rule_id = ?", String.class, firstRuleId);
        assertEquals("SUPERSEDED", firstStatus);
    }

    @Test
    void draftCannotUsePlatformDefaultsWhenItsRuleFieldsAndSourcesAreMissing() throws Exception {
        String propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        String ruleName = RULE_NAME_PREFIX + System.nanoTime();
        long ruleId = createDraft(propertyToken, ruleName, "2026-IT-incomplete", Map.of());

        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + ruleId + "/submit")
                        .header("Authorization", "Bearer " + propertyToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void draftCannotBeSubmittedWhenOneStructuredFieldHasNoSourceClause() throws Exception {
        String propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        Map<String, Object> configuration = completeConfiguration();
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceReferences = (Map<String, Object>) configuration.get("sourceClauseReferences");
        sourceReferences.remove(
                OwnersAssemblyRuleConfiguration.RuleConfigurationField.VOTING_CHANNEL_POLICY.name());
        long ruleId = createDraft(
                propertyToken,
                RULE_NAME_PREFIX + System.nanoTime(),
                "2026-IT-missing-source",
                configuration);

        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + ruleId + "/submit")
                        .header("Authorization", "Bearer " + propertyToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("请为每项结构化规则填写原件页码和条款依据")));
    }

    @Test
    void tenantBoundWorkIdentityCannotReadOrOperateRulesAsAnotherTenant() throws Exception {
        String propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        long ruleId = createDraft(
                propertyToken,
                RULE_NAME_PREFIX + System.nanoTime(),
                "2026-IT-tenant-isolation",
                completeConfiguration());

        mockMvc.perform(get("/api/v1/admin/owners-assembly-rules")
                        .param("tenantId", "10002")
                        .header("Authorization", "Bearer " + propertyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg", is("不能跨小区操作业主大会议事规则")));

        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + ruleId + "/submit")
                        .param("tenantId", "10002")
                        .header("Authorization", "Bearer " + propertyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg", is("不能跨小区操作业主大会议事规则")));
    }

    @Test
    void fullyConfirmedRuleCannotBeActivatedBeforeItsEffectiveDate() throws Exception {
        String propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        String directorToken = token(ACCOUNT_DIRECTOR, USER_DIRECTOR);
        long ruleId = createDraft(
                propertyToken,
                RULE_NAME_PREFIX + System.nanoTime(),
                "2026-IT-future-effective-date",
                completeConfiguration(),
                LocalDate.now().plusDays(1));
        submitAndConfirmAll(propertyToken, directorToken, ruleId);

        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + ruleId + "/activate")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("只能启用已经生效的议事规则版本")));
    }

    private void submitAndConfirmAll(String propertyToken, String directorToken, long ruleId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + ruleId + "/submit")
                        .header("Authorization", "Bearer " + propertyToken))
                .andExpect(status().isOk());
        for (OwnersAssemblyRuleConfiguration.RuleConfigurationField field
                : OwnersAssemblyRuleConfiguration.RuleConfigurationField.values()) {
            mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + ruleId
                            + "/field-confirmations/" + field.name() + "/confirm")
                            .header("Authorization", "Bearer " + directorToken))
                    .andExpect(status().isOk());
        }
    }

    private long createDraft(String token, String ruleName, String ruleVersion, Map<String, Object> configuration)
            throws Exception {
        return createDraft(token, ruleName, ruleVersion, configuration, LocalDate.now().minusDays(1));
    }

    private long createDraft(String token,
                             String ruleName,
                             String ruleVersion,
                             Map<String, Object> configuration,
                             LocalDate effectiveDate) throws Exception {
        MockMultipartFile configurationPart = new MockMultipartFile(
                "configuration",
                "configuration.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(configuration));
        MockMultipartFile sourceFile = new MockMultipartFile(
                "file",
                "业主大会议事规则.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "source-rule".getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/admin/owners-assembly-rules/drafts")
                        .file(configurationPart)
                        .file(sourceFile)
                        .param("ruleName", ruleName)
                        .param("ruleVersion", ruleVersion)
                        .param("effectiveDate", effectiveDate.toString())
                        .param("changeReason", "集成测试规则录入")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("ruleId").asLong();
    }

    private Map<String, Object> completeConfiguration() {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("allowedMeetingForms", List.of("WRITTEN_CONSULTATION"));
        configuration.put("planPublicityDays", 7);
        configuration.put("meetingNoticeDays", 0);
        configuration.put("validDeliveryMethods", List.of("DOOR_TO_DOOR"));
        configuration.put("nonResponsePolicy", "NOT_PARTICIPATED");
        configuration.put("proxyVotingPolicy", "WRITTEN_AUTHORIZATION_REQUIRED");
        configuration.put("votingChannelPolicy", "PAPER_ONLY");
        configuration.put("onlineIdentityVerificationRequired", false);
        configuration.put("paperBallotSealRequired", true);
        configuration.put("duplicateVotePolicy", "NOT_APPLICABLE");
        configuration.put("countingRules", Map.of(
                "GENERAL", countingRule(),
                "MAJOR", countingRule()));
        configuration.put("resultAnnouncementDays", 7);
        configuration.put("sourceClauseReferences", allSourceReferences());
        return configuration;
    }

    private Map<String, Object> countingRule() {
        return Map.of(
                "participationOwnerThreshold", threshold(1, 2, "AT_LEAST"),
                "participationAreaThreshold", threshold(1, 2, "AT_LEAST"),
                "approvalOwnerThreshold", threshold(1, 2, "GREATER_THAN"),
                "approvalAreaThreshold", threshold(1, 2, "GREATER_THAN"));
    }

    private Map<String, Object> threshold(int numerator, int denominator, String comparison) {
        return Map.of(
                "numerator", numerator,
                "denominator", denominator,
                "comparison", comparison);
    }

    private Map<String, Object> allSourceReferences() {
        Map<String, Object> references = new LinkedHashMap<>();
        for (OwnersAssemblyRuleConfiguration.RuleConfigurationField field
                : OwnersAssemblyRuleConfiguration.RuleConfigurationField.values()) {
            references.put(field.name(), Map.of(
                    "pageNumber", 1,
                    "clause", "第 1 条：" + field.name()));
        }
        return references;
    }

    private String token(long accountId, long userId) {
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, TENANT);
    }
}
