// 关联业务：验证业主大会依据已确认议事规则进入纸质书面征询，正式动作受主任/副主任控制，并向当前业主披露锁定材料。
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OwnersAssemblyFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final long ACCOUNT_DIRECTOR = 999811L;
    private static final long USER_DIRECTOR = 800101L;
    private static final long ACCOUNT_OWNER = 999913L;
    private static final long USER_OWNER = 70002L;
    private static final String ASSEMBLY_TITLE_PREFIX = "IT-业主大会-";
    private static final String RULE_NAME_PREFIX = "IT-业主大会办理规则-";

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
                        "owners-assembly-material-etag"));
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM t_owners_assembly_session WHERE title LIKE ?", ASSEMBLY_TITLE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_vote_item
                WHERE subject_id IN (
                    SELECT subject_id FROM t_voting_subject WHERE title LIKE ?
                )
                """, ASSEMBLY_TITLE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_voting_result
                WHERE subject_id IN (
                    SELECT subject_id FROM t_voting_subject WHERE title LIKE ?
                )
                """, ASSEMBLY_TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE title LIKE ?", ASSEMBLY_TITLE_PREFIX + "%");
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
    void writtenAssemblyCanDraftSubjectsBeforeFormalRuleGate() throws Exception {
        String directorToken = token(ACCOUNT_DIRECTOR, USER_DIRECTOR);
        String title = ASSEMBLY_TITLE_PREFIX + "书面征询-" + System.nanoTime();
        long sessionId = createWrittenSession(directorToken, title);

        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/subjects")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectType", "MAJOR",
                                "title", ASSEMBLY_TITLE_PREFIX + "公共区域改造方案-" + System.nanoTime(),
                                "content", "公共区域改造方案及其附件索引"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subjectType", is("MAJOR")));

        mockMvc.perform(get("/api/v1/owners-assemblies/" + sessionId + "/workspace")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assembly.title", is(title)))
                .andExpect(jsonPath("$.data.arrangement").doesNotExist())
                .andExpect(jsonPath("$.data.ruleSnapshot").doesNotExist())
                .andExpect(jsonPath("$.data.draftSubjects.length()", is(1)));
    }

    @Test
    void formalArrangementBlocksRuleCapabilitiesWithoutACompleteEvidenceStateMachine() throws Exception {
        String propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        String directorToken = token(ACCOUNT_DIRECTOR, USER_DIRECTOR);
        activateRule(propertyToken, directorToken, "需要结果公告", 7, 0, 7);
        long sessionId = prepareFormalArrangement(directorToken);

        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/arrangement")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(arrangementRequest(sessionId, directorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg").value("当前系统尚未建模独立的结果公告阶段；规则要求结果公告期限时不能进入正式办理"));
    }

    @Test
    void formalArrangementFreezesActiveRuleAndDerivesPaperOnlySettingsFromIt() throws Exception {
        String propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        String directorToken = token(ACCOUNT_DIRECTOR, USER_DIRECTOR);
        String firstVersion = "2026-IT-" + System.nanoTime();
        activateRule(propertyToken, directorToken, firstVersion, 7, 0, 0);
        long sessionId = prepareFormalArrangement(directorToken);

        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/arrangement")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(arrangementRequest(sessionId, directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PACKAGE_DRAFT")))
                .andExpect(jsonPath("$.data.votingChannelPolicy", is("PAPER_ONLY")))
                .andExpect(jsonPath("$.data.packageId").doesNotExist())
                .andExpect(jsonPath("$.data.publicNoticeDays", is(7)));

        mockMvc.perform(get("/api/v1/owners-assemblies/" + sessionId + "/workspace")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.arrangement.votingChannelPolicy", is("PAPER_ONLY")))
                .andExpect(jsonPath("$.data.ruleSnapshot.ruleVersion", is(firstVersion)))
                .andExpect(jsonPath("$.data.ruleSnapshot.planPublicityDays", is(7)))
                .andExpect(jsonPath("$.data.ruleSnapshot.validDeliveryMethods[0]", is("DOOR_TO_DOOR")))
                .andExpect(jsonPath("$.data.materials.length()", is(3)))
                .andExpect(jsonPath("$.data.materials[0].objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.materials[0].contentSha256").doesNotExist());

        activateRule(propertyToken, directorToken, "2026-IT-replacement-" + System.nanoTime(), 10, 0, 0);
        mockMvc.perform(get("/api/v1/owners-assemblies/" + sessionId + "/workspace")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruleSnapshot.ruleVersion", is(firstVersion)))
                .andExpect(jsonPath("$.data.ruleSnapshot.planPublicityDays", is(7)));

        verify(objectStorage, times(5)).put(anyString(), any(byte[].class), anyString(), anyString());
    }

    @Test
    void propertyMayPrepareMaterialsButOnlyDirectorOrViceDirectorCanConfirmFormalArrangement() throws Exception {
        String propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        String directorToken = token(ACCOUNT_DIRECTOR, USER_DIRECTOR);
        activateRule(propertyToken, directorToken, "2026-IT-formal-role-" + System.nanoTime(), 0, 0, 0);
        long sessionId = prepareFormalArrangement(directorToken);
        String request = arrangementRequest(sessionId, directorToken);

        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/arrangement")
                        .header("Authorization", "Bearer " + propertyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/arrangement")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PACKAGE_DRAFT")));
    }

    @Test
    void publishedAssemblyDisclosesOnlyLockedMaterialsAndCurrentOwnersParticipationState() throws Exception {
        String propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        String directorToken = token(ACCOUNT_DIRECTOR, USER_DIRECTOR);
        activateRule(propertyToken, directorToken, "2026-IT-owner-disclosure-" + System.nanoTime(), 0, 0, 0);
        long sessionId = prepareFormalArrangement(directorToken);

        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/arrangement")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(arrangementRequest(sessionId, directorToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/publish")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PUBLIC_NOTICE")));
        Long packageId = jdbcTemplate.queryForObject(
                "SELECT package_id FROM t_owners_assembly_package WHERE session_id = ?", Long.class, sessionId);

        mockMvc.perform(get("/api/v1/me/owners-assembly-disclosures/" + packageId)
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage", is("PUBLIC_NOTICE")))
                .andExpect(jsonPath("$.data.publicNotice.fileName", is("公示公告.pdf")))
                .andExpect(jsonPath("$.data.planAttachments.length()", is(1)))
                .andExpect(jsonPath("$.data.planAttachments[0].fileName", is("改造方案.pdf")))
                .andExpect(jsonPath("$.data.paperBallotTemplate.fileName", is("盖章选票模板.pdf")))
                .andExpect(jsonPath("$.data.participation.eligible", is(true)))
                .andExpect(jsonPath("$.data.participation.participated", is(false)))
                .andExpect(jsonPath("$.data.subjects[0].status", is("PUBLISHED")))
                .andExpect(jsonPath("$.data.subjects[0].choice").doesNotExist())
                .andExpect(jsonPath("$.data.publicNotice.objectKey").doesNotExist());
    }

    @Test
    void newAssemblyWorkflowRejectsMeetingModesWithoutImplementableEvidenceChain() throws Exception {
        String directorToken = token(ACCOUNT_DIRECTOR, USER_DIRECTOR);

        mockMvc.perform(post("/api/v1/owners-assemblies")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", ASSEMBLY_TITLE_PREFIX + "不允许线上混合模式-" + System.nanoTime(),
                                "preparationMode", "ONLINE_AND_OFFLINE"))))
                .andExpect(status().isBadRequest());
    }

    private long prepareFormalArrangement(String token) throws Exception {
        long sessionId = createWrittenSession(token, ASSEMBLY_TITLE_PREFIX + "材料归档-" + System.nanoTime());
        mockMvc.perform(post("/api/v1/owners-assemblies/" + sessionId + "/subjects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "subjectType", "MAJOR",
                                "title", ASSEMBLY_TITLE_PREFIX + "公共区域改造方案-" + System.nanoTime(),
                                "content", "公共区域改造方案及其附件索引"))))
                .andExpect(status().isCreated());
        return sessionId;
    }

    private String arrangementRequest(long sessionId, String token) throws Exception {
        long publicNoticeMaterialId = uploadMaterial(
                token, sessionId, "PUBLIC_NOTICE", "公示公告.pdf", "application/pdf", "notice");
        long planMaterialId = uploadMaterial(
                token, sessionId, "PLAN_ATTACHMENT", "改造方案.pdf", "application/pdf", "plan");
        long ballotTemplateMaterialId = uploadMaterial(
                token, sessionId, "PAPER_BALLOT_TEMPLATE", "盖章选票模板.pdf", "application/pdf", "ballot");
        return json(Map.of(
                "voteStartAt", Instant.now().plus(8, ChronoUnit.DAYS).toString(),
                "voteEndAt", Instant.now().plus(15, ChronoUnit.DAYS).toString(),
                "publicNoticeMaterialId", publicNoticeMaterialId,
                "planAttachmentMaterialIds", List.of(planMaterialId),
                "ballotTemplateMaterialId", ballotTemplateMaterialId));
    }

    private long createWrittenSession(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/v1/owners-assemblies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", title,
                                "preparationMode", "WRITTEN_DECISION"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("sessionId").asLong();
    }

    private void activateRule(String propertyToken,
                              String directorToken,
                              String version,
                              int planPublicityDays,
                              int meetingNoticeDays,
                              int resultAnnouncementDays) throws Exception {
        String ruleName = RULE_NAME_PREFIX + version;
        MockMultipartFile configurationPart = new MockMultipartFile(
                "configuration",
                "configuration.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(compatibleConfiguration(
                        planPublicityDays, meetingNoticeDays, resultAnnouncementDays)));
        MockMultipartFile sourceFile = new MockMultipartFile(
                "file",
                "业主大会议事规则.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "source-rule".getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/admin/owners-assembly-rules/drafts")
                        .file(configurationPart)
                        .file(sourceFile)
                        .param("ruleName", ruleName)
                        .param("ruleVersion", version)
                        .param("effectiveDate", LocalDate.now().minusDays(1).toString())
                        .param("changeReason", "集成测试规则录入")
                        .header("Authorization", "Bearer " + propertyToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long ruleId = objectMapper.readTree(response).path("data").path("ruleId").asLong();
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
        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + ruleId + "/activate")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    private Map<String, Object> compatibleConfiguration(int planPublicityDays,
                                                          int meetingNoticeDays,
                                                          int resultAnnouncementDays) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("allowedMeetingForms", List.of("WRITTEN_CONSULTATION"));
        configuration.put("planPublicityDays", planPublicityDays);
        configuration.put("meetingNoticeDays", meetingNoticeDays);
        configuration.put("validDeliveryMethods", List.of("DOOR_TO_DOOR"));
        configuration.put("nonResponsePolicy", "NOT_PARTICIPATED");
        configuration.put("proxyVotingPolicy", "NOT_ALLOWED");
        configuration.put("votingChannelPolicy", "PAPER_ONLY");
        configuration.put("onlineIdentityVerificationRequired", false);
        configuration.put("paperBallotSealRequired", true);
        configuration.put("duplicateVotePolicy", "NOT_APPLICABLE");
        configuration.put("countingRules", Map.of(
                "GENERAL", countingRule(),
                "MAJOR", countingRule()));
        configuration.put("resultAnnouncementDays", resultAnnouncementDays);
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

    private long uploadMaterial(String token,
                                long sessionId,
                                String materialType,
                                String fileName,
                                String contentType,
                                String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, contentType, content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/owners-assemblies/" + sessionId + "/materials")
                        .file(file)
                        .param("materialType", materialType)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("materialId").asLong();
    }

    private String token(long accountId, long userId) {
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, TENANT);
    }

    private String ownerToken() {
        return jwtTokenProvider.generateToken(ACCOUNT_OWNER, "C_USER", USER_OWNER, TENANT);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
