// 关联业务：验证维修工单从登记到表决、报审、盖章、合同、施工和验收的完整闭环。
package com.pangu.bootstrap.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import com.pangu.domain.repository.RepairWorkOrderRepository;
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

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.pangu.domain.repository.RepairDocumentPreviewConverter;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RepairWorkOrderFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACC_OWNER = 999913L;
    private static final long UID_OWNER = 70002L;
    private static final long ACC_PROPERTY_MANAGER = 999821L;
    private static final long USR_PROPERTY_MANAGER = 800201L;
    private static final long ACC_PROPERTY_STAFF = 999822L;
    private static final long USR_PROPERTY_STAFF = 800202L;
    private static final long ACC_DIRECTOR = 999811L;
    private static final long USR_DIRECTOR = 800101L;
    private static final long ACC_GRID = 999804L;
    private static final long USR_GRID = 800004L;
    private static final long ACC_STREET = 999801L;
    private static final long USR_STREET = 800001L;
    private static final long USR_COMMUNITY = 800003L;
    private static final long DEPT_COMMUNITY = 101L;
    private static final long DEPT_GRID = 104L;
    private static final long TENANT_CROSS = 10002L;
    private static final long CROSS_ROOM = 10002300101L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RepairWorkOrderRepository repairWorkOrderRepository;
    @MockBean private RepairEvidenceObjectStorage objectStorage;
    @MockBean private RepairDocumentPreviewConverter documentPreviewConverter;

    @BeforeEach
    public void configureObjectStorage() throws Exception {
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2), "etag-test"));
        when(objectStorage.createPreviewUrl(anyString(), anyString(), any()))
                .thenReturn(URI.create("https://oss.example.test/repair-preview").toURL());
        when(objectStorage.createDownloadUrl(anyString(), any()))
                .thenReturn(URI.create("https://oss.example.test/repair-download").toURL());
        when(objectStorage.read(anyString())).thenReturn("excel-source".getBytes(StandardCharsets.UTF_8));
        when(objectStorage.exists(anyString())).thenReturn(false, true);
        when(documentPreviewConverter.convertExcelToPdf(anyString(), anyString(), any(byte[].class)))
                .thenReturn("%PDF-1.7\npreview".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    public void clean() {
        jdbcTemplate.update("""
                UPDATE t_tenant_community
                SET repair_estimate_required = 0,
                    building_repair_default_decision_channel = 'WECHAT'
                WHERE tenant_id = ?
                """, TENANT);
        jdbcTemplate.update("""
                DELETE FROM t_repair_governance_seal
                WHERE work_order_id IN (
                    SELECT work_order_id FROM t_repair_work_order WHERE title LIKE 'IT-报修-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM t_committee_seal_usage
                WHERE business_type = 'REPAIR'
                  AND business_id IN (
                    SELECT work_order_id FROM t_repair_work_order WHERE title LIKE 'IT-报修-%'
                  )
                """);
        jdbcTemplate.update("DELETE FROM t_committee_electronic_seal WHERE seal_name LIKE 'IT-模拟印章-%'");
        jdbcTemplate.update("DELETE FROM t_repair_work_order WHERE title LIKE 'IT-报修-%'");
        jdbcTemplate.update("DELETE FROM t_supplier_activation_invitation WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%')");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT user_id FROM sys_user WHERE dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%'))");
        jdbcTemplate.update("DELETE FROM sys_user WHERE dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%')");
        jdbcTemplate.update("DELETE FROM t_supplier_tenant_relation WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%')");
        jdbcTemplate.update("DELETE FROM t_supplier_enterprise_verification WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%')");
        jdbcTemplate.update("DELETE FROM t_supplier_org_profile WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%')");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%'");
        jdbcTemplate.update("DELETE FROM t_account WHERE phone IN ('13800000931', '13800000932') AND NOT EXISTS (SELECT 1 FROM sys_user WHERE sys_user.account_id = t_account.account_id) AND NOT EXISTS (SELECT 1 FROM c_user WHERE c_user.account_id = t_account.account_id)");
        jdbcTemplate.update("""
                DELETE FROM sys_dept_building_scope
                WHERE dept_id = ? AND tenant_id = ? AND building_id = 30001
                """, DEPT_GRID, TENANT_CROSS);
        jdbcTemplate.update("""
                DELETE FROM sys_dept_tenant_scope
                WHERE dept_id = ? AND tenant_id = ?
                """, DEPT_COMMUNITY, TENANT_CROSS);
        jdbcTemplate.update("""
                DELETE FROM c_owner_property
                WHERE uid = 70001 AND tenant_id = ? AND room_id = ?
                """, TENANT_CROSS, CROSS_ROOM);
    }

    @Test
    void committeeDirectorCanCreateListAndDeactivateMockSeal() throws Exception {
        String directorToken = token(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR);
        String sealName = "IT-模拟印章-" + System.nanoTime();

        String created = mockMvc.perform(post("/api/v1/admin/committee-seals/mock")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "sealName", sealName,
                                "sealType", "OWNERS_COMMITTEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sealName", is(sealName)))
                .andExpect(jsonPath("$.data.providerCode", is("MOCK")))
                .andExpect(jsonPath("$.data.simulated", is(true)))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.custodianUserId", is((int) USR_DIRECTOR)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long sealId = objectMapper.readTree(created).path("data").path("sealId").asLong();

        mockMvc.perform(get("/api/v1/admin/committee-seals")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sealId", is((int) sealId)))
                .andExpect(jsonPath("$.data[0].committeeTermName", is("第三届业委会-2026")));

        mockMvc.perform(post("/api/v1/admin/committee-seals/" + sealId + "/deactivate")
                        .header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));
    }

    @Test
    public void buildingDecisionDenominator_sameOwnerMultipleRooms_countsOnePersonAndAggregatesArea() {
        var snapshot = repairWorkOrderRepository.loadBuildingDecisionSnapshot(TENANT, 30005L, null).orElseThrow();
        var participants = repairWorkOrderRepository.listDecisionRooms(TENANT, 30005L, null);

        assertEquals(1, snapshot.totalOwnerCount(), "同一产权人在同一表决范围内持有多套房屋时只计一个人数");
        assertEquals(0, new BigDecimal("154.00").compareTo(snapshot.totalArea()));
        assertEquals(1, participants.size());
        assertEquals(0, new BigDecimal("154.00").compareTo(participants.getFirst().buildArea()));
        assertTrue(participants.getFirst().roomLabel().contains("30005101"));
        assertTrue(participants.getFirst().roomLabel().contains("30005201"));
    }

    @Test
    public void supplierEnterpriseVerification_supportsManualAndReplaceableMockProviderWithTenantAudit()
            throws Exception {
        String managerToken = token(ACC_PROPERTY_MANAGER, "SYS_USER", USR_PROPERTY_MANAGER);
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);
        long supplierDeptId = registerMinimalSupplier(
                managerToken, "IT-供应商-主体核验-" + System.nanoTime());
        String uscc = "91310000A000000001";

        jdbcTemplate.update("""
                INSERT INTO t_supplier_tenant_relation (
                    tenant_id, supplier_dept_id, relation_type, status, requested_by_user_id
                ) VALUES (?, ?, 'NORMAL', 'PENDING_APPROVAL', ?)
                ON CONFLICT DO NOTHING
                """, TENANT_CROSS, supplierDeptId, USR_PROPERTY_MANAGER);

        mockMvc.perform(post("/api/v1/admin/supplier-organizations/" + supplierDeptId + "/manual-verifications")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "unifiedSocialCreditCode", uscc,
                                "sourceCode", "GSXT_WEB",
                                "verificationResult", "PASSED"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/supplier-organizations/verification-provider")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerCode", is("ALIYUN")))
                .andExpect(jsonPath("$.data.displayName", is("阿里云企业二要素核验")))
                .andExpect(jsonPath("$.data.simulated", is(true)));

        mockMvc.perform(post("/api/v1/admin/supplier-organizations/" + supplierDeptId + "/manual-verifications")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "unifiedSocialCreditCode", uscc,
                                "sourceCode", "GSXT_WEB",
                                "verificationResult", "PASSED",
                                "remark", "已在国家企业信用信息公示系统核对"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationMethod", is("PROPERTY_MANUAL")))
                .andExpect(jsonPath("$.data.sourceCode", is("GSXT_WEB")))
                .andExpect(jsonPath("$.data.verificationResult", is("PASSED")))
                .andExpect(jsonPath("$.data.operatorAccountId", is((int) ACC_PROPERTY_MANAGER)))
                .andExpect(jsonPath("$.data.operatorUserId", is((int) USR_PROPERTY_MANAGER)))
                .andExpect(jsonPath("$.data.simulated", is(false)))
                .andExpect(jsonPath("$.data.verifiedAt").isNotEmpty());

        JsonNode manuallyVerified = supplierOrganization(managerToken, supplierDeptId);
        assertEquals("VERIFIED", manuallyVerified.path("verificationStatus").asText());
        assertEquals("PROPERTY_MANUAL", manuallyVerified.path("verificationMethod").asText());
        assertEquals("GSXT_WEB", manuallyVerified.path("verificationSourceCode").asText());
        assertEquals(ACC_PROPERTY_MANAGER, manuallyVerified.path("verifiedByAccountId").asLong());

        assertEquals("PENDING_VERIFICATION", jdbcTemplate.queryForObject("""
                SELECT enterprise_verification_status
                FROM t_supplier_tenant_relation
                WHERE tenant_id = ? AND supplier_dept_id = ?
                """, String.class, TENANT_CROSS, supplierDeptId),
                "物业手工核验结论不得传播到其他租户");

        mockMvc.perform(post("/api/v1/admin/supplier-organizations/" + supplierDeptId + "/platform-verifications")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "unifiedSocialCreditCode", uscc,
                                "supplierAuthorizationConfirmed", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationMethod", is("PLATFORM_API")))
                .andExpect(jsonPath("$.data.providerCode", is("ALIYUN")))
                .andExpect(jsonPath("$.data.providerRequestId", startsWith("MOCK-ALIYUN-")))
                .andExpect(jsonPath("$.data.providerResultCode", is("SIMULATED_MATCH")))
                .andExpect(jsonPath("$.data.verificationResult", is("PASSED")))
                .andExpect(jsonPath("$.data.simulated", is(true)));

        mockMvc.perform(get("/api/v1/admin/supplier-organizations/" + supplierDeptId + "/verifications")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(2)))
                .andExpect(jsonPath("$.data[0].verificationMethod", is("PLATFORM_API")))
                .andExpect(jsonPath("$.data[1].verificationMethod", is("PROPERTY_MANUAL")));

        JsonNode platformVerified = supplierOrganization(managerToken, supplierDeptId);
        assertEquals("PLATFORM_API", platformVerified.path("verificationMethod").asText());
        assertEquals("ALIYUN", platformVerified.path("verificationProviderCode").asText());
        assertTrue(platformVerified.path("verificationSimulated").asBoolean());
    }

    @Test
    public void failedLocalDecision_canRevisePlanAndStartANewAuditedRound() throws Exception {
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);
        String managerToken = token(ACC_PROPERTY_MANAGER, "SYS_USER", USR_PROPERTY_MANAGER);
        String ownerRepToken = token(999812L, "SYS_USER", 800102L);
        long workOrderId = insertSupplierRecommendedBuildingRepair(
                "IT-报修-表决未通过后修订方案-" + System.nanoTime());
        long supplierDeptId = registerMinimalSupplier(
                managerToken, "IT-供应商-沿用报价-" + System.nanoTime());
        insertConfirmedQuoteAndRecommendation(workOrderId, supplierDeptId);

        action(ownerRepToken, workOrderId, "start-local-decision", Map.of(
                "scopeType", "BUILDING",
                "decisionChannel", "WECHAT",
                "scopeLabel", "1号楼",
                "remark", "发起首轮表决"));
        var firstRoundEntries = repairWorkOrderRepository.listDecisionRooms(TENANT, 30001L, null).stream()
                .map(room -> Map.<String, Object>of(
                        "roomId", room.roomId(),
                        "choice", "NOT_VOTED"))
                .toList();
        action(staffToken, workOrderId, "complete-local-decision", Map.of(
                "entries", firstRoundEntries,
                "evidenceAttachmentHash", "wechat-round-1-evidence",
                "remark", "首轮表决未通过"))
                .andExpect(jsonPath("$.data.status", is("PLAN_REVISION_REQUIRED")));

        action(staffToken, workOrderId, "submit-plan", Map.of(
                "planBudget", new BigDecimal("12000.00"),
                "fundSource", "BUILDING_MAINTENANCE_FUND",
                "remark", "根据表决意见提交修订方案"))
                .andExpect(jsonPath("$.data.status", is("PLAN_SUBMITTED")));

        action(managerToken, workOrderId, "reuse-supplier-quote", Map.of(
                "remark", "维修范围和报价条件未变化，沿用上一轮已确认报价"))
                .andExpect(jsonPath("$.data.status", is("SUPPLIER_RECOMMENDED")));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_repair_supplier_recommendation WHERE work_order_id = ?",
                Integer.class, workOrderId));
        assertEquals("REUSE_SUPPLIER_QUOTE", jdbcTemplate.queryForObject("""
                SELECT action FROM t_repair_work_order_event
                WHERE work_order_id = ? ORDER BY event_id DESC LIMIT 1
                """, String.class, workOrderId));
        action(ownerRepToken, workOrderId, "start-local-decision", Map.of(
                "scopeType", "BUILDING",
                "decisionChannel", "ONLINE",
                "scopeLabel", "1号楼修订方案",
                "remark", "发起第二轮表决"))
                .andExpect(jsonPath("$.data.status", is("LOCAL_DECISION_PENDING")));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_repair_local_decision WHERE work_order_id = ?",
                Integer.class, workOrderId));
        assertEquals("COLLECTING", repairWorkOrderRepository.findLocalDecision(workOrderId, TENANT)
                .orElseThrow().result());
    }

    @Test
    public void revisedPlan_canRequestNewQuoteVersionWithoutOverwritingHistory() throws Exception {
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);
        String managerToken = token(ACC_PROPERTY_MANAGER, "SYS_USER", USR_PROPERTY_MANAGER);
        long workOrderId = insertSupplierRecommendedBuildingRepair(
                "IT-报修-方案变更后修订报价-" + System.nanoTime());
        long supplierDeptId = registerMinimalSupplier(
                managerToken, "IT-供应商-修订报价-" + System.nanoTime());
        long previousQuoteId = insertConfirmedQuoteAndRecommendation(workOrderId, supplierDeptId);
        jdbcTemplate.update("UPDATE t_repair_work_order SET status = 'PLAN_SUBMITTED' WHERE work_order_id = ?",
                workOrderId);

        action(managerToken, workOrderId, "revision-quote-invitations", Map.of(
                "supplierDeptIds", List.of(supplierDeptId),
                "deadline", LocalDateTime.now().plusDays(2).toString(),
                "remark", "设备门规格和工程量发生变化，请重新报价"))
                .andExpect(jsonPath("$.data.status", is("QUOTE_COLLECTING")));

        assertEquals("REVISION_REQUESTED", jdbcTemplate.queryForObject(
                "SELECT quote_status FROM t_repair_supplier_quote WHERE quote_id = ?",
                String.class, previousQuoteId));
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + workOrderId + "/quote-invitations")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].invitationRound", is(2)))
                .andExpect(jsonPath("$.data[0].invitationType", is("REVISION")))
                .andExpect(jsonPath("$.data[0].revisionReason",
                        is("设备门规格和工程量发生变化，请重新报价")));

        submitQuote(staffToken, workOrderId, supplierDeptId, "26800.00")
                .andExpect(jsonPath("$.data.status", is("QUOTE_SUBMITTED")));
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + workOrderId
                        + "/supplier-quotes/" + supplierDeptId + "/history")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(2)))
                .andExpect(jsonPath("$.data[0].revisionNo", is(2)))
                .andExpect(jsonPath("$.data[0].quoteStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.data[1].quoteId", is((int) previousQuoteId)))
                .andExpect(jsonPath("$.data[1].quoteStatus", is("SUPERSEDED")));
    }

    @Test
    public void ownerPrivateRepair_propertyPackage_routesDirectlyAndCompletes() throws Exception {
        long opid = jdbcTemplate.queryForObject("""
                SELECT opid FROM c_owner_property
                WHERE uid = ? AND tenant_id = ?
                ORDER BY opid LIMIT 1
                """, Long.class, UID_OWNER, TENANT);
        String ownerToken = token(ACC_OWNER, "C_USER", UID_OWNER);
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);
        String managerToken = token(ACC_PROPERTY_MANAGER, "SYS_USER", USR_PROPERTY_MANAGER);
        String directorToken = token(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR);

        long id = createPrivate(ownerToken, opid, "IT-报修-厨房水管漏水-" + System.nanoTime());

        action(staffToken, id, "accept", Map.of("remark", "客服受理"))
                .andExpect(jsonPath("$.data.status", is("PENDING_VERIFY")));
        action(staffToken, id, "verify-location", Map.of("remark", "现场核验属实"))
                .andExpect(jsonPath("$.data.status", is("VERIFIED")))
                .andExpect(jsonPath("$.data.locationLocked", is(true)))
                .andExpect(jsonPath("$.data.fundGateBlocked", is(false)));
        actionRaw(staffToken, id, "submit-plan", Map.of(
                "planBudget", new BigDecimal("600.00"),
                "fundSource", "PROPERTY_INTERNAL",
                "remark", "尝试跳过派单和初勘"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42506)));
        action(managerToken, id, "assign", Map.of(
                "assignedUserId", USR_PROPERTY_STAFF,
                "assigneeRoleKey", "PROPERTY_STAFF",
                "remark", "派给工程员工"))
                .andExpect(jsonPath("$.data.status", is("ASSIGNED")));
        action(staffToken, id, "start-survey", Map.of("remark", "开始初勘"))
                .andExpect(jsonPath("$.data.status", is("SURVEYING")));
        actionRaw(staffToken, id, "submit-survey", Map.of(
                "surveySummary", "厨房立管接头老化，需更换阀门并恢复墙面",
                "riskLevel", "LOW",
                "remark", "缺少现场照片"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)));
        long surveyImageId = readyAttachment(id, "SURVEY_IMAGE", "image/jpeg", 1024L);
        long surveyVideoId = readyAttachment(id, "SURVEY_VIDEO", "video/mp4", 4096L);
        action(staffToken, id, "submit-survey", Map.of(
                "surveySummary", "厨房立管接头老化，需更换阀门并恢复墙面",
                "riskLevel", "LOW",
                "evidenceImageAttachmentIds", List.of(surveyImageId),
                "evidenceVideoAttachmentId", surveyVideoId,
                "remark", "现场初勘完成"))
                .andExpect(jsonPath("$.data.status", is("SURVEY_COMPLETED")))
                .andExpect(jsonPath("$.data.surveySummary", is("厨房立管接头老化，需更换阀门并恢复墙面")))
                .andExpect(jsonPath("$.data.riskLevel", is("LOW")));
        actionRaw(staffToken, id, "submit-plan", Map.of(
                "publicCeilingPrice", new BigDecimal("500.00"),
                "fundSource", "PROPERTY_INTERNAL",
                "remark", "错误设置供应商限价"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("物业包干维修不进入供应商邀价，不能设置公开最高限价")));
        action(staffToken, id, "submit-plan", Map.of(
                "fundSource", "PROPERTY_INTERNAL",
                "remark", "不要求内部估算的小修小补"))
                .andExpect(jsonPath("$.data.status", is("APPROVED")))
                .andExpect(jsonPath("$.data.planBudget").doesNotExist());
        action(staffToken, id, "start-work", Map.of("remark", "开始维修"))
                .andExpect(jsonPath("$.data.status", is("IN_PROGRESS")));
        action(staffToken, id, "submit-acceptance", Map.of("remark", "完工提交验收"))
                .andExpect(jsonPath("$.data.status", is("PENDING_ACCEPTANCE")));
        action(staffToken, id, "accept-completed", Map.of("remark", "物业完成私有空间维修验收"))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        mockMvc.perform(post("/api/v1/me/repairs/" + id + "/evaluation")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("satisfactionScore", 5, "comment", "响应及时"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("EVALUATED")))
                .andExpect(jsonPath("$.data.satisfactionScore", is(5)));

        String eventsResponse = mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/events")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(10)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode surveyEvent = findEvent(eventsResponse, "SUBMIT_SURVEY");
        JsonNode surveyPayload = objectMapper.readTree(surveyEvent.path("payloadJson").asText());
        assertEquals("LOW", surveyPayload.path("riskLevel").asText());
        assertEquals(surveyImageId, surveyPayload.path("attachments").get(0).path("attachmentId").asLong());
        assertEquals("SURVEY_IMAGE", surveyPayload.path("attachments").get(0).path("kind").asText());
        assertEquals("SURVEY_VIDEO", surveyPayload.path("attachments").get(1).path("kind").asText());
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_attachment
                WHERE work_order_id = ? AND status = 'BOUND' AND bound_action = 'SUBMIT_SURVEY'
                """, Integer.class, id));
    }

    @Test
    public void propertyStaffUploadsAndDeletesAttachmentThroughJavaOssSdk() throws Exception {
        String ownerToken = token(ACC_OWNER, "C_USER", UID_OWNER);
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);
        String managerToken = token(ACC_PROPERTY_MANAGER, "SYS_USER", USR_PROPERTY_MANAGER);
        long opid = jdbcTemplate.queryForObject("""
                SELECT opid FROM c_owner_property
                WHERE uid = ? AND tenant_id = ?
                ORDER BY opid LIMIT 1
                """, Long.class, UID_OWNER, TENANT);
        long id = createPrivate(ownerToken, opid, "IT-报修-OSS附件-" + System.nanoTime());
        action(staffToken, id, "accept", Map.of("remark", "受理"));
        action(staffToken, id, "verify-location", Map.of("remark", "核验"));
        action(managerToken, id, "assign", Map.of(
                "assignedUserId", USR_PROPERTY_STAFF,
                "assigneeRoleKey", "PROPERTY_STAFF",
                "remark", "派单"));
        action(staffToken, id, "start-survey", Map.of("remark", "开始初勘"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "现场.jpg", "image/jpeg", new byte[1024]);
        String uploadResponse = mockMvc.perform(multipart("/api/v1/admin/repair-work-orders/" + id
                        + "/attachments")
                        .file(file)
                        .param("attachmentKind", "SURVEY_IMAGE")
                        .param("contentType", "image/jpeg")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andExpect(jsonPath("$.data.actualSize", is(1024)))
                .andExpect(jsonPath("$.data.etag", is("etag-test")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long attachmentId = objectMapper.readTree(uploadResponse).path("data").path("attachmentId").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/admin/repair-work-orders/" + id + "/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_repair_attachment WHERE attachment_id = ?",
                Integer.class, attachmentId));
    }

    @Test
    public void propertyStaffSubmitsInspectionFromSubmittedWithOneBusinessAction() throws Exception {
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);
        String created = mockMvc.perform(post("/api/v1/admin/repair-work-orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "buildingId", 30001,
                                "title", "IT-报修-统一勘验-" + System.nanoTime(),
                                "description", "物业现场勘验统一提交",
                                "locationText", "1号楼设备层",
                                "category", "PUBLIC_FACILITY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")))
                .andReturn().getResponse().getContentAsString();
        long id = dataId(created);

        MockMultipartFile image = new MockMultipartFile(
                "file", "勘验现场.jpg", "image/jpeg", new byte[1024]);
        String uploaded = mockMvc.perform(multipart("/api/v1/admin/repair-work-orders/" + id + "/attachments")
                        .file(image)
                        .param("attachmentKind", "SURVEY_IMAGE")
                        .param("contentType", "image/jpeg")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("READY")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long imageId = objectMapper.readTree(uploaded).path("data").path("attachmentId").asLong();

        action(staffToken, id, "submit-inspection", Map.of(
                "surveySummary", "设备门合页损坏，需要更换并校正门框",
                "riskLevel", "LOW",
                "evidenceImageAttachmentIds", List.of(imageId),
                "remark", "管理后台提交勘验记录"))
                .andExpect(jsonPath("$.data.status", is("SURVEY_COMPLETED")))
                .andExpect(jsonPath("$.data.locationLocked", is(true)))
                .andExpect(jsonPath("$.data.assignedUserId", is((int) USR_PROPERTY_STAFF)));

        List<String> actions = jdbcTemplate.queryForList("""
                SELECT action FROM t_repair_work_order_event
                WHERE work_order_id = ?
                ORDER BY event_id
                """, String.class, id);
        assertTrue(actions.containsAll(List.of(
                "ACCEPT", "VERIFY_LOCATION", "ASSIGN", "START_SURVEY", "SUBMIT_SURVEY")));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_attachment
                WHERE attachment_id = ? AND status = 'BOUND' AND bound_action = 'SUBMIT_SURVEY'
                """, Integer.class, imageId));
    }

    @Test
    public void publicRepairWithoutBuilding_requiresManualLocationBeforeVerify() throws Exception {
        String ownerToken = token(ACC_OWNER, "C_USER", UID_OWNER);
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);
        String title = "IT-报修-公共区域门禁滴水-" + System.nanoTime();

        long id = createPublic(ownerToken, title);

        mockMvc.perform(get("/api/v1/me/repairs/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("NEED_MANUAL_LOCATION")))
                .andExpect(jsonPath("$.data.needManualLocation", is(true)));

        actionRaw(staffToken, id, "verify-location", Map.of("remark", "尝试直接核验"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is(42507)));

        Long roomId = jdbcTemplate.queryForObject("""
                SELECT room_id FROM c_owner_property
                WHERE tenant_id = ? AND building_id = 30002
                ORDER BY room_id LIMIT 1
                """, Long.class, TENANT);
        long locationImageId = readyAttachment(id, "LOCATION_IMAGE", "image/jpeg", 1024L);
        action(staffToken, id, "correct-location", Map.of(
                "buildingId", 30002,
                "roomId", roomId,
                "locationText", "2号楼大堂门禁",
                "reason", "现场确认在2号楼",
                "fieldSupplement", "物业现场确认门禁顶部管线渗水",
                "evidenceImageAttachmentIds", List.of(locationImageId)))
                .andExpect(jsonPath("$.data.status", is("PENDING_VERIFY")))
                .andExpect(jsonPath("$.data.needManualLocation", is(false)))
                .andExpect(jsonPath("$.data.roomId", is(roomId.intValue())));
        action(staffToken, id, "verify-location", Map.of("remark", "位置锁定"))
                .andExpect(jsonPath("$.data.status", is("VERIFIED")))
                .andExpect(jsonPath("$.data.locationLocked", is(true)));

        String response = mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/events")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode correctEvent = findEvent(response, "CORRECT_LOCATION");
        JsonNode payload = objectMapper.readTree(correctEvent.path("payloadJson").asText());
        assertEquals("物业现场确认门禁顶部管线渗水", payload.path("fieldSupplement").asText());
        assertTrue(payload.path("attachments").isArray());
        assertEquals(locationImageId, payload.path("attachments").get(0).path("attachmentId").asLong());
    }

    @Test
    public void propertyStaffCanRegisterAdminPublicRepair() throws Exception {
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);
        String title = "IT-报修-物业登记公共区域-" + System.nanoTime();

        String response = mockMvc.perform(post("/api/v1/admin/repair-work-orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", title,
                                "description", "物业客服接报公共区域报修",
                                "publicAreaScope", "COMMUNITY",
                                "locationText", "地下车库入口",
                                "category", "FIRE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source", is("ADMIN_PC")))
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")))
                .andExpect(jsonPath("$.data.spaceScope", is("PUBLIC")))
                .andExpect(jsonPath("$.data.publicAreaScope", is("COMMUNITY")))
                .andExpect(jsonPath("$.data.needManualLocation", is(false)))
                .andExpect(jsonPath("$.data.category", is("FIRE_PROTECTION")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long workOrderId = objectMapper.readTree(response).path("data").path("workOrderId").asLong();

        MockMultipartFile attachment = new MockMultipartFile(
                "file", "设备门现场说明.pdf", "application/pdf", new byte[1024]);
        mockMvc.perform(multipart("/api/v1/admin/repair-work-orders/" + workOrderId + "/intake-attachments")
                        .file(attachment)
                        .param("contentType", "application/pdf")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentKind", is("INTAKE_ATTACHMENT")))
                .andExpect(jsonPath("$.data.status", is("BOUND")))
                .andExpect(jsonPath("$.data.originalFileName", is("设备门现场说明.pdf")));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_attachment
                WHERE work_order_id = ? AND attachment_kind = 'INTAKE_ATTACHMENT'
                  AND status = 'BOUND' AND bound_action = 'ADMIN_REGISTER_PUBLIC'
                """, Integer.class, workOrderId));
    }

    @Test
    public void propertyStaffCannotRegisterCommunityPublicRepairWithoutLocation() throws Exception {
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);

        mockMvc.perform(post("/api/v1/admin/repair-work-orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "IT-报修-小区公共位置缺失-" + System.nanoTime(),
                                "description", "位置范围已知为小区公共区域，但未填写具体位置",
                                "publicAreaScope", "COMMUNITY",
                                "category", "ELECTRICAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("小区公共区域报修必须填写具体位置")));
    }

    @Test
    public void propertyStaffCanLoadRepairLocationOptions() throws Exception {
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);

        mockMvc.perform(get("/api/v1/admin/repair-work-orders/location-options")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.communities.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.communities[0].buildings.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    public void ownerSubmittedRepair_isVisibleToPropertyStaffMobileLogin() throws Exception {
        long opid = jdbcTemplate.queryForObject("""
                SELECT opid FROM c_owner_property
                WHERE uid = ? AND tenant_id = ?
                ORDER BY opid LIMIT 1
                """, Long.class, UID_OWNER, TENANT);
        String ownerToken = token(ACC_OWNER, "C_USER", UID_OWNER);
        String title = "IT-报修-物业C端可见-" + System.nanoTime();
        createPrivate(ownerToken, opid, title);

        String staffToken = login("13800000022", "AUTO");

        mockMvc.perform(get("/api/v1/admin/repair-work-orders")
                        .param("keyword", title)
                        .param("page", "1")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.items[0].title", is(title)));
    }

    @Test
    public void buildingRepairDecisionUsesCommunityDefaultAndAllowsPreStartOverride() throws Exception {
        String ownerRepToken = token(999812L, "SYS_USER", 800102L);
        jdbcTemplate.update("""
                UPDATE t_tenant_community
                SET building_repair_default_decision_channel = 'ONLINE'
                WHERE tenant_id = ?
                """, TENANT);

        long defaultOrderId = insertSupplierRecommendedBuildingRepair(
                "IT-报修-社区默认在线表决-" + System.nanoTime());
        action(ownerRepToken, defaultOrderId, "start-local-decision", Map.of(
                "scopeType", "BUILDING",
                "scopeLabel", "1号楼",
                "remark", "采用社区默认表决方式"))
                .andExpect(jsonPath("$.data.status", is("LOCAL_DECISION_PENDING")));

        assertEquals("ONLINE", jdbcTemplate.queryForObject("""
                SELECT decision_channel
                FROM t_repair_local_decision
                WHERE work_order_id = ?
                """, String.class, defaultOrderId));
        assertEquals("COMMUNITY_DEFAULT", decisionChannelSource(defaultOrderId));

        long overriddenOrderId = insertSupplierRecommendedBuildingRepair(
                "IT-报修-工单覆盖微信接龙-" + System.nanoTime());
        action(ownerRepToken, overriddenOrderId, "start-local-decision", Map.of(
                "scopeType", "BUILDING",
                "decisionChannel", "WECHAT",
                "scopeLabel", "1号楼",
                "remark", "本工单改用微信接龙"))
                .andExpect(jsonPath("$.data.status", is("LOCAL_DECISION_PENDING")));

        assertEquals("WECHAT", jdbcTemplate.queryForObject("""
                SELECT decision_channel
                FROM t_repair_local_decision
                WHERE work_order_id = ?
                """, String.class, overriddenOrderId));
        assertEquals("WORK_ORDER_OVERRIDE", decisionChannelSource(overriddenOrderId));
    }

    @Test
    public void publicBuildingRepair_fullDeliveryContractAndOwnerAcceptance() throws Exception {
        String votingOwnerToken = token(999812L, "C_USER", 70001L);
        String staffToken = token(ACC_PROPERTY_STAFF, "SYS_USER", USR_PROPERTY_STAFF);
        String managerToken = token(ACC_PROPERTY_MANAGER, "SYS_USER", USR_PROPERTY_MANAGER);
        String ownerRepToken = token(999812L, "SYS_USER", 800102L);
        String directorToken = token(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR);
        String title = "IT-报修-单楼栋接龙维修-" + System.nanoTime();

        String created = mockMvc.perform(post("/api/v1/admin/repair-work-orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "buildingId", 30001,
                                "title", title,
                                "description", "1号楼公共管道维修",
                                "locationText", "1号楼东侧排水管",
                                "category", "PUBLIC_FACILITY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")))
                .andReturn().getResponse().getContentAsString();
        long id = dataId(created);

        action(staffToken, id, "accept", Map.of("remark", "物业受理"))
                .andExpect(jsonPath("$.data.status", is("PENDING_VERIFY")));
        action(staffToken, id, "verify-location", Map.of("remark", "现场勘验属实"))
                .andExpect(jsonPath("$.data.status", is("VERIFIED")));
        action(managerToken, id, "assign", Map.of(
                "assignedUserId", USR_PROPERTY_STAFF,
                "assigneeRoleKey", "PROPERTY_STAFF",
                "remark", "物业工程跟进"))
                .andExpect(jsonPath("$.data.status", is("ASSIGNED")));
        action(staffToken, id, "start-survey", Map.of("remark", "开始现场勘验"))
                .andExpect(jsonPath("$.data.status", is("SURVEYING")));
        long publicSurveyImageId = readyAttachment(id, "SURVEY_IMAGE", "image/jpeg", 1024L);
        action(staffToken, id, "submit-survey", Map.of(
                "surveySummary", "1号楼公共排水管堵塞，需供应商维修",
                "riskLevel", "MEDIUM",
                "evidenceImageAttachmentIds", List.of(publicSurveyImageId),
                "remark", "勘验完成"))
                .andExpect(jsonPath("$.data.status", is("SURVEY_COMPLETED")));
        jdbcTemplate.update("UPDATE t_tenant_community SET repair_estimate_required = 1 WHERE tenant_id = ?", TENANT);
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/planning-policy")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internalEstimateRequired", is(true)));
        actionRaw(staffToken, id, "submit-plan", Map.of(
                "fundSource", "BUILDING_MAINTENANCE_FUND",
                "remark", "缺少内部估算"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("当前社区要求填写物业内部估算金额")));
        jdbcTemplate.update("UPDATE t_tenant_community SET repair_estimate_required = 0 WHERE tenant_id = ?", TENANT);
        action(staffToken, id, "submit-plan", Map.of(
                "planBudget", new BigDecimal("9000.00"),
                "publicCeilingPrice", new BigDecimal("8500.00"),
                "fundSource", "BUILDING_MAINTENANCE_FUND",
                "remark", "维修范围及询价口径已确认"))
                .andExpect(jsonPath("$.data.status", is("PLAN_SUBMITTED")))
                .andExpect(jsonPath("$.data.planBudget", is(9000.0)))
                .andExpect(jsonPath("$.data.publicCeilingPrice", is(8500.0)));
        long supplierA = registerSupplier(managerToken, "IT-供应商-甲-" + System.nanoTime(), "91310000A000000001");
        long supplierB = registerSupplier(managerToken, "IT-供应商-乙-" + System.nanoTime(), "91310000A000000002");
        String provisionalSupplierName = "IT-供应商-丙-" + System.nanoTime();
        long supplierC = registerMinimalSupplier(managerToken, provisionalSupplierName);
        assertEquals(supplierC, registerMinimalSupplier(managerToken, provisionalSupplierName));
        jdbcTemplate.update("""
                UPDATE t_supplier_tenant_relation
                SET enterprise_verification_status = 'VERIFIED'
                WHERE tenant_id = ? AND supplier_dept_id IN (?, ?)
                """, TENANT, supplierA, supplierB);
        jdbcTemplate.update("""
                UPDATE t_supplier_tenant_relation
                SET relation_type = 'FRAMEWORK', service_category = 'PUBLIC_AREA_FACILITY',
                    status = 'ACTIVE', valid_from = CURRENT_DATE, valid_until = CURRENT_DATE + 365
                WHERE tenant_id = ? AND supplier_dept_id = ?
                """, TENANT, supplierA);
        mockMvc.perform(get("/api/v1/admin/supplier-organizations")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].supplierDeptId", hasItem((int) supplierA)));
        mockMvc.perform(get("/api/v1/admin/supplier-framework-relations")
                        .param("serviceCategory", "PUBLIC_AREA_FACILITY")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].supplierDeptId", is((int) supplierA)));
        action(managerToken, id, "quote-invitations", Map.of(
                "supplierDeptIds", List.of(supplierA, supplierB, supplierC),
                "deadline", LocalDateTime.now().plusDays(3).toString(),
                "remark", "向三家供应商发出邀价"))
                .andExpect(jsonPath("$.data.status", is("QUOTE_COLLECTING")));
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/quote-invitations")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(3)))
                .andExpect(jsonPath("$.data[*].supplierDeptId", hasItem((int) supplierA)));
        actionRaw(managerToken, id, "quote-invitations", Map.of(
                "supplierDeptIds", List.of(supplierA),
                "remark", "重复邀请"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(42501)));
        long supplierD = registerMinimalSupplier(managerToken, "IT-供应商-追加-" + System.nanoTime());
        actionRaw(managerToken, id, "quote-invitations", Map.of(
                "supplierDeptIds", List.of(supplierD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(42501)));
        action(managerToken, id, "quote-invitations", Map.of(
                "supplierDeptIds", List.of(supplierD),
                "remark", "原受邀供应商响应不足，追加询价"))
                .andExpect(jsonPath("$.data.status", is("QUOTE_COLLECTING")));
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/quote-invitations")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(4)));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM t_supplier_activation_invitation
                WHERE supplier_dept_id = ?
                """, Integer.class, supplierC));

        Long supplierInvitationId = jdbcTemplate.queryForObject("""
                SELECT invitation_id
                FROM t_supplier_activation_invitation
                WHERE supplier_dept_id = ? AND contact_phone = '13800000931' AND status = 'PENDING'
                """, Long.class, supplierA);
        JsonNode pendingAccount = supplierOrganization(managerToken, supplierA);
        assertEquals("PENDING_ACTIVATION", pendingAccount.path("accountStatus").asText());
        assertEquals(0, pendingAccount.path("activeAccountCount").asInt());
        assertEquals(supplierInvitationId.longValue(), pendingAccount.path("activationInvitationId").asLong());
        assertEquals("CONTACT_MISSING", supplierOrganization(managerToken, supplierC)
                .path("accountStatus").asText());
        mockMvc.perform(post("/api/v1/supplier-activation/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invitationId", supplierInvitationId,
                                "phone", "13800000931",
                                "smsCode", "123456",
                                "operatorName", "供应商甲报价经办人"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierDeptId", is((int) supplierA)))
                .andExpect(jsonPath("$.data.roleKey", is("SERVICE_PROVIDER_STAFF")));
        JsonNode activatedAccount = supplierOrganization(managerToken, supplierA);
        assertEquals("ACTIVATED", activatedAccount.path("accountStatus").asText());
        assertEquals(1, activatedAccount.path("activeAccountCount").asInt());
        assertEquals("13800000931", activatedAccount.path("loginPhone").asText());
        mockMvc.perform(post("/api/v1/supplier-activation/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invitationId", supplierInvitationId,
                                "phone", "13800000931",
                                "smsCode", "123456",
                                "operatorName", "供应商甲报价经办人"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(42524)));
        String supplierToken = loginSupplier("13800000931");
        mockMvc.perform(get("/api/v1/auth/menus")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].id", is("supplier-service")))
                .andExpect(jsonPath("$.data[0].pages.length()", is(1)))
                .andExpect(jsonPath("$.data[0].pages[0].id", is("supplier-workbench")));
        mockMvc.perform(get("/api/v1/supplier/repair-work-orders")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].workOrderId", hasItem((int) id)))
                .andExpect(jsonPath("$.data[0].planBudget").doesNotExist())
                .andExpect(jsonPath("$.data[0].fundSource").doesNotExist())
                .andExpect(jsonPath("$.data[0].publicCeilingPrice", is(8500.0)));
        MockMultipartFile supplierQuoteFile = new MockMultipartFile(
                "file", "供应商报价.pdf", "application/pdf", new byte[2048]);
        String supplierQuoteUpload = mockMvc.perform(multipart(
                        "/api/v1/supplier/repair-work-orders/" + id + "/quote-attachments")
                        .file(supplierQuoteFile)
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentKind", is("QUOTE_DOCUMENT")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long supplierQuoteAttachmentId = objectMapper.readTree(supplierQuoteUpload)
                .path("data").path("attachmentId").asLong();
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/attachments/"
                        + supplierQuoteAttachmentId + "/preview-url")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentId", is((int) supplierQuoteAttachmentId)))
                .andExpect(jsonPath("$.data.originalFileName", is("供应商报价.pdf")))
                .andExpect(jsonPath("$.data.contentType", is("application/pdf")))
                .andExpect(jsonPath("$.data.actualSize", is(2048)))
                .andExpect(jsonPath("$.data.converted", is(false)))
                .andExpect(jsonPath("$.data.previewUrl", is("https://oss.example.test/repair-preview")));
        MockMultipartFile supplierExcelFile = new MockMultipartFile(
                "file", "供应商报价.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "excel-source".getBytes(StandardCharsets.UTF_8));
        String supplierExcelUpload = mockMvc.perform(multipart(
                        "/api/v1/supplier/repair-work-orders/" + id + "/quote-attachments")
                        .file(supplierExcelFile)
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long supplierExcelAttachmentId = objectMapper.readTree(supplierExcelUpload)
                .path("data").path("attachmentId").asLong();
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/attachments/"
                        + supplierExcelAttachmentId + "/preview-url")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentId", is((int) supplierExcelAttachmentId)))
                .andExpect(jsonPath("$.data.originalFileName", is("供应商报价.xlsx")))
                .andExpect(jsonPath("$.data.contentType", is("application/pdf")))
                .andExpect(jsonPath("$.data.actualSize", is(12)))
                .andExpect(jsonPath("$.data.converted", is(true)))
                .andExpect(jsonPath("$.data.previewUrl", is("https://oss.example.test/repair-preview")));
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/attachments/"
                        + supplierExcelAttachmentId + "/preview-url")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.converted", is(true)));
        verify(documentPreviewConverter, times(1))
                .convertExcelToPdf(anyString(), anyString(), any(byte[].class));
        mockMvc.perform(post("/api/v1/supplier/repair-work-orders/" + id + "/quote")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "quoteAmount", new BigDecimal("8000.00"),
                                "quoteSummary", "供应商在线结构化报价",
                                "attachmentId", supplierQuoteAttachmentId,
                                "remark", "供应商本人提交"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planBudget").doesNotExist())
                .andExpect(jsonPath("$.data.fundSource").doesNotExist())
                .andExpect(jsonPath("$.data.publicCeilingPrice", is(8500.0)));
        assertEquals("SUPPLIER_ONLINE", jdbcTemplate.queryForObject("""
                SELECT submission_source FROM t_repair_supplier_quote
                WHERE work_order_id = ? AND supplier_dept_id = ?
                """, String.class, id, supplierA));
        assertEquals("BOUND", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_attachment WHERE attachment_id = ?",
                String.class, supplierQuoteAttachmentId));

        String secondInvitationResponse = mockMvc.perform(post(
                        "/api/v1/admin/supplier-organizations/" + supplierA + "/activation-invitations")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactName", "供应商甲第二经办人",
                                "contactPhone", "13800000932",
                                "validHours", 24))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long secondInvitationId = objectMapper.readTree(secondInvitationResponse)
                .path("data").path("invitationId").asLong();
        mockMvc.perform(post("/api/v1/supplier-activation/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invitationId", secondInvitationId,
                                "phone", "13800000932",
                                "smsCode", "123456",
                                "operatorName", "供应商甲第二经办人"))))
                .andExpect(status().isOk());
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id = u.user_id
                JOIN sys_role r ON r.role_id = ur.role_id
                WHERE u.dept_id = ? AND r.role_key = 'SERVICE_PROVIDER_STAFF'
                """, Integer.class, supplierA));

        String expiredInvitationResponse = mockMvc.perform(post(
                        "/api/v1/admin/supplier-organizations/" + supplierA + "/activation-invitations")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "contactName", "过期邀请经办人",
                                "contactPhone", "13800000933",
                                "validHours", 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long expiredInvitationId = objectMapper.readTree(expiredInvitationResponse)
                .path("data").path("invitationId").asLong();
        jdbcTemplate.update("UPDATE t_supplier_activation_invitation SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE invitation_id = ?",
                expiredInvitationId);
        mockMvc.perform(post("/api/v1/supplier-activation/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invitationId", expiredInvitationId,
                                "phone", "13800000933",
                                "smsCode", "123456",
                                "operatorName", "过期邀请经办人"))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is(42525)));
        assertEquals("EXPIRED", jdbcTemplate.queryForObject(
                "SELECT status FROM t_supplier_activation_invitation WHERE invitation_id = ?",
                String.class, expiredInvitationId));

        submitQuote(staffToken, id, supplierB, "9000.00");
        submitQuote(staffToken, id, supplierC, "9500.00")
                .andExpect(jsonPath("$.data.status", is("QUOTE_SUBMITTED")));
        submitQuote(staffToken, id, supplierC, "9400.00")
                .andExpect(jsonPath("$.data.status", is("QUOTE_SUBMITTED")));
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/supplier-quotes")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(3)))
                .andExpect(jsonPath("$.data[0].supplierDeptId", is((int) supplierA)))
                .andExpect(jsonPath("$.data[2].supplierDeptId", is((int) supplierC)))
                .andExpect(jsonPath("$.data[2].quoteAmount", is(9400.0)))
                .andExpect(jsonPath("$.data[2].revisionNo", is(2)))
                .andExpect(jsonPath("$.data[2].quoteStatus", is("ACTIVE")));
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id
                        + "/supplier-quotes/" + supplierC + "/history")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(2)))
                .andExpect(jsonPath("$.data[0].quoteAmount", is(9400.0)))
                .andExpect(jsonPath("$.data[0].revisionNo", is(2)))
                .andExpect(jsonPath("$.data[0].quoteStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.data[1].quoteAmount", is(9500.0)))
                .andExpect(jsonPath("$.data[1].revisionNo", is(1)))
                .andExpect(jsonPath("$.data[1].quoteStatus", is("SUPERSEDED")));
        assertEquals(3, jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT supplier_dept_id)
                FROM t_repair_supplier_quote
                WHERE work_order_id = ? AND quote_status = 'ACTIVE'
                """, Integer.class, id));

        Long quoteId = jdbcTemplate.queryForObject("""
                SELECT quote_id FROM t_repair_supplier_quote
                WHERE work_order_id = ? AND supplier_dept_id = ? AND quote_status = 'ACTIVE'
                """, Long.class, id, supplierA);
        action(managerToken, id, "recommend-supplier", Map.of(
                "quoteId", quoteId,
                "selectionMethod", "COMPETITIVE_QUOTATION",
                "recommendationReason", "三家比价后，甲公司报价最低且材料齐全",
                "remark", "物业推荐甲公司"))
                .andExpect(jsonPath("$.data.status", is("SUPPLIER_RECOMMENDED")));

        actionRaw(managerToken, id, "start-assembly-decision", Map.of(
                "packageId", 1,
                "remark", "错误尝试关联业主大会"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("仅小区公共维修资金或公共收益维修可以关联业主大会表决")));

        action(ownerRepToken, id, "start-local-decision", Map.of(
                "scopeType", "BUILDING",
                "decisionChannel", "ONLINE",
                "scopeLabel", "1号楼",
                "remark", "物业选择C端在线表决"))
                .andExpect(jsonPath("$.data.status", is("LOCAL_DECISION_PENDING")));

        long decisionId = jdbcTemplate.queryForObject(
                "SELECT decision_id FROM t_repair_local_decision WHERE work_order_id = ?",
                Long.class, id);
        long ownerOpid = jdbcTemplate.queryForObject("""
                SELECT opid FROM c_owner_property
                WHERE uid = ? AND tenant_id = ? AND building_id = 30001 AND account_status = 1
                ORDER BY opid LIMIT 1
                """, Long.class, 70001L, TENANT);
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/local-decision")
                .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decisionChannel", is("ONLINE")))
                .andExpect(jsonPath("$.data.currentThresholdPassed", is(false)));
        mockMvc.perform(get("/api/v1/me/repair-local-decisions")
                        .header("Authorization", "Bearer " + votingOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].decisionId", is((int) decisionId)))
                .andExpect(jsonPath("$.data[0].quoteAmount", is(8000.0)))
                .andExpect(jsonPath("$.data[0].myChoice").doesNotExist());
        mockMvc.perform(get("/api/v1/me/repair-local-decisions/" + decisionId + "/quote-preview")
                        .param("opid", String.valueOf(ownerOpid))
                        .header("Authorization", "Bearer " + votingOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewUrl", is("https://oss.example.test/repair-preview")));
        mockMvc.perform(post("/api/v1/me/repair-local-decisions/" + decisionId + "/votes")
                        .header("Authorization", "Bearer " + votingOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("opid", ownerOpid, "choice", "AGREE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myChoice", is("AGREE")));
        BigDecimal submittedVotingArea = jdbcTemplate.queryForObject("""
                SELECT SUM(build_area) FROM t_repair_solitaire_entry
                WHERE decision_id = ? AND submission_channel = 'ONLINE'
                """, BigDecimal.class, decisionId);
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/local-decision")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participatedOwnerCount", is(1)))
                .andExpect(jsonPath("$.data.participatedArea", is(submittedVotingArea.doubleValue())))
                .andExpect(jsonPath("$.data.currentThresholdPassed", is(true)))
                .andExpect(jsonPath("$.data.agreeOwnerCount").doesNotExist());
        assertEquals("ONLINE", jdbcTemplate.queryForObject("""
                SELECT submission_channel FROM t_repair_solitaire_entry
                WHERE decision_id = ? AND room_id = (
                    SELECT room_id FROM c_owner_property WHERE opid = ?
                )
                """, String.class, decisionId, ownerOpid));

        action(managerToken, id, "pause-local-decision", Map.of("remark", "临时暂停，保留选票"))
                .andExpect(jsonPath("$.data.status", is("LOCAL_DECISION_PENDING")));
        assertEquals("PAUSED", jdbcTemplate.queryForObject(
                "SELECT result FROM t_repair_local_decision WHERE decision_id = ?",
                String.class, decisionId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_repair_solitaire_entry WHERE decision_id = ?",
                Integer.class, decisionId));
        mockMvc.perform(get("/api/v1/me/repair-local-decisions")
                        .header("Authorization", "Bearer " + votingOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));
        actionRaw(managerToken, id, "complete-local-decision", Map.of(
                "entries", List.of(), "remark", "暂停状态错误结算"))
                .andExpect(status().isConflict());

        action(managerToken, id, "resume-local-decision", Map.of("remark", "恢复表决"))
                .andExpect(jsonPath("$.data.status", is("LOCAL_DECISION_PENDING")));
        assertEquals("COLLECTING", jdbcTemplate.queryForObject(
                "SELECT result FROM t_repair_local_decision WHERE decision_id = ?",
                String.class, decisionId));
        mockMvc.perform(get("/api/v1/me/repair-local-decisions")
                        .header("Authorization", "Bearer " + votingOwnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].myChoice", is("AGREE")));

        jdbcTemplate.update("DELETE FROM t_repair_solitaire_entry WHERE decision_id = ?", decisionId);
        jdbcTemplate.update("UPDATE t_repair_local_decision SET decision_channel = 'WECHAT' WHERE decision_id = ?",
                decisionId);

        String decisionRoomsResponse = mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/local-decision-rooms")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> entries = new java.util.ArrayList<>();
        objectMapper.readTree(decisionRoomsResponse).path("data").forEach(room -> entries.add(Map.of(
                "roomId", room.path("roomId").asLong(),
                "choice", "AGREE",
                "originalText", "微信接龙同意")));
        long solitaireScreenshotId = readyAttachment(id, "SOLITAIRE_SCREENSHOT", "image/png", 4096L);
        action(staffToken, id, "complete-local-decision", Map.of(
                "entries", entries,
                "evidenceAttachmentId", solitaireScreenshotId,
                "remark", "物业完成接龙核验"))
                .andExpect(jsonPath("$.data.status", is("LOCAL_DECISION_PASSED")));
        assertEquals("BOUND", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_attachment WHERE attachment_id = ?",
                String.class, solitaireScreenshotId));

        long approvalDocumentId = readyAttachment(id, "APPROVAL_DOCUMENT", "application/pdf", 8192L);
        action(managerToken, id, "approval-package", Map.of(
                "officialDocumentAttachmentId", approvalDocumentId,
                "remark", "物业上传正式报审文件并生成不可变合并包"))
                .andExpect(jsonPath("$.data.status", is("PRICE_REVIEW_PENDING")));
        assertEquals("BOUND", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_attachment WHERE attachment_id = ?",
                String.class, approvalDocumentId));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_approval_attachment attachment
                JOIN t_repair_approval_package package ON package.package_id = attachment.package_id
                WHERE package.work_order_id = ?
                  AND attachment.attachment_type IN ('APPROVAL_DOCUMENT', 'SOLITAIRE_SCREENSHOT')
                """, Integer.class, id));

        action(directorToken, id, "price-review", Map.of(
                "reviewMode", "INTERNAL_PRICE_REVIEW",
                "reviewedAmount", new BigDecimal("8000.00"),
                "conclusion", "APPROVED",
                "opinion", "价格与维修范围匹配"))
                .andExpect(jsonPath("$.data.status", is("GOVERNANCE_PENDING")));

        action(directorToken, id, "governance-confirm", Map.of("remark", "主任确认同意报批"))
                .andExpect(jsonPath("$.data.status", is("GOVERNANCE_CONFIRMED")));
        long sealedDocumentId = readyAttachment(
                id, "GOVERNANCE_SEALED_DOCUMENT", "image/png", 8192L);
        action(directorToken, id, "seal", Map.of(
                "sealingMethod", "UPLOADED_PHYSICAL",
                "sealedAttachmentId", sealedDocumentId,
                "remark", "业委会盖章"))
                .andExpect(jsonPath("$.data.status", is("SEALED")));
        assertEquals("BOUND", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_attachment WHERE attachment_id = ?",
                String.class, sealedDocumentId));
        assertEquals("PHYSICAL_STAMP_FILE_UPLOADED", jdbcTemplate.queryForObject("""
                SELECT verification_status FROM t_committee_seal_usage
                WHERE business_type = 'REPAIR' AND business_id = ?
                ORDER BY usage_id DESC LIMIT 1
                """, String.class, id));

        jdbcTemplate.update("""
                UPDATE t_supplier_tenant_relation
                SET enterprise_verification_status = 'PENDING_VERIFICATION'
                WHERE tenant_id = ? AND supplier_dept_id = ?
                """, TENANT, supplierA);
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/contract-supplier-candidate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierDeptId", is((int) supplierA)))
                .andExpect(jsonPath("$.data.supplierName").isNotEmpty())
                .andExpect(jsonPath("$.data.verificationStatus", is("PENDING_VERIFICATION")))
                .andExpect(jsonPath("$.data.contractEligible", is(false)))
                .andExpect(jsonPath("$.data.contractEligibilityMessage",
                        is("推荐供应商的企业主体尚未完成独立核验，暂不能发起合同签署")));
        jdbcTemplate.update("""
                UPDATE t_supplier_tenant_relation
                SET enterprise_verification_status = 'VERIFIED'
                WHERE tenant_id = ? AND supplier_dept_id = ?
                """, TENANT, supplierA);
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/contract-supplier-candidate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierDeptId", is((int) supplierA)))
                .andExpect(jsonPath("$.data.contractEligible", is(true)))
                .andExpect(jsonPath("$.data.contractEligibilityMessage").doesNotExist());

        action(managerToken, id, "contracts", Map.of(
                "supplierDeptId", supplierA,
                "contractAmount", new BigDecimal("8000.00"),
                "repairScopeHash", "locked-repair-scope-hash",
                "fundSource", "BUILDING_MAINTENANCE_FUND",
                "signingMethod", "MIXED",
                "contractFileHash", "contract-draft-hash",
                "remark", "创建三方施工合同"))
                .andExpect(jsonPath("$.data.status", is("CONTRACT_SIGNING")));
        action(managerToken, id, "contracts/complete", Map.of(
                "signatures", List.of(
                        signature("OWNERS_ASSEMBLY_OR_GROUP", "1号楼业主组织"),
                        signature("PROPERTY", "测试物业"),
                        signature("SUPPLIER", "测试供应商")),
                "finalContractFileHash", "signed-three-party-contract-hash",
                "remark", "三方合同签署完成"))
                .andExpect(jsonPath("$.data.status", is("CONTRACT_EFFECTIVE")));

        Long affectedRoomId = jdbcTemplate.queryForObject("""
                SELECT room_id FROM c_owner_property
                WHERE tenant_id = ? AND building_id = 30001
                ORDER BY room_id LIMIT 1
                """, Long.class, TENANT);
        action(managerToken, id, "acceptance-scope", Map.of(
                "rooms", List.of(Map.of("roomId", affectedRoomId, "affectedReason", "本次修复该户渗水")),
                "remark", "锁定受影响房屋"));
        action(staffToken, id, "start-work", Map.of("remark", "供应商进场维修"))
                .andExpect(jsonPath("$.data.status", is("IN_PROGRESS")));
        action(staffToken, id, "submit-acceptance", Map.of("remark", "供应商完工并提交验收"))
                .andExpect(jsonPath("$.data.status", is("PENDING_ACCEPTANCE")));
        action(staffToken, id, "acceptance-records", Map.of(
                "roomId", affectedRoomId,
                "participantType", "AFFECTED_OWNER",
                "participantName", "受影响业主代表",
                "conclusion", "PASSED",
                "signatureHash", "affected-owner-signature-hash",
                "remark", "物业代录纸质验收单"));
        action(ownerRepToken, id, "acceptance-records", Map.of(
                "participantType", "OWNER_REPRESENTATIVE",
                "participantName", "1号楼楼组长",
                "conclusion", "PASSED",
                "signatureHash", "owner-representative-signature-hash",
                "remark", "楼组长验收通过"));
        action(ownerRepToken, id, "accept-completed", Map.of("remark", "受影响业主与楼组长均验收通过"))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));
        action(managerToken, id, "payment-requests", Map.of(
                "milestoneType", "ACCEPTANCE",
                "requestedAmount", new BigDecimal("8000.00"),
                "conditionEvidenceHash", "acceptance-payment-evidence-hash",
                "remark", "维修模块发起验收款申请"))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        Integer approvalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_repair_governance_approval WHERE work_order_id = ?",
                Integer.class, id);
        Integer sealCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_repair_governance_seal WHERE work_order_id = ?",
                Integer.class, id);
        Integer paymentRequestCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_repair_payment_request WHERE work_order_id = ? AND status = 'PENDING_FINANCE'",
                Integer.class, id);
        assertEquals(1, approvalCount);
        assertEquals(1, sealCount);
        assertEquals(1, paymentRequestCount);
    }

    @Test
    public void committeeDirectorCannotRegisterAdminPublicRepair() throws Exception {
        String directorToken = token(ACC_DIRECTOR, "SYS_USER", USR_DIRECTOR);

        mockMvc.perform(post("/api/v1/admin/repair-work-orders")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "IT-报修-主任越权登记-" + System.nanoTime(),
                                "description", "业委会主任不承担物业接报登记职责",
                                "locationText", "小区主路",
                                "category", "PUBLIC_FACILITY"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void gridMemberAdminList_canSeeCrossTenantBuildingScope() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO c_owner_property
                    (uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status)
                VALUES (70001, ?, 30001, ?, 88.00, 0, 1)
                ON CONFLICT DO NOTHING
                """, TENANT_CROSS, CROSS_ROOM);
        jdbcTemplate.update("""
                INSERT INTO sys_dept_tenant_scope (dept_id, tenant_id, assigned_by, status)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (dept_id, tenant_id)
                DO UPDATE SET status = 1, assigned_by = EXCLUDED.assigned_by, updated_at = now()
                """, DEPT_COMMUNITY, TENANT_CROSS, USR_COMMUNITY);
        jdbcTemplate.update("""
                INSERT INTO sys_dept_building_scope (dept_id, tenant_id, building_id, assigned_by, status)
                VALUES (?, ?, 30001, ?, 1)
                ON CONFLICT (dept_id, tenant_id, building_id)
                DO UPDATE SET status = 1, assigned_by = EXCLUDED.assigned_by, updated_at = now()
                """, DEPT_GRID, TENANT_CROSS, USR_COMMUNITY);
        String title = "IT-报修-跨小区网格-" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO t_repair_work_order
                    (tenant_id, title, description, source, space_scope, status,
                     reporter_account_id, reporter_uid, building_id, location_text, category)
                VALUES (?, ?, '跨小区网格可见性测试', 'C_OWNER_APP', 'PUBLIC', 'SUBMITTED',
                        999812, 70001, 30001, '跨小区 1 号楼公共区域', 'PUBLIC_FACILITY')
                """, TENANT_CROSS, title);
        String gridToken = token(ACC_GRID, "SYS_USER", USR_GRID);

        mockMvc.perform(get("/api/v1/admin/repair-work-orders")
                        .param("keyword", title)
                        .header("Authorization", "Bearer " + gridToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.items[0].tenantId", is((int) TENANT_CROSS)))
                .andExpect(jsonPath("$.data.items[0].buildingId", is(30001)));
    }

    @Test
    public void streetAdminWithoutTenantHint_usesDefaultTenantRepairOrders() throws Exception {
        String title = "IT-报修-街道默认小区视野-" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO t_repair_work_order
                    (tenant_id, title, description, source, space_scope, status,
                     reporter_account_id, reporter_uid, building_id, location_text, category)
                VALUES (?, ?, '街道全辖区工单可见性测试', 'C_OWNER_APP', 'PUBLIC', 'SUBMITTED',
                        999812, 70001, 30001, '跨小区公共区域', 'PUBLIC_FACILITY')
                """, TENANT, title);
        String streetToken = token(ACC_STREET, "SYS_USER", USR_STREET, null);

        String response = mockMvc.perform(get("/api/v1/admin/repair-work-orders")
                        .param("keyword", title)
                        .header("Authorization", "Bearer " + streetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.items[0].tenantId", is((int) TENANT)))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).path("data").path("items").get(0).path("workOrderId").asLong();

        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id)
                        .header("Authorization", "Bearer " + streetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId", is((int) TENANT)));
    }

    private long createPrivate(String token, long opid, String title) throws Exception {
        String body = json(Map.of(
                "opid", opid,
                "title", title,
                "description", "测试私有空间报修",
                "category", "PLUMBING",
                "evidenceText", "现场照片已由业主上传到对象存储"));
        String response = mockMvc.perform(post("/api/v1/me/repairs/private")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("SUBMITTED")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return dataId(response);
    }

    private long createPublic(String token, String title) throws Exception {
        String body = json(Map.of(
                "title", title,
                "description", "2栋大堂门禁一直滴水",
                "locationText", "业主描述：2栋大堂",
                "category", "PUBLIC_FACILITY"));
        String response = mockMvc.perform(post("/api/v1/me/repairs/public")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return dataId(response);
    }

    private org.springframework.test.web.servlet.ResultActions action(String token,
                                                                      long id,
                                                                      String action,
                                                                      Map<String, ?> body) throws Exception {
        return actionRaw(token, id, action, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    private org.springframework.test.web.servlet.ResultActions actionRaw(String token,
                                                                         long id,
                                                                         String action,
                                                                         Map<String, ?> body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/repair-work-orders/" + id + "/" + action)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private org.springframework.test.web.servlet.ResultActions submitQuote(String token,
                                                                           long id,
                                                                           long supplierDeptId,
                                                                           String amount) throws Exception {
        long quoteAttachmentId = readyAttachment(id, "QUOTE_DOCUMENT", "application/pdf", 2048L);
        return action(token, id, "supplier-quotes", Map.of(
                "supplierDeptId", supplierDeptId,
                "quoteAmount", new BigDecimal(amount),
                "quoteSummary", "维修报价",
                "attachmentId", quoteAttachmentId,
                "originalSource", "PAPER",
                "confirmationStatus", "OFFLINE_EVIDENCE_VERIFIED",
                "remark", "物业代录报价"));
    }

    private long registerSupplier(String token, String legalName, String uscc) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/supplier-organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "legalName", legalName,
                                "unifiedSocialCreditCode", uscc,
                                "contactName", "测试联系人",
                                "contactPhone", "13800000931"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").asLong();
    }

    private long registerMinimalSupplier(String token, String legalName) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/supplier-organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("legalName", legalName))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").asLong();
    }

    private JsonNode supplierOrganization(String token, long supplierDeptId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/admin/supplier-organizations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode supplier : objectMapper.readTree(response).path("data")) {
            if (supplier.path("supplierDeptId").asLong() == supplierDeptId) {
                return supplier;
            }
        }
        throw new AssertionError("未找到供应商 supplierDeptId=" + supplierDeptId);
    }

    private long insertSupplierRecommendedBuildingRepair(String title) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_work_order (
                    tenant_id, title, description, source, space_scope, status,
                    reporter_account_id, reporter_user_id, building_id, location_text,
                    need_manual_location, location_locked, category, fund_source, fund_gate_blocked
                ) VALUES (
                    ?, ?, '楼栋公共区域维修', 'ADMIN_PC', 'PUBLIC', 'SUPPLIER_RECOMMENDED',
                    ?, ?, 30001, '1号楼公共区域',
                    0, 1, 'PUBLIC_FACILITY', 'BUILDING_MAINTENANCE_FUND', 0
                )
                RETURNING work_order_id
                """, Long.class, TENANT, title, ACC_PROPERTY_STAFF, USR_PROPERTY_STAFF);
    }

    private long insertConfirmedQuoteAndRecommendation(long workOrderId, long supplierDeptId) {
        long invitationId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_quote_invitation (
                    work_order_id, tenant_id, supplier_dept_id, invited_by_user_id,
                    status, invitation_round, invitation_type
                ) VALUES (?, ?, ?, ?, 'SUBMITTED', 1, 'INITIAL')
                RETURNING quote_invitation_id
                """, Long.class, workOrderId, TENANT, supplierDeptId, USR_PROPERTY_MANAGER);
        String supplierName = jdbcTemplate.queryForObject(
                "SELECT legal_name FROM t_supplier_org_profile WHERE supplier_dept_id = ?",
                String.class, supplierDeptId);
        long quoteId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_supplier_quote (
                    work_order_id, tenant_id, supplier_name, quote_amount, quote_summary,
                    submitted_by_user_id, submitted_by_role_key, submitted_by_supplier,
                    supplier_confirmed, supplier_dept_id, quote_invitation_id, submission_source,
                    confirmation_status, original_source, original_attachment_hash,
                    quote_status, revision_no
                ) VALUES (
                    ?, ?, ?, 25000.00, '上一轮已确认报价',
                    ?, 'PROPERTY_MANAGER', 0,
                    1, ?, ?, 'PROPERTY_ENTRY',
                    'OFFLINE_EVIDENCE_VERIFIED', 'PAPER', 'previous-quote-hash',
                    'ACTIVE', 1
                )
                RETURNING quote_id
                """, Long.class, workOrderId, TENANT, supplierName,
                USR_PROPERTY_MANAGER, supplierDeptId, invitationId);
        jdbcTemplate.update("""
                INSERT INTO t_repair_supplier_recommendation (
                    work_order_id, tenant_id, quote_id, recommended_by_user_id,
                    recommendation_reason, single_source, single_source_reason,
                    selection_method, insufficient_quote_reason
                ) VALUES (?, ?, ?, ?, '上一轮物业推荐', 1, '原方案采用直接选择',
                          'DIRECT_AWARD', '原轮次报价不足三家')
                """, workOrderId, TENANT, quoteId, USR_PROPERTY_MANAGER);
        return quoteId;
    }

    private String decisionChannelSource(long workOrderId) {
        return jdbcTemplate.queryForObject("""
                SELECT payload_json ->> 'decisionChannelSource'
                FROM t_repair_work_order_event
                WHERE work_order_id = ? AND action = 'START_LOCAL_DECISION'
                ORDER BY event_id DESC
                LIMIT 1
                """, String.class, workOrderId);
    }

    private Map<String, Object> signature(String partyType, String signerName) {
        return Map.of(
                "partyType", partyType,
                "signerName", signerName,
                "signatureMethod", "PAPER_SCAN",
                "signatureFileHash", partyType + "-signature-hash",
                "signedAt", LocalDateTime.now().toString());
    }

    private String token(long accountId, String identityType, long activeIdentityId) {
        return jwtTokenProvider.generateToken(accountId, identityType, activeIdentityId, TENANT);
    }

    private String token(long accountId, String identityType, long activeIdentityId, Long tenantId) {
        return jwtTokenProvider.generateToken(accountId, identityType, activeIdentityId, tenantId);
    }

    private String login(String phone, String clientPortal) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", phone,
                                "smsCode", "123456",
                                "loginType", 1,
                                "clientPortal", clientPortal
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.identity_type", is("SYS_USER")))
                .andExpect(jsonPath("$.data.user_info.role_key", is("PROPERTY_STAFF")))
                .andExpect(jsonPath("$.data.user_info.permissions", hasItem("repair:workorder:read")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("access_token").asText();
    }

    private String loginSupplier(String phone) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", phone,
                                "smsCode", "123456",
                                "loginType", 1,
                                "clientPortal", "B"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_info.identity_type", is("SYS_USER")))
                .andExpect(jsonPath("$.data.user_info.role_key", is("SERVICE_PROVIDER_STAFF")))
                .andExpect(jsonPath("$.data.user_info.permissions", hasItem("repair:workorder:supplier")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("access_token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private long dataId(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        return root.path("data").path("workOrderId").asLong();
    }

    private JsonNode findEvent(String json, String action) throws Exception {
        JsonNode events = objectMapper.readTree(json).path("data");
        for (JsonNode event : events) {
            if (action.equals(event.path("action").asText())) {
                return event;
            }
        }
        throw new AssertionError("未找到事件 action=" + action);
    }

    private long readyAttachment(long workOrderId, String kind, String contentType, long size) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_attachment (
                    work_order_id, tenant_id, attachment_kind, object_key, original_file_name,
                    content_type, declared_size, actual_size, etag, status, uploaded_by_account_id,
                    confirmed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'READY', ?, CURRENT_TIMESTAMP)
                RETURNING attachment_id
                """, Long.class,
                workOrderId, TENANT, kind,
                "repair/test/" + workOrderId + "/" + kind + "/" + System.nanoTime(),
                "evidence", contentType, size, size, "etag-" + System.nanoTime(), ACC_PROPERTY_STAFF);
    }
}
