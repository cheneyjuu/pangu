// 关联业务：验证楼栋维修接龙治理和全小区维修业主大会事项关联是两条独立、可审计的授权流程。
package com.pangu.bootstrap.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
class RepairProjectGovernanceFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final long ACCOUNT_DIRECTOR = 999811L;
    private static final long USER_DIRECTOR = 800101L;
    private static final String PROJECT_PREFIX = "IT-治理维修工程-";
    private static final String CASE_PREFIX = "IT-治理维修报修-";
    private static final String ASSEMBLY_PREFIX = "IT-治理维修业主大会-";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String propertyToken;
    private String directorToken;
    private long buildingId;

    @BeforeEach
    void setUp() throws Exception {
        propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        directorToken = token(ACCOUNT_DIRECTOR, USER_DIRECTOR);
        buildingId = jdbcTemplate.queryForObject("""
                SELECT op.building_id
                FROM c_owner_property op
                WHERE op.tenant_id = ?
                  AND op.account_status = 1
                  AND op.verify_status = 'VERIFIED'
                GROUP BY op.building_id
                ORDER BY COUNT(DISTINCT op.room_id) DESC, op.building_id
                LIMIT 1
                """, Long.class, TENANT);
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2),
                        "governance-etag"));
        when(objectStorage.createDownloadUrl(anyString(), any()))
                .thenReturn(URI.create("https://oss.example.test/repair-project-governance").toURL());
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("""
                UPDATE t_repair_building_process
                SET seal_usage_id = NULL
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_committee_seal_usage
                WHERE business_type = 'REPAIR_PROJECT'
                  AND business_id IN (
                      SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                  )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_repair_project WHERE project_name LIKE ?", PROJECT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_repair_work_order WHERE title LIKE ?", CASE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_voting_result
                WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE title LIKE ?)
                """, ASSEMBLY_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_owners_assembly_subject
                WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE title LIKE ?)
                """, ASSEMBLY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE title LIKE ?", ASSEMBLY_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_owners_assembly_session WHERE title LIKE ?", ASSEMBLY_PREFIX + "%");
    }

    @Test
    void buildingGovernanceRequiresRawDecisionPropertyDocumentDirectorApprovalAndCommitteeSeal() throws Exception {
        LockedProject locked = createAndLockBuildingProject();
        long ruleAttachmentId = upload(locked.projectId(), "备案议事规则.pdf", "rule");
        long evidenceAttachmentId = upload(locked.projectId(), "微信接龙截图.jpg", "evidence");
        long officialAttachmentId = upload(locked.projectId(), "物业正式报审文件.pdf", "official");
        long reviewAttachmentId = upload(locked.projectId(), "第三方审价报告.pdf", "review");
        long sealedAttachmentId = upload(locked.projectId(), "业委会盖章报审文件.pdf", "sealed");

        Map<String, Object> invalidRule = startDecisionRequest(ruleAttachmentId, "FOLLOW_MAJORITY");
        mockMvc.perform(post(projectPath(locked.projectId(), "/building-governance/start"))
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidRule)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("尚未接入备案规则推导票能力，未表态房屋只能保持未参与")));

        JsonNode started = responseData(postJson(
                projectPath(locked.projectId(), "/building-governance/start"),
                propertyToken, startDecisionRequest(ruleAttachmentId, "NOT_PARTICIPATED")))
                .path("process");
        assertEquals("DECISION_COLLECTING", started.path("status").asText());
        assertEquals(0, started.path("processVersion").asInt());

        List<Map<String, Object>> entries = new ArrayList<>();
        for (JsonNode room : locked.allocationRooms()) {
            entries.add(Map.of(
                    "roomId", room.path("roomId").asLong(),
                    "choice", "AGREE",
                    "originalText", "同意按锁定方案维修并承担本户分摊费用"));
        }
        JsonNode decided = responseData(postJson(
                projectPath(locked.projectId(), "/building-governance/decision/complete"),
                propertyToken, Map.of(
                        "expectedProcessVersion", 0,
                        "evidenceAttachmentId", evidenceAttachmentId,
                        "entries", entries)));
        assertEquals("DECISION_PASSED", decided.path("process").path("status").asText());
        assertEquals("PASSED", decided.path("decision").path("result").asText());

        JsonNode documented = responseData(postJson(
                projectPath(locked.projectId(), "/building-governance/official-document"),
                propertyToken, Map.of(
                        "expectedProcessVersion", 1,
                        "attachmentId", officialAttachmentId)));
        assertEquals("OFFICIAL_DOCUMENT_READY", documented.path("process").path("status").asText());

        JsonNode reviewed = responseData(postJson(
                projectPath(locked.projectId(), "/building-governance/price-review"),
                directorToken, Map.ofEntries(
                        Map.entry("expectedProcessVersion", 2),
                        Map.entry("reviewMode", "THIRD_PARTY_AUDIT"),
                        Map.entry("reviewedAmount", 900),
                        Map.entry("reportAttachmentId", reviewAttachmentId),
                        Map.entry("conclusion", "APPROVED"),
                        Map.entry("opinion", "审价金额不超过锁定预算"))));
        assertEquals("PRICE_REVIEWED", reviewed.path("process").path("status").asText());

        JsonNode approved = responseData(postJson(
                projectPath(locked.projectId(), "/building-governance/committee-approval"),
                directorToken, Map.of(
                        "expectedProcessVersion", 3,
                        "opinion", "同意按审价结果用印")));
        assertEquals("DIRECTOR", approved.path("process").path("approverPosition").asText());
        assertEquals("COMMITTEE_APPROVED", approved.path("process").path("status").asText());

        JsonNode sealed = responseData(postJson(
                projectPath(locked.projectId(), "/building-governance/seal"),
                directorToken, Map.of(
                        "expectedProcessVersion", 4,
                        "sealedAttachmentId", sealedAttachmentId,
                        "remark", "纸质盖章件扫描归档")));
        assertEquals("AUTHORIZED", sealed.path("process").path("status").asText());
        assertEquals(5, sealed.path("process").path("processVersion").asInt());

        mockMvc.perform(get("/api/v1/admin/repair-projects/" + locked.projectId())
                        .header("Authorization", bearer(directorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.status", is("AUTHORIZED")));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM t_committee_seal_usage
                WHERE business_type = 'REPAIR_PROJECT' AND business_id = ?
                """, locked.projectId()));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM t_repair_governance_basis
                WHERE project_id = ? AND basis_type = 'BUILDING_REPAIR_DECISION'
                """, locked.projectId()));
    }

    @Test
    void omittedBuildingRoomsRemainNotVotedAndCannotBeTreatedAsConsent() throws Exception {
        LockedProject locked = createAndLockBuildingProject();
        long ruleAttachmentId = upload(locked.projectId(), "备案议事规则.pdf", "rule");
        long evidenceAttachmentId = upload(locked.projectId(), "无人表态截图.jpg", "empty");
        postJson(projectPath(locked.projectId(), "/building-governance/start"),
                propertyToken, startDecisionRequest(ruleAttachmentId, "NOT_PARTICIPATED"));

        JsonNode result = responseData(postJson(
                projectPath(locked.projectId(), "/building-governance/decision/complete"),
                propertyToken, Map.of(
                        "expectedProcessVersion", 0,
                        "evidenceAttachmentId", evidenceAttachmentId,
                        "entries", List.of())));
        assertEquals("DECISION_FAILED", result.path("process").path("status").asText());
        assertEquals("FAILED", result.path("decision").path("result").asText());
        assertEquals(locked.allocationRooms().size(), result.path("entries").size());
        for (JsonNode entry : result.path("entries")) {
            assertEquals("NOT_VOTED", entry.path("choice").asText());
        }
        assertEquals("PLAN_LOCKED", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_project WHERE project_id = ?",
                String.class, locked.projectId()));
    }

    @Test
    void communityProjectUsesTheLinkedAssemblySubjectResultInsteadOfPackageWideResult() throws Exception {
        LockedProject locked = createAndLockCommunityProject();
        AssemblyFixture fixture = createSettledAssemblyFixture();

        JsonNode linked = responseData(postJson(
                projectPath(locked.projectId(), "/community-assembly/link"),
                directorToken, Map.of(
                        "expectedProjectVersion", 1,
                        "packageId", fixture.packageId(),
                        "subjectId", fixture.passedSubjectId())));
        assertEquals("LINKED", linked.path("status").asText());
        assertEquals(fixture.passedSubjectId(), linked.path("subjectId").asLong());

        JsonNode settled = responseData(postJson(
                projectPath(locked.projectId(), "/community-assembly/settle"),
                directorToken, Map.of("expectedProjectVersion", 2)));
        assertEquals("SETTLED", settled.path("status").asText());
        assertEquals("PASSED", settled.path("result").asText());
        assertEquals("AUTHORIZED", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_project WHERE project_id = ?",
                String.class, locked.projectId()));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM t_repair_governance_basis
                WHERE project_id = ? AND reference_type = 'ASSEMBLY_SUBJECT'
                  AND reference_id = ?
                """, locked.projectId(), fixture.passedSubjectId()));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM t_repair_governance_basis
                WHERE project_id = ? AND reference_id = ?
                """, locked.projectId(), fixture.failedSubjectId()));
    }

    private LockedProject createAndLockBuildingProject() throws Exception {
        long workOrderId = createBuildingRepairCase();
        return createAndLockProject(buildingProjectRequest(workOrderId));
    }

    private LockedProject createAndLockCommunityProject() throws Exception {
        return createAndLockProject(communityProjectRequest());
    }

    private LockedProject createAndLockProject(Map<String, Object> request) throws Exception {
        JsonNode created = responseData(postJson(
                "/api/v1/admin/repair-projects", propertyToken, request));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();
        long quoteAttachmentId = upload(projectId, "原始报价.pdf", "quote");
        long photoAttachmentId = upload(projectId, "现场照片.jpg", "photo");
        link(projectId, planId, quoteAttachmentId, "ORIGINAL_QUOTE");
        link(projectId, planId, photoAttachmentId, "SITE_PHOTO");
        JsonNode locked = responseData(postJson(
                projectPath(projectId, "/plans/" + planId + "/lock"),
                propertyToken, Map.of("expectedProjectVersion", 0)));
        List<JsonNode> allocation = new ArrayList<>();
        locked.path("currentPlanAllocationRooms").forEach(allocation::add);
        return new LockedProject(projectId, planId, allocation);
    }

    private long createBuildingRepairCase() throws Exception {
        JsonNode created = responseData(postJson(
                "/api/v1/admin/repair-work-orders", propertyToken, Map.of(
                        "publicAreaScope", "BUILDING",
                        "buildingId", buildingId,
                        "locationText", "楼栋外墙",
                        "title", CASE_PREFIX + System.nanoTime(),
                        "description", "外墙渗水待形成工程项目",
                        "category", "WATERPROOFING")));
        long workOrderId = created.path("workOrderId").asLong();
        jdbcTemplate.update("""
                UPDATE t_repair_work_order
                SET status = 'SURVEY_COMPLETED',
                    location_locked = 1,
                    need_manual_location = 0,
                    fund_gate_blocked = 0,
                    survey_summary = '已完成现场勘验',
                    risk_level = 'MEDIUM'
                WHERE work_order_id = ?
                """, workOrderId);
        return workOrderId;
    }

    private Map<String, Object> buildingProjectRequest(long workOrderId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectName", PROJECT_PREFIX + "楼栋-" + System.nanoTime());
        request.put("scopeType", "BUILDING");
        request.put("buildingId", buildingId);
        request.put("fundSource", "BUILDING_MAINTENANCE_FUND");
        request.put("governancePath", "BUILDING_REPAIR_DECISION");
        Map<String, Object> plan = commonPlan("楼栋外墙渗水原因及维修范围");
        plan.put("affectedOwnerScopeDescription", "本楼栋受维修直接影响的业主");
        plan.put("minimumAffectedOwnerAcceptors", 1);
        plan.put("affectedOwnerPassRule", "ALL");
        plan.put("affectedOwnerApprovalRatio", 1);
        plan.put("items", List.of(Map.ofEntries(
                Map.entry("itemNo", "WATERPROOF-1"),
                Map.entry("buildingId", buildingId),
                Map.entry("locationText", "楼栋外墙"),
                Map.entry("workContent", "外墙渗水点清理并实施防水"),
                Map.entry("quantity", 10),
                Map.entry("unit", "平方米"),
                Map.entry("estimatedUnitPrice", 100),
                Map.entry("estimatedAmount", 1000),
                Map.entry("linkedWorkOrderIds", List.of(workOrderId)))));
        request.put("plan", plan);
        return request;
    }

    private Map<String, Object> communityProjectRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectName", PROJECT_PREFIX + "小区道路-" + System.nanoTime());
        request.put("scopeType", "COMMUNITY");
        request.put("fundSource", "COMMUNITY_MAINTENANCE_FUND");
        request.put("governancePath", "COMMUNITY_ASSEMBLY_DECISION");
        Map<String, Object> plan = commonPlan("小区道路破损原因及整体维修范围");
        plan.put("items", List.of(Map.ofEntries(
                Map.entry("itemNo", "ROAD-1"),
                Map.entry("locationText", "小区主干道"),
                Map.entry("workContent", "破损路面铣刨并重铺"),
                Map.entry("quantity", 10),
                Map.entry("unit", "平方米"),
                Map.entry("estimatedUnitPrice", 100),
                Map.entry("estimatedAmount", 1000),
                Map.entry("linkedWorkOrderIds", List.of()))));
        request.put("plan", plan);
        return request;
    }

    private Map<String, Object> commonPlan(String cause) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("problemCause", cause);
        plan.put("implementationScope", "按工程项清单及锁定范围施工");
        plan.put("budgetTotal", 1000);
        plan.put("allocationRuleType", "BY_BUILDING_AREA");
        plan.put("allocationRuleDescription", "按锁定房屋建筑面积分摊");
        plan.put("supplierSelectionMethod", "COMPETITIVE_QUOTATION");
        plan.put("supplierSelectionReason", "通过询价比较形成实施报价");
        plan.put("constructionManagementRequirements", "物业项目负责人组织现场和工程量管理");
        plan.put("evidenceRequirements", evidenceRequirements());
        plan.put("safetyRequirements", "设置围挡并落实高空和用电安全措施");
        plan.put("acceptanceMethod", "按锁定工程项核对实际数量、单价、金额和过程证据");
        plan.put("settlementMethod", "ACTUAL_QUANTITY");
        plan.put("plannedStartDate", LocalDate.now().plusDays(10));
        plan.put("plannedCompletionDate", LocalDate.now().plusDays(30));
        plan.put("warrantyDays", 365);
        plan.put("priceReviewRequired", true);
        plan.put("paymentMilestones", paymentMilestones());
        plan.put("attachments", List.of());
        return plan;
    }

    private List<Map<String, Object>> evidenceRequirements() {
        List<Map<String, Object>> requirements = new ArrayList<>();
        for (String stage : List.of(
                "BEFORE_CONSTRUCTION", "MATERIAL_ENTRY", "DURING_CONSTRUCTION",
                "CONCEALED_WORK", "COMPLETION", "ACCEPTANCE")) {
            requirements.add(Map.of(
                    "stage", stage,
                    "description", stage + " 原始照片、核验人和时间",
                    "required", true));
        }
        return requirements;
    }

    private List<Map<String, Object>> paymentMilestones() {
        return List.of(
                Map.of("type", "ADVANCE", "maximumContractRatio", 0.30,
                        "requiredEvidenceCodes", List.of("SIGNED_CONTRACT")),
                Map.of("type", "PROGRESS", "maximumContractRatio", 0.90,
                        "requiredEvidenceCodes", List.of("PROGRESS_RECORD")),
                Map.of("type", "COMPLETION", "maximumContractRatio", 1.00,
                        "requiredEvidenceCodes", List.of("ACCEPTANCE", "SETTLEMENT")),
                Map.of("type", "WARRANTY_RELEASE", "maximumContractRatio", 1.00,
                        "requiredEvidenceCodes", List.of("WARRANTY_EXPIRED_CERTIFICATE")));
    }

    private Map<String, Object> startDecisionRequest(long ruleAttachmentId, String nonResponseRule) {
        return Map.of(
                "expectedProjectVersion", 1,
                "ruleDocumentAttachmentId", ruleAttachmentId,
                "ruleVersion", "2026备案版",
                "deliveryRule", "物业在本楼栋微信接龙群送达并保留完整截图",
                "nonResponseRule", nonResponseRule,
                "scopeLabel", "本楼栋专有维修资金承担范围");
    }

    private AssemblyFixture createSettledAssemblyFixture() {
        long suffix = System.nanoTime();
        Long sessionId = jdbcTemplate.queryForObject("""
                INSERT INTO t_owners_assembly_session (
                    tenant_id, title, preparation_mode, status, created_by_user_id
                ) VALUES (?, ?, 'FULL', 'SETTLED', ?)
                RETURNING session_id
                """, Long.class, TENANT, ASSEMBLY_PREFIX + suffix, USER_DIRECTOR);
        Long packageId = jdbcTemplate.queryForObject("""
                INSERT INTO t_owners_assembly_package (
                    session_id, tenant_id, status, voting_channel_policy, public_notice_days,
                    announcement_hash, attachment_manifest_hash, ballot_template_hash,
                    electronic_seal_hash, package_hash, public_notice_start_at,
                    public_notice_end_at, vote_start_at, vote_end_at,
                    locked_by_user_id, locked_at
                ) VALUES (?, ?, 'SETTLED', 'PAPER_ONLY', 7,
                    'announcement-hash', 'manifest-hash', 'ballot-hash',
                    'seal-hash', 'package-hash', CURRENT_TIMESTAMP - INTERVAL '10 days',
                    CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '2 days',
                    CURRENT_TIMESTAMP - INTERVAL '1 day', ?, CURRENT_TIMESTAMP - INTERVAL '10 days')
                RETURNING package_id
                """, Long.class, sessionId, TENANT, USER_DIRECTOR);
        long passedSubjectId = insertSettledSubject(ASSEMBLY_PREFIX + suffix + "-道路维修", true);
        long failedSubjectId = insertSettledSubject(ASSEMBLY_PREFIX + suffix + "-其他事项", false);
        jdbcTemplate.update("""
                INSERT INTO t_owners_assembly_subject(package_id, tenant_id, subject_id)
                VALUES (?, ?, ?), (?, ?, ?)
                """, packageId, TENANT, passedSubjectId, packageId, TENANT, failedSubjectId);
        return new AssemblyFixture(sessionId, packageId, passedSubjectId, failedSubjectId);
    }

    private long insertSettledSubject(String title, boolean passed) {
        Long subjectId = jdbcTemplate.queryForObject("""
                INSERT INTO t_voting_subject (
                    tenant_id, title, content_html, subject_type, scope, status,
                    publish_at, vote_start_at, vote_end_at, settled_at, proposed_by_user_id
                ) VALUES (?, ?, '<p>维修事项</p>', 2, 1, 5,
                    CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '2 days',
                    CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP, ?)
                RETURNING subject_id
                """, Long.class, TENANT, title, USER_DIRECTOR);
        jdbcTemplate.update("""
                INSERT INTO t_voting_result (
                    subject_id, statistics_version, total_area, total_owner_count,
                    participating_area, participating_owner_count, quorum_satisfied,
                    passed, result_payload, attestation_tx_hash
                ) VALUES (?, 1, 1000, 10, 800, 8, 1, ?, '{}'::jsonb, ?)
                """, subjectId, passed ? 1 : 0, "STUB-" + subjectId);
        return subjectId;
    }

    private long upload(long projectId, String fileName, String content) throws Exception {
        String contentType = fileName.endsWith(".jpg") ? "image/jpeg" : "application/pdf";
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, contentType, content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart(
                        "/api/v1/admin/repair-projects/" + projectId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer(propertyToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("attachmentId").asLong();
    }

    private void link(long projectId, long planId, long attachmentId, String purpose) throws Exception {
        postJson(projectPath(projectId, "/plans/" + planId + "/attachments"),
                propertyToken, Map.of("attachmentId", attachmentId, "purpose", purpose));
    }

    private String postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode responseData(String response) throws Exception {
        return objectMapper.readTree(response).path("data");
    }

    private String token(long accountId, long userId) {
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, TENANT);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String projectPath(long projectId, String suffix) {
        return "/api/v1/admin/repair-projects/" + projectId + suffix;
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record LockedProject(long projectId, long planId, List<JsonNode> allocationRooms) {
    }

    private record AssemblyFixture(
            long sessionId, long packageId, long passedSubjectId, long failedSubjectId) {
    }
}
