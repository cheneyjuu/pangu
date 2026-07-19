// 关联业务：验证报修事项的登记、定位、踏勘、附件与管理端可见性；公共工程动作由维修项目承接。
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
import java.util.List;
import java.util.Map;

import com.pangu.domain.repository.RepairDocumentPreviewConverter;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                    building_repair_default_decision_channel = 'ONLINE'
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
        jdbcTemplate.update("DELETE FROM t_auth_refresh_session WHERE account_id IN (SELECT account_id FROM t_account WHERE phone IN ('13800000931', '13800000932'))");
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

        mockMvc.perform(post("/api/v1/admin/supplier-organizations")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("legalName", "IT-员工不得登记供应商-" + System.nanoTime()))))
                .andExpect(status().isForbidden());

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
    public void ownerUploadsAndBindsReportPhotosAfterCreatingRepair() throws Exception {
        String ownerToken = token(ACC_OWNER, "C_USER", UID_OWNER);
        long opid = jdbcTemplate.queryForObject("""
                SELECT opid FROM c_owner_property
                WHERE uid = ? AND tenant_id = ?
                ORDER BY opid LIMIT 1
                """, Long.class, UID_OWNER, TENANT);
        long id = createPrivate(ownerToken, opid, "IT-报修-业主现场照片-" + System.nanoTime());

        assertNull(jdbcTemplate.queryForObject(
                "SELECT survey_summary FROM t_repair_work_order WHERE work_order_id = ?",
                String.class,
                id));

        MockMultipartFile file = new MockMultipartFile(
                "file", "业主现场.jpg", "image/jpeg", new byte[1024]);
        String uploadResponse = mockMvc.perform(multipart("/api/v1/me/repairs/" + id + "/attachments")
                        .file(file)
                        .param("contentType", "image/jpeg")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentKind", is("OWNER_REPORT_IMAGE")))
                .andExpect(jsonPath("$.data.status", is("BOUND")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long attachmentId = objectMapper.readTree(uploadResponse).path("data").path("attachmentId").asLong();

        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_attachment
                WHERE attachment_id = ?
                  AND attachment_kind = 'OWNER_REPORT_IMAGE'
                  AND status = 'BOUND'
                  AND bound_action = 'OWNER_SUBMIT_EVIDENCE'
                """, Integer.class, attachmentId));
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

        String staffToken = login("13800000022", "SYS_USER");

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
