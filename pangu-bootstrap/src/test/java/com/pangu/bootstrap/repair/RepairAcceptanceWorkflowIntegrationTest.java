// 关联业务：验证全小区公共维修项目必须完成业委会负责人在线同意、用印和专业共同签署。
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepairAcceptanceWorkflowIntegrationTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final long ACCOUNT_COMMITTEE_MEMBER = 999813L;
    private static final long USER_COMMITTEE_MEMBER = 800103L;
    private static final String PROJECT_PREFIX = "IT-验收维修项目-";
    private static final String SUPPLIER_PREFIX = "IT-验收维修供应商-";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String propertyToken;
    private String viceDirectorToken;

    @BeforeEach
    void setUp() throws Exception {
        propertyToken = token(ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        viceDirectorToken = token(ACCOUNT_COMMITTEE_MEMBER, USER_COMMITTEE_MEMBER);
        jdbcTemplate.update("""
                UPDATE t_committee_member_position
                SET position = 'VICE_DIRECTOR', update_time = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND user_id = ? AND status = 1
                """, TENANT, USER_COMMITTEE_MEMBER);
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2), "community-acceptance-etag"));
        when(objectStorage.createDownloadUrl(anyString(), any()))
                .thenReturn(URI.create("https://oss.example.test/community-acceptance").toURL());
    }

    @AfterEach
    void clean() {
        // 验收参与记录引用用印记录，先解除该引用，再清理项目聚合和测试用印数据。
        jdbcTemplate.update("""
                DELETE FROM t_repair_acceptance_party
                WHERE acceptance_id IN (
                    SELECT acceptance.acceptance_id
                    FROM t_repair_acceptance acceptance
                    JOIN t_repair_project project ON project.project_id = acceptance.project_id
                    WHERE project.project_name LIKE ?
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
        RepairProjectSourcingTestSupport.cleanSuppliers(jdbcTemplate, SUPPLIER_PREFIX);
        jdbcTemplate.update("""
                UPDATE t_committee_member_position
                SET position = 'MEMBER', update_time = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND user_id = ? AND status = 1
                """, TENANT, USER_COMMITTEE_MEMBER);
    }

    @Test
    void communityProjectRequiresViceDirectorSealAndProfessionalCosign() throws Exception {
        AcceptanceFixture fixture = createCommunityAcceptanceFixture();

        postBadRequest(projectPath(fixture.projectId(), "/acceptance/finalization"), viceDirectorToken,
                Map.of("expectedProjectVersion", projectVersion(fixture.projectId()),
                        "resultAttachmentId", fixture.resultAttachmentId()),
                "业委会主任或副主任尚未在线同意");
        postBadRequest(projectPath(fixture.projectId(), "/acceptance/seal"), viceDirectorToken,
                Map.of("sourceAttachmentId", fixture.sourceAttachmentId(),
                        "sealedAttachmentId", fixture.sealedAttachmentId(),
                        "remark", "错误尝试在负责人同意前用印"),
                "业委会主任或副主任尚未在线同意，禁止办理验收用印");

        postOk(projectPath(fixture.projectId(), "/acceptance/committee-executive"), viceDirectorToken,
                Map.of("conclusion", "PASSED", "participantName", "钱副主任",
                        "opinion", "副主任在线同意验收"));
        postOk(projectPath(fixture.projectId(), "/acceptance/property-technical"), propertyToken,
                Map.of("conclusion", "PASSED", "participantName", "物业项目负责人",
                        "participantOrganization", "测试物业服务企业",
                        "opinion", "物业核对工程量并共同签署"));
        postBadRequest(projectPath(fixture.projectId(), "/acceptance/finalization"), viceDirectorToken,
                Map.of("expectedProjectVersion", projectVersion(fixture.projectId()),
                        "resultAttachmentId", fixture.resultAttachmentId()),
                "验收文件尚未完成业委会公章用印及登记");

        postOk(projectPath(fixture.projectId(), "/acceptance/seal"), viceDirectorToken,
                Map.of("sourceAttachmentId", fixture.sourceAttachmentId(),
                        "sealedAttachmentId", fixture.sealedAttachmentId(),
                        "remark", "业委会验收表用印登记"));
        String finalized = postOk(
                projectPath(fixture.projectId(), "/acceptance/finalization"), viceDirectorToken,
                Map.of("expectedProjectVersion", projectVersion(fixture.projectId()),
                        "resultAttachmentId", fixture.resultAttachmentId(),
                        "remark", "三项条件全部满足"));
        assertEquals("PASSED", objectMapper.readTree(finalized).path("data").path("status").asText());
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_project WHERE project_id = ?",
                String.class, fixture.projectId()));
        assertEquals(3, jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT party_role)
                FROM t_repair_acceptance_party party
                JOIN t_repair_acceptance acceptance ON acceptance.acceptance_id = party.acceptance_id
                WHERE acceptance.project_id = ?
                  AND party.party_role IN (
                    'COMMITTEE_EXECUTIVE_APPROVER', 'COMMITTEE_SEAL_OPERATOR',
                    'PROPERTY_TECHNICAL_COSIGNER'
                  )
                """, Integer.class, fixture.projectId()));
        assertEquals("VICE_DIRECTOR", jdbcTemplate.queryForObject("""
                SELECT committee_position
                FROM t_repair_acceptance_party party
                JOIN t_repair_acceptance acceptance ON acceptance.acceptance_id = party.acceptance_id
                WHERE acceptance.project_id = ?
                  AND party.party_role = 'COMMITTEE_EXECUTIVE_APPROVER'
                ORDER BY party.party_id DESC LIMIT 1
                """, String.class, fixture.projectId()));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_committee_seal_usage
                WHERE business_type = 'REPAIR_PROJECT' AND business_id = ?
                """, Integer.class, fixture.projectId()));
    }

    private AcceptanceFixture createCommunityAcceptanceFixture() throws Exception {
        JsonNode created = responseData(postOk(
                "/api/v1/admin/repair-projects", propertyToken, communityProjectRequest()));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();
        RepairProjectSourcingTestSupport.SelectedSupplier selectedSupplier =
                RepairProjectSourcingTestSupport.completeCompetitiveSourcing(
                        mockMvc, objectMapper, propertyToken, SUPPLIER_PREFIX, projectId, 1000);
        long photoAttachmentId = upload(projectId, "道路现场.jpg", "photo");
        link(projectId, planId, photoAttachmentId, "SITE_PHOTO");
        postOk(projectPath(projectId, "/plans/" + planId + "/lock"), propertyToken,
                Map.of("expectedProjectVersion", 0));

        long settlementAttachmentId = upload(projectId, "竣工结算单.pdf", "settlement");
        long sourceAttachmentId = upload(projectId, "验收签前文件.pdf", "acceptance-source");
        long sealedAttachmentId = upload(projectId, "验收盖章文件.pdf", "acceptance-sealed");
        long resultAttachmentId = upload(projectId, "验收定案文件.pdf", "acceptance-result");
        String supplierName = jdbcTemplate.queryForObject(
                "SELECT dept_name FROM sys_dept WHERE dept_id = ?",
                String.class, selectedSupplier.supplierDeptId());

        // 本测试只聚焦验收切片，合同、施工和结算的完整路径由工程执行流程测试覆盖。
        long contractId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_contract (
                    work_order_id, project_id, plan_id, tenant_id, supplier_dept_id,
                    supplier_name, contract_amount, repair_scope_hash, fund_source,
                    signing_method, contract_file_hash, status, created_by_user_id, effective_at
                ) VALUES (
                    NULL, ?, ?, ?, ?, ?, 1000.00,
                    'community-acceptance-scope', 'COMMUNITY_MAINTENANCE_FUND',
                    'OFFLINE', 'community-acceptance-contract', 'EFFECTIVE', ?, CURRENT_TIMESTAMP
                ) RETURNING contract_id
                """, Long.class, projectId, planId, TENANT,
                selectedSupplier.supplierDeptId(), supplierName, USER_PROPERTY_MANAGER);
        long settlementId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_project_settlement (
                    project_id, plan_id, contract_id, tenant_id, version_no, status,
                    subtotal_amount, tax_amount, total_amount, settlement_attachment_id,
                    submitted_by_user_id, verified_by_user_id, verification_opinion, verified_at
                ) VALUES (?, ?, ?, ?, 1, 'VERIFIED', 1000.00, 0.00, 1000.00, ?, ?, ?,
                          '实际工程量与结算一致', CURRENT_TIMESTAMP)
                RETURNING settlement_id
                """, Long.class, projectId, planId, contractId, TENANT, settlementAttachmentId,
                USER_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        long policyId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_acceptance_policy_snapshot (
                    work_order_id, project_id, plan_id, tenant_id, workflow_type, policy_hash,
                    affected_owner_count, minimum_affected_owner_participants,
                    minimum_affected_owner_approvals, affected_owner_pass_rule,
                    affected_owner_approval_ratio, version, status, locked_by_user_id
                ) VALUES (
                    NULL, ?, ?, ?, 'COMMUNITY_PUBLIC_REPAIR', ?, 0, NULL, NULL, NULL, NULL,
                    1, 'ACTIVE', ?
                ) RETURNING policy_id
                """, Long.class, projectId, planId, TENANT, "a".repeat(64), USER_PROPERTY_MANAGER);
        jdbcTemplate.update("""
                INSERT INTO t_repair_acceptance (
                    work_order_id, project_id, settlement_id, tenant_id, policy_id,
                    round_no, status, submitted_by_user_id
                ) VALUES (NULL, ?, ?, ?, ?, 1, 'COLLECTING', ?)
                """, projectId, settlementId, TENANT, policyId, USER_PROPERTY_MANAGER);
        jdbcTemplate.update("""
                UPDATE t_repair_project
                SET status = 'PENDING_ACCEPTANCE', version = version + 1,
                    update_time = CURRENT_TIMESTAMP
                WHERE project_id = ?
                """, projectId);
        return new AcceptanceFixture(
                projectId, sourceAttachmentId, sealedAttachmentId, resultAttachmentId);
    }

    private Map<String, Object> communityProjectRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectName", PROJECT_PREFIX + System.nanoTime());
        request.put("scopeType", "COMMUNITY");
        request.put("fundSource", "COMMUNITY_MAINTENANCE_FUND");
        request.put("governancePath", "COMMUNITY_ASSEMBLY_DECISION");
        request.put("plan", communityPlan());
        return request;
    }

    private Map<String, Object> communityPlan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planDescription", "小区道路破损原因及整体维修范围；按工程项清单及锁定范围施工");
        plan.put("budgetTotal", 1000);
        plan.put("allocationRuleType", "BY_BUILDING_AREA");
        plan.put("allocationRuleDescription", "按小区锁定房屋建筑面积分摊");
        plan.put("supplierSelectionMethod", "COMPETITIVE_QUOTATION");
        plan.put("supplierSelectionReason", "通过询价比较形成实施报价");
        plan.put("constructionManagementRequirements", "物业项目负责人组织现场和工程量管理");
        plan.put("evidenceRequirements", evidenceRequirements());
        plan.put("safetyRequirements", "设置围挡并落实用电安全措施");
        plan.put("acceptanceMethod", "主任或副主任在线同意、业委会用印并由专业人员共同签署");
        plan.put("settlementMethod", "ACTUAL_QUANTITY");
        plan.put("plannedStartDate", LocalDate.now().plusDays(10));
        plan.put("plannedCompletionDate", LocalDate.now().plusDays(30));
        plan.put("warrantyDays", 365);
        plan.put("priceReviewRequired", true);
        plan.put("paymentMilestones", paymentMilestones());
        plan.put("attachments", List.of());
        plan.put("items", List.of(Map.ofEntries(
                Map.entry("itemNo", "ROAD-1"),
                Map.entry("locationText", "小区主干道"),
                Map.entry("workContent", "破损路面铣刨并重铺"),
                Map.entry("quantity", 10),
                Map.entry("unit", "平方米"),
                Map.entry("estimatedUnitPrice", 100),
                Map.entry("estimatedAmount", 1000),
                Map.entry("linkedWorkOrderIds", List.of()))));
        return plan;
    }

    private List<Map<String, Object>> evidenceRequirements() {
        List<Map<String, Object>> requirements = new ArrayList<>();
        for (String stage : List.of(
                "BEFORE_CONSTRUCTION", "MATERIAL_ENTRY", "DURING_CONSTRUCTION",
                "CONCEALED_WORK", "COMPLETION", "ACCEPTANCE")) {
            requirements.add(Map.of(
                    "stage", stage, "description", stage + " 原始证据", "required", true));
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

    private void link(long projectId, long planId, long attachmentId, String purpose) throws Exception {
        postOk(projectPath(projectId, "/plans/" + planId + "/attachments"), propertyToken,
                Map.of("attachmentId", attachmentId, "purpose", purpose));
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

    private String postOk(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void postBadRequest(String path, String token, Object body, String message) throws Exception {
        mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is(message)));
    }

    private JsonNode responseData(String response) throws Exception {
        return objectMapper.readTree(response).path("data");
    }

    private int projectVersion(long projectId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM t_repair_project WHERE project_id = ?", Integer.class, projectId);
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record AcceptanceFixture(
            long projectId,
            long sourceAttachmentId,
            long sealedAttachmentId,
            long resultAttachmentId) {
    }
}
