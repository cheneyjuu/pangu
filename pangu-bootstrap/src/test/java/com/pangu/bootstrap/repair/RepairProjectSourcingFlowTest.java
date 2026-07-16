// 关联业务：验证共有部分维修工程从邀价、报价修订、比价推荐到方案锁定形成同一审计链。
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class RepairProjectSourcingFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final String PROJECT_PREFIX = "IT-项目询价-";
    private static final String SUPPLIER_PREFIX = "IT-项目询价供应商-";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String propertyToken;
    private long buildingId;

    @BeforeEach
    void setUp() throws Exception {
        propertyToken = token(ACCOUNT_PROPERTY_MANAGER, "SYS_USER", USER_PROPERTY_MANAGER);
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
                        "sourcing-etag"));
        when(objectStorage.createDownloadUrl(anyString(), any()))
                .thenReturn(URI.create("https://oss.example.test/repair-project-sourcing").toURL());
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM t_repair_project WHERE project_name LIKE ?", PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_supplier_activation_invitation
                WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, SUPPLIER_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM sys_user_role
                WHERE user_id IN (SELECT user_id FROM sys_user
                    WHERE dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?))
                """, SUPPLIER_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM sys_user
                WHERE dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, SUPPLIER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_account WHERE phone LIKE '13987%'");
        jdbcTemplate.update("""
                DELETE FROM t_supplier_tenant_relation
                WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, SUPPLIER_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_supplier_enterprise_verification
                WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, SUPPLIER_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_supplier_org_profile
                WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, SUPPLIER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE dept_name LIKE ?", SUPPLIER_PREFIX + "%");
    }

    @Test
    void competitiveSourcingRequiresInvitationsConfirmedQuotesAndSelectionBeforePlanLock() throws Exception {
        List<Long> suppliers = List.of(
                registerVerifiedSupplier(), registerVerifiedSupplier(), registerVerifiedSupplier());
        JsonNode created = data(postOk("/api/v1/admin/repair-projects", propertyToken, projectRequest()));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();

        JsonNode invited = data(postOk(sourcingPath(projectId, "/invitations"), propertyToken, Map.of(
                "supplierDeptIds", suppliers,
                "deadline", LocalDateTime.now().plusDays(3))));
        assertEquals(3, invited.path("invitations").size());

        String firstSupplierToken = createSupplierIdentity(suppliers.get(0));
        JsonNode opportunities = data(getOk(
                "/api/v1/supplier/repair-projects/quote-opportunities", firstSupplierToken));
        JsonNode opportunity = opportunities.get(0);
        assertEquals(projectId, opportunity.path("projectId").asLong());
        assertTrue(opportunity.path("items").get(0).path("estimatedUnitPrice").isMissingNode());
        long firstInvitationId = opportunity.path("invitation").path("invitationId").asLong();
        long firstAttachment = upload(projectId, "供应商一报价.pdf", "supplier-one", firstSupplierToken);
        JsonNode firstQuote = data(postOk(
                "/api/v1/supplier/repair-projects/" + projectId + "/quotes",
                firstSupplierToken, Map.of(
                        "invitationId", firstInvitationId,
                        "quoteAmount", 900,
                        "quoteSummary", "供应商在线提交首版报价",
                        "attachmentId", firstAttachment)));
        assertEquals("ONLINE_CONFIRMED", firstQuote.path("confirmationStatus").asText());

        long secondAttachment = upload(projectId, "供应商二纸质报价.pdf", "supplier-two", propertyToken);
        JsonNode secondQuote = data(postOk(sourcingPath(projectId, "/quotes"), propertyToken, Map.of(
                "supplierDeptId", suppliers.get(1),
                "quoteAmount", 930,
                "quoteSummary", "物业代录纸质报价",
                "attachmentId", secondAttachment,
                "confirmationStatus", "OFFLINE_EVIDENCE_VERIFIED",
                "originalSource", "PAPER")));

        mockMvc.perform(post(sourcingPath(projectId, "/selection"))
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quoteId", firstQuote.path("quoteId").asLong()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("有效报价不足 3 家时必须说明继续推荐的理由")));

        JsonNode selected = data(postOk(sourcingPath(projectId, "/selection"), propertyToken, Map.of(
                "quoteId", firstQuote.path("quoteId").asLong(),
                "recommendationReason", "综合施工组织和报价选择",
                "insufficientQuoteReason", "第三家供应商在截止日前未响应")));
        assertEquals(suppliers.get(0).longValue(), selected.path("selection").path("supplierDeptId").asLong());
        assertEquals(900, selected.path("selection").path("quoteAmount").asInt());
        assertEquals(2, selected.path("quotes").size());
        assertEquals(secondQuote.path("quoteId").asLong(), selected.path("quotes").get(1).path("quoteId").asLong());

        long photoAttachment = upload(projectId, "现场照片.jpg", "site", propertyToken);
        postOk("/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/attachments",
                propertyToken, Map.of("attachmentId", photoAttachment, "purpose", "SITE_PHOTO"));
        JsonNode locked = data(postOk(
                "/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/lock",
                propertyToken, Map.of("expectedProjectVersion", 0)));
        assertEquals("PLAN_LOCKED", locked.path("project").path("status").asText());
        assertFalse(locked.path("plans").get(0).path("snapshotHash").asText().isBlank());
        JsonNode lockedSourcing = data(getOk(sourcingPath(projectId, ""), propertyToken));
        assertEquals(firstQuote.path("quoteId").asLong(),
                lockedSourcing.path("selection").path("quoteId").asLong());
        assertEquals(1, count("""
                SELECT COUNT(*) FROM t_repair_project_event
                WHERE project_id = ? AND action = 'PROJECT_SUPPLIER_SELECTED'
                """, projectId));
    }

    private Map<String, Object> projectRequest() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planDescription", "楼栋外墙维修范围与施工方案");
        plan.put("budgetTotal", 1000);
        plan.put("allocationRuleType", "BY_BUILDING_AREA");
        plan.put("allocationRuleDescription", "按锁定房屋建筑面积分摊");
        plan.put("supplierSelectionMethod", "COMPETITIVE_QUOTATION");
        plan.put("supplierSelectionReason", "采用竞争性询价形成施工报价");
        plan.put("constructionManagementRequirements", "物业核验工程量和过程资料");
        plan.put("evidenceRequirements", evidenceRequirements());
        plan.put("safetyRequirements", "设置围挡并落实高空作业防护");
        plan.put("acceptanceMethod", "按锁定工程项和过程资料验收");
        plan.put("affectedOwnerScopeDescription", "本楼栋受影响业主");
        plan.put("minimumAffectedOwnerAcceptors", 1);
        plan.put("affectedOwnerPassRule", "ALL");
        plan.put("affectedOwnerApprovalRatio", 1);
        plan.put("settlementMethod", "ACTUAL_QUANTITY");
        plan.put("plannedStartDate", LocalDate.now().plusDays(7));
        plan.put("plannedCompletionDate", LocalDate.now().plusDays(30));
        plan.put("warrantyDays", 365);
        plan.put("priceReviewRequired", true);
        plan.put("paymentMilestones", paymentMilestones());
        plan.put("attachments", List.of());
        plan.put("items", List.of(Map.ofEntries(
                Map.entry("itemNo", "WALL-1"),
                Map.entry("buildingId", buildingId),
                Map.entry("locationText", "楼栋外墙"),
                Map.entry("workContent", "清理空鼓并修复外墙防水层"),
                Map.entry("quantity", 10),
                Map.entry("unit", "平方米"),
                Map.entry("estimatedUnitPrice", 100),
                Map.entry("estimatedAmount", 1000),
                Map.entry("linkedWorkOrderIds", List.of()))));
        return Map.of(
                "projectName", PROJECT_PREFIX + System.nanoTime(),
                "scopeType", "BUILDING",
                "buildingId", buildingId,
                "fundSource", "BUILDING_MAINTENANCE_FUND",
                "governancePath", "BUILDING_REPAIR_DECISION",
                "plan", plan);
    }

    private long registerVerifiedSupplier() throws Exception {
        String supplierName = SUPPLIER_PREFIX + System.nanoTime();
        JsonNode supplier = data(postOk("/api/v1/admin/supplier-organizations", propertyToken,
                Map.of("legalName", supplierName)));
        long supplierDeptId = supplier.isNumber() ? supplier.asLong() : supplier.path("supplierDeptId").asLong();
        mockMvc.perform(post("/api/v1/admin/supplier-organizations/" + supplierDeptId + "/manual-verifications")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "unifiedSocialCreditCode", "91310000" + String.format("%010d", supplierDeptId),
                                "sourceCode", "GSXT_WEB",
                                "verificationResult", "PASSED",
                                "remark", "测试中已核对企业登记信息"))))
                .andExpect(status().isCreated());
        return supplierDeptId;
    }

    private String createSupplierIdentity(long supplierDeptId) {
        String phone = "13987" + String.format("%06d", supplierDeptId % 1_000_000);
        long accountId = jdbcTemplate.queryForObject("""
                INSERT INTO t_account (phone, real_name, real_name_verified, status, last_active_identity_type)
                VALUES (?, '项目报价经办人', 1, 1, 'SYS_USER')
                RETURNING account_id
                """, Long.class, phone);
        long userId = jdbcTemplate.queryForObject("""
                INSERT INTO sys_user (account_id, dept_id, user_name, nick_name, status)
                VALUES (?, ?, ?, '项目报价经办人', '0')
                RETURNING user_id
                """, Long.class, accountId, supplierDeptId, "project-quote-" + supplierDeptId);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id, effective_data_scope, granted_by)
                SELECT ?, role_id, 'ORG_ONLY', ?
                FROM sys_role WHERE role_key = 'SERVICE_PROVIDER_STAFF'
                """, userId, USER_PROPERTY_MANAGER);
        return token(accountId, "SYS_USER", userId);
    }

    private long upload(long projectId, String fileName, String content, String token) throws Exception {
        String contentType = fileName.endsWith(".jpg") ? "image/jpeg" : "application/pdf";
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, contentType, content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart(
                        "/api/v1/admin/repair-projects/" + projectId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("attachmentId").asLong();
    }

    private List<Map<String, Object>> evidenceRequirements() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String stage : List.of(
                "BEFORE_CONSTRUCTION", "MATERIAL_ENTRY", "DURING_CONSTRUCTION",
                "CONCEALED_WORK", "COMPLETION", "ACCEPTANCE")) {
            result.add(Map.of("stage", stage, "description", stage + " 原始证据", "required", true));
        }
        return result;
    }

    private List<Map<String, Object>> paymentMilestones() {
        return List.of(
                Map.of("type", "ADVANCE", "maximumContractRatio", 0.30,
                        "requiredEvidenceCodes", List.of("SIGNED_CONTRACT")),
                Map.of("type", "PROGRESS", "maximumContractRatio", 0.80,
                        "requiredEvidenceCodes", List.of("PROGRESS_RECORD")),
                Map.of("type", "COMPLETION", "maximumContractRatio", 0.90,
                        "requiredEvidenceCodes", List.of("ACCEPTANCE", "SETTLEMENT")),
                Map.of("type", "WARRANTY_RELEASE", "maximumContractRatio", 1.00,
                        "requiredEvidenceCodes", List.of("WARRANTY_EXPIRED_CERTIFICATE")));
    }

    private String postOk(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String getOk(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode data(String response) throws Exception {
        return objectMapper.readTree(response).path("data");
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String token(long accountId, String identityType, long identityId) {
        return jwtTokenProvider.generateToken(accountId, identityType, identityId, TENANT);
    }

    private String sourcingPath(long projectId, String suffix) {
        return "/api/v1/admin/repair-projects/" + projectId + "/sourcing" + suffix;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
