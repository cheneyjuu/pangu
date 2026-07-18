// 关联业务：验证草稿阶段可询价，报价明细可选关联维修点位且税率、税额只以报价单头为准。
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final String PROJECT_PREFIX = "IT-维修点位询价-";
    private static final String SUPPLIER_PREFIX = "IT-维修点位询价供应商-";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String propertyToken;
    private long buildingId;

    @BeforeEach
    void setUp() throws Exception {
        propertyToken = jwtTokenProvider.generateToken(
                ACCOUNT_PROPERTY_MANAGER, "SYS_USER", USER_PROPERTY_MANAGER, TENANT);
        buildingId = jdbcTemplate.queryForObject("""
                SELECT building_id
                FROM c_owner_property
                WHERE tenant_id = ? AND account_status = 1
                GROUP BY building_id
                ORDER BY COUNT(DISTINCT room_id) DESC, building_id
                LIMIT 1
                """, Long.class, TENANT);
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2), "sourcing-etag"));
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
    void draftQuoteUsesOptionalWorkPointAndHeaderTaxWhileSelectionStaysBlocked() throws Exception {
        JsonNode created = data(postOk("/api/v1/admin/repair-projects", propertyToken, projectRequest()));
        long projectId = created.path("project").path("projectId").asLong();
        long workPointId = created.path("currentPlanWorkPoints").get(0).path("workPointId").asLong();
        long supplierDeptId = registerVerifiedSupplier();

        JsonNode invited = data(postOk(sourcingPath(projectId, "/invitations"), propertyToken, Map.of(
                "supplierDeptIds", List.of(supplierDeptId),
                "deadline", LocalDateTime.now().plusDays(3))));
        assertEquals(1, invited.path("invitations").size());

        String supplierToken = createSupplierIdentity(supplierDeptId);
        JsonNode opportunities = data(getOk(
                "/api/v1/supplier/repair-projects/quote-opportunities", supplierToken));
        JsonNode opportunity = opportunities.get(0);
        assertEquals(projectId, opportunity.path("projectId").asLong());
        assertEquals(workPointId, opportunity.path("workPoints").get(0).path("workPointId").asLong());
        assertTrue(opportunity.path("items").isMissingNode());

        long invitationId = opportunity.path("invitation").path("invitationId").asLong();
        long attachmentId = upload(projectId, "供应商报价原件.pdf", "quote", supplierToken);

        mockMvc.perform(post("/api/v1/supplier/repair-projects/" + projectId + "/quotes")
                        .header("Authorization", bearer(supplierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(quoteRequest(invitationId, attachmentId, workPointId + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("报价明细包含当前实施方案以外的维修点位")));

        JsonNode quote = data(postOk(
                "/api/v1/supplier/repair-projects/" + projectId + "/quotes",
                supplierToken, quoteRequest(invitationId, attachmentId, workPointId)));
        assertEquals("ONLINE_CONFIRMED", quote.path("confirmationStatus").asText());
        assertEquals(1000, quote.path("amountExcludingTax").asInt());
        assertEquals(9, quote.path("taxRate").asInt());
        assertEquals(90, quote.path("taxAmount").asInt());
        assertEquals(1090, quote.path("quoteAmount").asInt());
        assertEquals(workPointId, quote.path("quoteLines").get(0).path("workPointId").asLong());
        assertTrue(quote.path("quoteLines").get(1).path("workPointId").isNull());

        mockMvc.perform(post(sourcingPath(projectId, "/selection"))
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quoteId", quote.path("quoteId").asLong()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg", is(
                        "当前项目尚未接入可信的决定或授权快照，不能定商；中选报价不属于建项草稿前置条件")));
    }

    private Map<String, Object> projectRequest() {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("businessName", "楼栋公共区域排水泵维修点");
        point.put("buildingId", buildingId);
        point.put("locationType", "COMMON_AREA");
        point.put("commonAreaName", "地下车库排水泵房");
        point.put("component", "排水泵及控制柜");
        point.put("specificPart", "泵组和控制柜连接部位");
        point.put("symptom", "设备运行异常并伴随排水不畅");
        point.put("causeStatus", "PENDING_INVESTIGATION");
        point.put("proposedMeasure", "在完成专业勘验后更换故障部件并恢复排水能力");
        point.put("linkedWorkOrderIds", List.of());

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planDescription", "本草稿记录维修点位和询价边界，报价行与点位不强制一一对应。");
        plan.put("budgetTotal", 1090);
        plan.put("workPoints", List.of(point));
        plan.put("attachments", List.of());
        return Map.of(
                "projectName", PROJECT_PREFIX + System.nanoTime(),
                "scopeType", "BUILDING",
                "buildingId", buildingId,
                "plan", plan);
    }

    private Map<String, Object> quoteRequest(long invitationId, long attachmentId, long workPointId) {
        return Map.of(
                "invitationId", invitationId,
                "quoteAmount", 1090,
                "taxRate", 9,
                "quoteSummary", "报价原件已核对，税率以报价单头为准",
                "attachmentId", attachmentId,
                "constructionPeriodDays", 10,
                "warrantyDays", 365,
                "originalAmountConfirmed", true,
                "quoteLines", List.of(
                        Map.of(
                                "workPointId", workPointId,
                                "itemName", "排水泵维修材料和人工",
                                "lineType", "CONSTRUCTION_MEASURE",
                                "workDescription", "按勘验结论维修泵组和控制柜",
                                "quantity", 1,
                                "unit", "项",
                                "unitPriceExcludingTax", 900),
                        Map.of(
                                "itemName", "运输和清运",
                                "lineType", "TRANSPORT_CLEANUP",
                                "workDescription", "项目通用运输和清运费用",
                                "quantity", 1,
                                "unit", "项",
                                "unitPriceExcludingTax", 100)));
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
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, null);
    }

    private long upload(long projectId, String fileName, String content, String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart(
                        "/api/v1/admin/repair-projects/" + projectId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer(token)))
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

    private String getOk(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode data(String response) throws Exception {
        return objectMapper.readTree(response).path("data");
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
