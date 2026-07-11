package com.pangu.bootstrap.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.pangu.domain.repository.RepairEvidenceObjectStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;
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
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    @BeforeEach
    public void configureObjectStorage() {
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2), "etag-test"));
    }

    @AfterEach
    public void clean() {
        jdbcTemplate.update("DELETE FROM t_repair_work_order WHERE title LIKE 'IT-报修-%'");
        jdbcTemplate.update("DELETE FROM t_supplier_activation_invitation WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%')");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT user_id FROM sys_user WHERE dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%'))");
        jdbcTemplate.update("DELETE FROM sys_user WHERE dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%')");
        jdbcTemplate.update("DELETE FROM t_supplier_tenant_relation WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE 'IT-供应商-%')");
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
    public void ownerPrivateRepair_propertyAndCommittee_completeAndEvaluate() throws Exception {
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
        action(staffToken, id, "submit-plan", Map.of(
                "planBudget", new BigDecimal("600.00"),
                "fundSource", "PROPERTY_INTERNAL",
                "remark", "小修小补"))
                .andExpect(jsonPath("$.data.status", is("PLAN_SUBMITTED")));
        action(managerToken, id, "route-plan", Map.of("remark", "包干内维修"))
                .andExpect(jsonPath("$.data.status", is("APPROVED")));
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

        mockMvc.perform(post("/api/v1/admin/repair-work-orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", title,
                                "description", "物业客服接报公共区域报修",
                                "locationText", "地下车库入口",
                                "category", "PUBLIC_FACILITY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source", is("ADMIN_PC")))
                .andExpect(jsonPath("$.data.status", is("NEED_MANUAL_LOCATION")))
                .andExpect(jsonPath("$.data.spaceScope", is("PUBLIC")));
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
    public void publicBuildingRepair_fullDeliveryContractAndOwnerAcceptance() throws Exception {
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
        action(staffToken, id, "submit-plan", Map.of(
                "planBudget", new BigDecimal("9000.00"),
                "fundSource", "MAINTENANCE_FUND",
                "remark", "维修范围及估算已确认"))
                .andExpect(jsonPath("$.data.status", is("PLAN_SUBMITTED")));
        long supplierA = registerSupplier(managerToken, "IT-供应商-甲-" + System.nanoTime(), "91310000A000000001");
        long supplierB = registerSupplier(managerToken, "IT-供应商-乙-" + System.nanoTime(), "91310000A000000002");
        long supplierC = registerSupplier(managerToken, "IT-供应商-丙-" + System.nanoTime(), "91310000A000000003");
        jdbcTemplate.update("UPDATE t_supplier_org_profile SET verification_status = 'VERIFIED' WHERE supplier_dept_id IN (?, ?, ?)",
                supplierA, supplierB, supplierC);
        jdbcTemplate.update("""
                UPDATE t_supplier_tenant_relation
                SET relation_type = 'FRAMEWORK', service_category = 'PUBLIC_FACILITY',
                    status = 'ACTIVE', valid_from = CURRENT_DATE, valid_until = CURRENT_DATE + 365
                WHERE tenant_id = ? AND supplier_dept_id = ?
                """, TENANT, supplierA);
        mockMvc.perform(get("/api/v1/admin/supplier-organizations")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].supplierDeptId", hasItem((int) supplierA)));
        mockMvc.perform(get("/api/v1/admin/supplier-framework-relations")
                        .param("serviceCategory", "PUBLIC_FACILITY")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].supplierDeptId", is((int) supplierA)));
        action(managerToken, id, "quote-invitations", Map.of(
                "supplierDeptIds", List.of(supplierA, supplierB, supplierC),
                "deadline", LocalDateTime.now().plusDays(3).toString(),
                "remark", "向三家供应商发出邀价"))
                .andExpect(jsonPath("$.data.status", is("QUOTE_COLLECTING")));

        Long supplierInvitationId = jdbcTemplate.queryForObject("""
                SELECT invitation_id
                FROM t_supplier_activation_invitation
                WHERE supplier_dept_id = ? AND contact_phone = '13800000931' AND status = 'PENDING'
                """, Long.class, supplierA);
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
                .andExpect(jsonPath("$.data[*].workOrderId", hasItem((int) id)));
        mockMvc.perform(post("/api/v1/supplier/repair-work-orders/" + id + "/quote")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "quoteAmount", new BigDecimal("8000.00"),
                                "quoteSummary", "供应商在线结构化报价",
                                "attachmentHash", "supplier-online-quote-hash",
                                "remark", "供应商本人提交"))))
                .andExpect(status().isOk());
        assertEquals("SUPPLIER_ONLINE", jdbcTemplate.queryForObject("""
                SELECT submission_source FROM t_repair_supplier_quote
                WHERE work_order_id = ? AND supplier_dept_id = ?
                """, String.class, id, supplierA));

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
        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/supplier-quotes")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(3)))
                .andExpect(jsonPath("$.data[0].supplierDeptId", is((int) supplierA)));

        Long quoteId = jdbcTemplate.queryForObject("""
                SELECT quote_id FROM t_repair_supplier_quote
                WHERE work_order_id = ? AND supplier_dept_id = ?
                """, Long.class, id, supplierA);
        action(managerToken, id, "recommend-supplier", Map.of(
                "quoteId", quoteId,
                "selectionMethod", "COMPETITIVE_QUOTATION",
                "recommendationReason", "三家比价后，甲公司报价最低且材料齐全",
                "remark", "物业推荐甲公司"))
                .andExpect(jsonPath("$.data.status", is("SUPPLIER_RECOMMENDED")));

        action(ownerRepToken, id, "start-local-decision", Map.of(
                "scopeType", "BUILDING",
                "scopeLabel", "1号楼",
                "remark", "楼主发起微信接龙"))
                .andExpect(jsonPath("$.data.status", is("LOCAL_DECISION_PENDING")));

        String decisionRoomsResponse = mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/local-decision-rooms")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> entries = new java.util.ArrayList<>();
        objectMapper.readTree(decisionRoomsResponse).path("data").forEach(room -> entries.add(Map.of(
                "roomId", room.path("roomId").asLong(),
                "choice", "AGREE",
                "originalText", "微信接龙同意")));
        action(staffToken, id, "complete-local-decision", Map.of(
                "entries", entries,
                "evidenceAttachmentHash", "wechat-solitaire-print-hash",
                "remark", "物业完成接龙核验"))
                .andExpect(jsonPath("$.data.status", is("LOCAL_DECISION_PASSED")));

        action(managerToken, id, "approval-package", Map.of(
                "officialDocumentHash", "property-official-report-hash",
                "mergedPackageHash", "immutable-merged-package-hash",
                "printedAndAttached", true,
                "attachments", List.of(
                        Map.of("attachmentType", "SOLITAIRE_SCREENSHOT", "attachmentHash", "wechat-solitaire-print-hash", "originalFileName", "接龙截图.pdf", "sortOrder", 1),
                        Map.of("attachmentType", "QUOTE", "attachmentHash", "supplier-a-quote-hash", "originalFileName", "报价单.pdf", "sortOrder", 2)),
                "remark", "物业上传正式报审文件并生成不可变合并包"))
                .andExpect(jsonPath("$.data.status", is("PRICE_REVIEW_PENDING")));

        action(directorToken, id, "price-review", Map.of(
                "reviewMode", "INTERNAL_PRICE_REVIEW",
                "reviewedAmount", new BigDecimal("8000.00"),
                "conclusion", "APPROVED",
                "opinion", "价格与维修范围匹配"))
                .andExpect(jsonPath("$.data.status", is("GOVERNANCE_PENDING")));

        action(directorToken, id, "governance-confirm", Map.of("remark", "主任确认同意报批"))
                .andExpect(jsonPath("$.data.status", is("GOVERNANCE_CONFIRMED")));
        action(directorToken, id, "seal", Map.of(
                "sealType", "COMMITTEE_SEAL",
                "sealedFileHash", "committee-sealed-approval-hash",
                "remark", "业委会盖章"))
                .andExpect(jsonPath("$.data.status", is("SEALED")));

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
        return action(token, id, "supplier-quotes", Map.of(
                "supplierDeptId", supplierDeptId,
                "quoteAmount", new BigDecimal(amount),
                "quoteSummary", "维修报价",
                "attachmentHash", "supplier-" + supplierDeptId + "-quote-hash",
                "originalSource", "PAPER",
                "originalAttachmentHash", "supplier-" + supplierDeptId + "-original-hash",
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
