// 关联业务：端到端验证楼栋维修从已勘验来源、参考询价和方案锁定，到治理授权与最终定商的真实状态交接。
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class RepairProjectBuildingE2eFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final long ACCOUNT_PROPERTY_STAFF = 999822L;
    private static final long USER_PROPERTY_STAFF = 800202L;
    private static final long ACCOUNT_COMMITTEE_DIRECTOR = 999811L;
    private static final long USER_COMMITTEE_DIRECTOR = 800101L;
    private static final String PROJECT_PREFIX = "IT-楼栋维修端到端-";
    private static final String WORK_ORDER_PREFIX = "IT-楼栋维修来源-";
    private static final String SUPPLIER_PREFIX = "IT-楼栋维修端到端供应商-";
    private static final BigDecimal PLAN_BUDGET = new BigDecimal("2207.00");
    private static final BigDecimal QUOTE_AMOUNT = new BigDecimal("1090.00");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String propertyManagerToken;
    private String propertyStaffToken;
    private String committeeDirectorToken;
    private long buildingId;
    private Long createdFundingAccountId;
    private Long createdDecisionRuleId;

    @BeforeEach
    void setUp() {
        propertyManagerToken = jwtTokenProvider.generateToken(
                ACCOUNT_PROPERTY_MANAGER, "SYS_USER", USER_PROPERTY_MANAGER, TENANT);
        propertyStaffToken = jwtTokenProvider.generateToken(
                ACCOUNT_PROPERTY_STAFF, "SYS_USER", USER_PROPERTY_STAFF, TENANT);
        committeeDirectorToken = jwtTokenProvider.generateToken(
                ACCOUNT_COMMITTEE_DIRECTOR, "SYS_USER", USER_COMMITTEE_DIRECTOR, TENANT);
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
                        invocation.getArgument(2), "repair-project-e2e-etag"));
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
        jdbcTemplate.update("DELETE FROM t_repair_work_order WHERE title LIKE ?", WORK_ORDER_PREFIX + "%");
        if (createdFundingAccountId != null) {
            jdbcTemplate.update("DELETE FROM t_fund_ledger_entry WHERE account_id = ?", createdFundingAccountId);
            jdbcTemplate.update("DELETE FROM t_maintenance_fund_account WHERE account_id = ?", createdFundingAccountId);
        }
        if (createdDecisionRuleId != null) {
            jdbcTemplate.update("DELETE FROM t_repair_decision_rule WHERE rule_id = ?", createdDecisionRuleId);
        }
        jdbcTemplate.update("""
                DELETE FROM t_supplier_activation_invitation
                WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, SUPPLIER_PREFIX + "%");
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
    void surveyedBuildingRepairRunsFromReferenceQuoteToAuthorizedSupplierSelection() throws Exception {
        ensureSupportedDecisionRule();
        long sourceWorkOrderId = createSurveyedBuildingWorkOrder();
        createdFundingAccountId = seedBuildingMaintenanceAccount(PLAN_BUDGET);

        JsonNode project = postData("/api/v1/admin/repair-projects", propertyManagerToken,
                projectRequest(sourceWorkOrderId));
        long projectId = project.path("project").path("projectId").asLong();
        long planId = project.path("plans").get(0).path("planId").asLong();
        long workPointId = project.path("currentPlanWorkPoints").get(0).path("workPointId").asLong();
        assertEquals("CONFIRMED", project.path("decisionScope").path("verificationStatus").asText());
        assertEquals("PROJECT_LINKED", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_work_order WHERE work_order_id = ?", String.class, sourceWorkOrderId));

        long supplierDeptId = registerVerifiedSupplier();
        JsonNode invited = postData(sourcingPath(projectId, "/invitations"), propertyManagerToken, Map.of(
                "supplierDeptIds", List.of(supplierDeptId),
                "deadline", LocalDateTime.now().plusDays(3)));
        long invitationId = invited.path("invitations").get(0).path("invitationId").asLong();
        long quoteAttachmentId = uploadProjectAttachment(
                projectId, propertyManagerToken, "参考报价原件.pdf", "reference-quote");
        JsonNode quote = postData(sourcingPath(projectId, "/quotes"), propertyManagerToken,
                quoteRequest(supplierDeptId, invitationId, quoteAttachmentId, workPointId));
        long quoteId = quote.path("quoteId").asLong();
        assertEquals("OFFLINE_EVIDENCE_VERIFIED", quote.path("confirmationStatus").asText());
        assertEquals(0, QUOTE_AMOUNT.compareTo(quote.path("quoteAmount").decimalValue()));

        long responsibilityAttachmentId = uploadProjectAttachment(
                projectId, propertyManagerToken, "工程责任与专项维修资金使用依据.pdf", "responsibility-basis");
        JsonNode responsibility = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/responsibility-determinations",
                propertyManagerToken, Map.of(
                        "expectedProjectVersion", project.path("project").path("version").asInt(),
                        "responsibilityPath", "SHARED_COMMON_REPAIR",
                        "fundingSourceType", "SPECIAL_MAINTENANCE_LEDGER",
                        "basisAttachmentId", responsibilityAttachmentId,
                        "basisReference", "本工程经勘验属于楼栋共有维修，需由相关业主决定后使用专项维修资金。"));
        assertEquals("PENDING_CONFIRMATION", responsibility.path("responsibilityDetermination").path("status").asText());
        assertTrue(responsibility.path("responsibilityDetermination").path("approvedAmount").isMissingNode());
        JsonNode responsibilityConfirmed = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/responsibility-determinations/"
                        + responsibility.path("responsibilityDetermination").path("determinationId").asLong() + "/confirm",
                committeeDirectorToken, Map.of(
                        "expectedProjectVersion", responsibility.path("project").path("version").asInt(),
                        "confirmationNote", "已核验共有责任、专项维修资金路径和后续相关业主决定程序。"));
        assertEquals("CONFIRMED", responsibilityConfirmed.path("responsibilityDetermination").path("status").asText());

        JsonNode frozen = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/freeze-for-authorization",
                propertyManagerToken, Map.of("expectedProjectVersion",
                        responsibilityConfirmed.path("project").path("version").asInt()));
        assertEquals("AUTHORIZATION_IN_PROGRESS", frozen.path("project").path("status").asText());
        assertEquals("AUTHORIZATION_FROZEN", frozen.path("plans").get(0).path("status").asText());
        assertEquals(64, frozen.path("plans").get(0).path("authorizationSnapshotHash").asText().length());
        assertTrue(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) > 0
                FROM t_repair_plan_allocation_room
                WHERE plan_id = ?
                """, Boolean.class, planId));

        JsonNode started = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/building-governance/start",
                propertyManagerToken, Map.of("expectedProjectVersion", frozen.path("project").path("version").asInt()));
        assertEquals("DECISION_COLLECTING", started.path("process").path("status").asText());
        assertEquals("WECHAT", started.path("decision").path("decisionChannel").asText());

        long decisionEvidenceAttachmentId = uploadProjectAttachment(
                projectId, propertyManagerToken, "楼栋维修接龙原始截图.pdf", "decision-evidence");
        JsonNode completed = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/building-governance/decision/complete",
                propertyManagerToken, Map.of(
                        "expectedProcessVersion", processVersion(started),
                        "evidenceAttachmentId", decisionEvidenceAttachmentId,
                        "confirmedResult", "PASSED"));
        assertEquals("DECISION_PASSED", completed.path("process").path("status").asText());
        assertEquals("PASSED", completed.path("decision").path("result").asText());

        long officialDocumentAttachmentId = uploadProjectAttachment(
                projectId, propertyManagerToken, "楼栋维修正式报审文件.pdf", "official-document");
        JsonNode officialDocument = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/building-governance/official-document",
                propertyManagerToken, Map.of(
                        "expectedProcessVersion", processVersion(completed),
                        "attachmentId", officialDocumentAttachmentId));
        assertEquals("OFFICIAL_DOCUMENT_READY", officialDocument.path("process").path("status").asText());

        long priceReviewAttachmentId = uploadProjectAttachment(
                projectId, committeeDirectorToken, "楼栋维修审价报告.pdf", "price-review");
        JsonNode reviewed = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/building-governance/price-review",
                committeeDirectorToken, Map.of(
                        "expectedProcessVersion", processVersion(officialDocument),
                        "reviewMode", "INTERNAL_PRICE_REVIEW",
                        "reviewedAmount", QUOTE_AMOUNT,
                        "reportAttachmentId", priceReviewAttachmentId,
                        "conclusion", "APPROVED",
                        "opinion", "审价金额未超过锁定预算，报价原件和工程范围一致。"));
        assertEquals("PRICE_REVIEWED", reviewed.path("process").path("status").asText());
        assertEquals(0, QUOTE_AMOUNT.compareTo(reviewed.path("process").path("reviewedAmount").decimalValue()));

        JsonNode approved = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/building-governance/committee-approval",
                committeeDirectorToken, Map.of(
                        "expectedProcessVersion", processVersion(reviewed),
                        "opinion", "同意按审价结果办理用印和施工单位选择授权。"));
        assertEquals("COMMITTEE_APPROVED", approved.path("process").path("status").asText());
        assertEquals(USER_COMMITTEE_DIRECTOR, approved.path("process").path("approvedByUserId").asLong());

        long sealedAuthorizationAttachmentId = uploadProjectAttachment(
                projectId, committeeDirectorToken, "楼栋维修用印授权文件.pdf", "sealed-authorization");
        JsonNode authorized = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/building-governance/seal",
                committeeDirectorToken, Map.of(
                        "expectedProcessVersion", processVersion(approved),
                        "sealedAttachmentId", sealedAuthorizationAttachmentId,
                        "remark", "盖章件明确本项目采用直接定商。",
                        "supplierSelectionAuthorization", Map.of(
                                "selectionMethod", "DIRECT_AWARD",
                                "evaluationRule", "AUTHORIZED_DIRECT_SELECTION",
                                "nonCompetitiveSelectionBasis", "已用印授权文件明确本次楼栋紧急维修采用直接定商。")));
        assertEquals("AUTHORIZED", authorized.path("process").path("status").asText());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_repair_governance_basis
                WHERE project_id = ? AND plan_id = ? AND status = 'ACTIVE'
                """, Integer.class, projectId, planId));

        int authorizedProjectVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM t_repair_project WHERE project_id = ?", Integer.class, projectId);
        JsonNode locked = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/lock",
                propertyManagerToken, Map.of("expectedProjectVersion", authorizedProjectVersion));
        assertEquals("AUTHORIZED", locked.path("project").path("status").asText());
        assertEquals("LOCKED", locked.path("plans").get(0).path("status").asText());
        assertEquals("SPECIAL_MAINTENANCE_LEDGER", locked.path("fundingSlices").get(0)
                .path("sourceType").asText());
        assertEquals(64, locked.path("plans").get(0).path("snapshotHash").asText().length());

        // 授权提案被冻结后，既有报价可读；完成授权和最终锁定后也不能再更改参考询价。
        mockMvc.perform(post(sourcingPath(projectId, "/invitations"))
                        .header("Authorization", bearer(propertyManagerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "supplierDeptIds", List.of(supplierDeptId),
                                "deadline", LocalDateTime.now().plusDays(3)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg", is("当前项目不是实施方案草稿，不能修改参考询价")));

        long selectionEvidenceAttachmentId = uploadProjectAttachment(
                projectId, committeeDirectorToken, "施工单位评审记录.pdf", "supplier-selection-evidence");
        JsonNode selected = postData(sourcingPath(projectId, "/selection"), committeeDirectorToken, Map.of(
                "quoteId", quoteId,
                "selectionRationale", "依据已用印授权文件、已通过审价和已核验参考报价确认施工单位。",
                "selectionEvidenceAttachmentId", selectionEvidenceAttachmentId));
        assertEquals("AUTHORIZED", selected.path("selectionAuthorization").path("status").asText());
        assertTrue(selected.path("selectionAuthorization").path("currentActorMayConfirm").asBoolean());
        assertEquals("DIRECT_AWARD", selected.path("selection").path("selectionMethod").asText());
        assertEquals(quoteId, selected.path("selection").path("quoteId").asLong());
        assertEquals(USER_COMMITTEE_DIRECTOR, jdbcTemplate.queryForObject("""
                SELECT confirmed_by_user_id
                FROM t_repair_project_supplier_selection
                WHERE project_id = ? AND quote_id = ?
                """, Long.class, projectId, quoteId));
    }

    private void ensureSupportedDecisionRule() throws Exception {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("""
                SELECT rule_id, non_response_rule
                FROM t_repair_decision_rule
                WHERE tenant_id = ? AND status = 'ACTIVE' AND effective_at <= CURRENT_TIMESTAMP
                """, TENANT);
        if (!existing.isEmpty()) {
            assertEquals("NOT_PARTICIPATED", existing.getFirst().get("non_response_rule"),
                    "端到端测试不会替换已有备案规则；现有规则必须是当前支持的计票方式");
            return;
        }
        MockMultipartFile ruleFile = new MockMultipartFile(
                "file", "楼栋维修征询规则.pdf", "application/pdf", "e2e-rule".getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/admin/repair-decision-rules")
                        .file(ruleFile)
                        .param("ruleName", "端到端楼栋维修征询规则")
                        .param("ruleVersion", "E2E-" + System.nanoTime())
                        .param("effectiveDate", LocalDate.now().toString())
                        .param("deliveryRule", "微信接龙送达并保留原始截图")
                        .param("nonResponseRule", "NOT_PARTICIPATED")
                        .header("Authorization", bearer(committeeDirectorToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        createdDecisionRuleId = objectMapper.readTree(response).path("data").path("ruleId").asLong();
    }

    private long createSurveyedBuildingWorkOrder() throws Exception {
        JsonNode created = postData("/api/v1/admin/repair-work-orders", propertyManagerToken, Map.of(
                "publicAreaScope", "BUILDING",
                "buildingId", buildingId,
                "locationText", "楼栋公共外墙窗框交界",
                "title", WORK_ORDER_PREFIX + System.nanoTime(),
                "description", "现场发现楼栋公共部位渗水，需要完成勘验后纳入维修工程。",
                "category", "WATERPROOFING"));
        long workOrderId = created.path("workOrderId").asLong();
        postAction(workOrderId, "/accept", propertyStaffToken, Map.of("remark", "物业受理楼栋公共部位报修"));
        postAction(workOrderId, "/verify-location", propertyStaffToken, Map.of("remark", "现场核验楼栋和公共范围"));
        postAction(workOrderId, "/assign", propertyManagerToken, Map.of(
                "assignedUserId", USER_PROPERTY_STAFF,
                "assigneeRoleKey", "PROPERTY_STAFF",
                "remark", "派工完成现场勘验"));
        postAction(workOrderId, "/start-survey", propertyStaffToken, Map.of("remark", "开始现场勘验"));
        long surveyImageAttachmentId = uploadWorkOrderSurveyImage(workOrderId);
        JsonNode surveyed = postAction(workOrderId, "/submit-survey", propertyStaffToken, Map.of(
                "surveySummary", "楼栋外墙窗框交界密封层老化，雨后存在渗水痕迹。",
                "riskLevel", "MEDIUM",
                "evidenceImageAttachmentIds", List.of(surveyImageAttachmentId),
                "remark", "现场勘验已完成"));
        assertEquals("SURVEY_COMPLETED", surveyed.path("status").asText());
        return workOrderId;
    }

    private long uploadWorkOrderSurveyImage(long workOrderId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "楼栋现场勘验照片.jpg", "image/jpeg", "survey-image".getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/admin/repair-work-orders/" + workOrderId + "/attachments")
                        .file(file)
                        .param("attachmentKind", "SURVEY_IMAGE")
                        .param("contentType", "image/jpeg")
                        .header("Authorization", bearer(propertyStaffToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("attachmentId").asLong();
    }

    /** 测试夹具只写入与当前楼栋绑定的账簿，不以小区账户替代楼栋维修资金范围。 */
    private long seedBuildingMaintenanceAccount(BigDecimal totalBalance) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_maintenance_fund_account (
                    tenant_id, account_level, reference_id, ancestors, total_balance, frozen_balance
                ) VALUES (?, 2, ?, '0', ?, 0)
                RETURNING account_id
                """, Long.class, TENANT, buildingId, totalBalance);
    }

    private long registerVerifiedSupplier() throws Exception {
        String supplierName = SUPPLIER_PREFIX + System.nanoTime();
        JsonNode supplier = postData("/api/v1/admin/supplier-organizations", propertyManagerToken,
                Map.of("legalName", supplierName));
        long supplierDeptId = supplier.isNumber() ? supplier.asLong() : supplier.path("supplierDeptId").asLong();
        mockMvc.perform(post("/api/v1/admin/supplier-organizations/" + supplierDeptId + "/manual-verifications")
                        .header("Authorization", bearer(propertyManagerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "unifiedSocialCreditCode", "91310000" + String.format("%010d", supplierDeptId),
                                "sourceCode", "GSXT_WEB",
                                "verificationResult", "PASSED",
                                "remark", "端到端测试中已核验企业登记信息"))))
                .andExpect(status().isCreated());
        return supplierDeptId;
    }

    private Map<String, Object> projectRequest(long sourceWorkOrderId) {
        Map<String, Object> workPoint = new LinkedHashMap<>();
        workPoint.put("businessName", "楼栋外墙窗框交界渗水维修点");
        workPoint.put("buildingId", buildingId);
        workPoint.put("locationType", "COMMON_AREA");
        workPoint.put("commonAreaName", "楼栋公共外墙窗框交界");
        workPoint.put("spaceName", "楼栋公共部位");
        workPoint.put("component", "外墙防水节点和窗框密封层");
        workPoint.put("specificPart", "窗框周边老化密封层");
        workPoint.put("symptom", "雨后窗框周边可见渗水痕迹");
        workPoint.put("causeStatus", "PENDING_INVESTIGATION");
        workPoint.put("proposedMeasure", "清理既有密封层并按勘验结论修复防水节点");
        workPoint.put("technicalRequirements", "施工前后留存同角度照片，避免破坏相邻饰面");
        workPoint.put("preliminaryEstimatedAmount", PLAN_BUDGET);
        workPoint.put("estimateSource", "现场勘验初步估算");
        workPoint.put("linkedWorkOrderIds", List.of(sourceWorkOrderId));

        return Map.of(
                "projectName", PROJECT_PREFIX + System.nanoTime(),
                "scopeType", "BUILDING",
                "buildingId", buildingId,
                "plan", Map.of(
                        "planDescription", "本方案依据已勘验的楼栋公共部位来源形成；参考报价在方案冻结前收集。",
                        "budgetTotal", PLAN_BUDGET,
                        "workPoints", List.of(workPoint),
                        "attachments", List.of()));
    }

    private Map<String, Object> quoteRequest(
            long supplierDeptId, long invitationId, long attachmentId, long workPointId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("supplierDeptId", supplierDeptId);
        request.put("invitationId", invitationId);
        request.put("quoteAmount", QUOTE_AMOUNT);
        request.put("taxRate", 9);
        request.put("quoteSummary", "报价原件已核对；税率以报价单头为准。");
        request.put("attachmentId", attachmentId);
        request.put("confirmationStatus", "OFFLINE_EVIDENCE_VERIFIED");
        request.put("originalSource", "EMAIL_ORIGINAL");
        request.put("constructionPeriodDays", 10);
        request.put("warrantyDays", 365);
        request.put("originalAmountConfirmed", true);
        request.put("quoteLines", List.of(
                Map.of(
                        "workPointId", workPointId,
                        "itemName", "外墙窗框防水维修材料和人工",
                        "lineType", "CONSTRUCTION_MEASURE",
                        "workDescription", "清理老化密封层并修复外墙防水节点",
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
        return request;
    }

    private long uploadProjectAttachment(long projectId, String token, String fileName, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/admin/repair-projects/" + projectId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("attachmentId").asLong();
    }

    private JsonNode postAction(long workOrderId, String suffix, String token, Object body) throws Exception {
        return postData("/api/v1/admin/repair-work-orders/" + workOrderId + suffix, token, body);
    }

    private JsonNode postData(String path, String token, Object body) throws Exception {
        String response = mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data");
    }

    private int processVersion(JsonNode details) {
        return details.path("process").path("processVersion").asInt();
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
