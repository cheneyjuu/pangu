// 关联业务：验证楼栋维修由物业与施工单位签约、施工取证、材料、结算、楼组长与受影响业主验收、付款和披露闭环。
package com.pangu.bootstrap.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import com.pangu.infrastructure.security.crypto.Sm4Util;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
class RepairProjectExecutionFlowTest {

    private static final long TENANT = 10001L;
    private static final long BUILDING_ID = 30002L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final long ACCOUNT_DIRECTOR = 999811L;
    private static final long USER_DIRECTOR = 800101L;
    private static final long ACCOUNT_BUILDING_LEADER = 999812L;
    private static final long USER_BUILDING_LEADER = 800102L;
    private static final long ACCOUNT_AFFECTED_OWNER = 999913L;
    private static final long UID_AFFECTED_OWNER = 70002L;
    private static final String PROJECT_PREFIX = "IT-工程执行维修-";
    private static final String SUPPLIER_PREFIX = "IT-工程执行供应商-";
    private static final String PROPERTY_ENTERPRISE_NAME = "IT-工程执行物业服务有限公司";
    private static final String PROPERTY_ENTERPRISE_USCC = "91310000A123456789";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Value("${platform.security.sm4-key-hex}") private String sm4KeyHex;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String propertyToken;
    private String directorToken;
    private String leaderToken;
    private String ownerToken;
    private Long leaderAssignmentId;

    @BeforeEach
    void setUp() throws Exception {
        propertyToken = token(ACCOUNT_PROPERTY_MANAGER, "SYS_USER", USER_PROPERTY_MANAGER);
        directorToken = token(ACCOUNT_DIRECTOR, "SYS_USER", USER_DIRECTOR);
        leaderToken = token(ACCOUNT_BUILDING_LEADER, "SYS_USER", USER_BUILDING_LEADER);
        ownerToken = token(ACCOUNT_AFFECTED_OWNER, "C_USER", UID_AFFECTED_OWNER);
        activatePropertyServiceOrganization();
        leaderAssignmentId = jdbcTemplate.queryForObject("""
                INSERT INTO sys_user_building(user_id, building_id, tenant_id, assigned_by, status)
                VALUES (?, ?, ?, ?, 1)
                RETURNING assignment_id
                """, Long.class, USER_BUILDING_LEADER, BUILDING_ID, TENANT, USER_DIRECTOR);
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2), "execution-etag"));
        when(objectStorage.createDownloadUrl(anyString(), any()))
                .thenReturn(URI.create("https://oss.example.test/repair-project-execution").toURL());
    }

    @AfterEach
    void clean() {
        // 工程子聚合同时引用项目附件或工程项，必须先按依赖顺序清理，再删除项目根。
        jdbcTemplate.update("""
                DELETE FROM t_repair_payment_evidence
                WHERE payment_request_id IN (
                    SELECT payment_request_id FROM t_repair_payment_request
                    WHERE project_id IN (
                        SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                    )
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_payment_request
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_acceptance_party
                WHERE acceptance_id IN (
                    SELECT acceptance_id FROM t_repair_acceptance
                    WHERE project_id IN (
                        SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                    )
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_acceptance
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_completion_disclosure_photo
                WHERE disclosure_id IN (
                    SELECT disclosure_id FROM t_repair_completion_disclosure
                    WHERE project_id IN (
                        SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                    )
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_completion_disclosure
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_execution_record
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_material_inspection
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_project_settlement_item
                WHERE settlement_id IN (
                    SELECT settlement_id FROM t_repair_project_settlement
                    WHERE project_id IN (
                        SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                    )
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_project_settlement
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_contract_signature
                WHERE contract_id IN (
                    SELECT contract_id FROM t_repair_contract
                    WHERE project_id IN (
                        SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                    )
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_contract
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_plan_attachment
                WHERE plan_id IN (
                    SELECT plan_id FROM t_repair_plan_version
                    WHERE project_id IN (
                        SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                    )
                )
                """, PROJECT_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE FROM t_repair_acceptance_policy_snapshot
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, PROJECT_PREFIX + "%");
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
        jdbcTemplate.update("DELETE FROM t_account WHERE phone LIKE '13988%'");
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
        deleteTestPropertyServiceOrganization();
        if (leaderAssignmentId != null) {
            jdbcTemplate.update("DELETE FROM sys_user_building WHERE assignment_id = ?", leaderAssignmentId);
        }
    }

    @Test
    void buildingExecutionRequiresVerifiedEvidenceOwnerAcceptanceAndLegalPaymentLimits() throws Exception {
        LockedProject locked = createAndAuthorizeProject();
        long supplierDeptId = locked.supplierDeptId();
        long contractFile = upload(locked.projectId(), "双方施工合同.pdf", "contract");
        long ownerSignature = upload(locked.projectId(), "业主方签署页.pdf", "owner-signature");
        long propertySignature = upload(locked.projectId(), "物业签署页.pdf", "property-signature");
        long supplierSignature = upload(locked.projectId(), "施工单位签署页.pdf", "supplier-signature");
        List<Map<String, Object>> signatures = List.of(
                signature("PROPERTY", "物业项目经理", null, propertySignature),
                signature("SUPPLIER", "施工单位负责人", null, supplierSignature));
        List<Map<String, Object>> invalidThreePartySignatures = List.of(
                signature("OWNERS_ASSEMBLY_OR_GROUP", "业委会主任", USER_DIRECTOR, ownerSignature),
                signature("PROPERTY", "物业项目经理", USER_PROPERTY_MANAGER, propertySignature),
                signature("SUPPLIER", "施工单位负责人", null, supplierSignature));
        List<Map<String, Object>> invalidElectronicSignatures = List.of(
                signature("PROPERTY", "物业项目经理", null, propertySignature, "ELECTRONIC"),
                signature("SUPPLIER", "施工单位负责人", null, supplierSignature));
        long nonSelectedSupplierDeptId = jdbcTemplate.queryForObject("""
                SELECT supplier_dept_id
                FROM t_repair_project_quote_invitation
                WHERE project_id = ? AND supplier_dept_id <> ?
                ORDER BY invitation_id
                LIMIT 1
                """, Long.class, locked.projectId(), supplierDeptId);
        postBadRequest(projectPath(locked.projectId(), "/contract"), propertyToken,
                Map.ofEntries(
                        Map.entry("expectedProjectVersion", projectVersion(locked.projectId())),
                        Map.entry("supplierDeptId", nonSelectedSupplierDeptId),
                        Map.entry("contractAmount", 1000),
                        Map.entry("contractAttachmentId", contractFile),
                        Map.entry("signatures", signatures)),
                "合同施工单位必须与锁定方案的中选供应商一致");
        postBadRequest(projectPath(locked.projectId(), "/contract"), propertyToken,
                Map.ofEntries(
                        Map.entry("expectedProjectVersion", projectVersion(locked.projectId())),
                        Map.entry("supplierDeptId", supplierDeptId),
                        Map.entry("contractAmount", 1000),
                        Map.entry("contractAttachmentId", contractFile),
                        Map.entry("signatures", invalidThreePartySignatures)),
                "合同签署方不符合当前维修流程");
        postBadRequest(projectPath(locked.projectId(), "/contract"), propertyToken,
                Map.ofEntries(
                        Map.entry("expectedProjectVersion", projectVersion(locked.projectId())),
                        Map.entry("supplierDeptId", supplierDeptId),
                        Map.entry("contractAmount", 1000),
                        Map.entry("contractAttachmentId", contractFile),
                        Map.entry("signatures", invalidElectronicSignatures)),
                "电子签署必须绑定系统工作身份 partyType=PROPERTY");
        postBadRequest(projectPath(locked.projectId(), "/contract"), propertyToken,
                Map.ofEntries(
                        Map.entry("expectedProjectVersion", projectVersion(locked.projectId())),
                        Map.entry("supplierDeptId", supplierDeptId),
                        Map.entry("contractAmount", 1001),
                        Map.entry("contractAttachmentId", contractFile),
                        Map.entry("signatures", signatures)),
                "合同金额超过中选报价、锁定方案预算或有效审价金额");

        JsonNode contract = data(postOk(projectPath(locked.projectId(), "/contract"), propertyToken,
                Map.ofEntries(
                        Map.entry("expectedProjectVersion", projectVersion(locked.projectId())),
                        Map.entry("supplierDeptId", supplierDeptId),
                        Map.entry("contractAmount", 1000),
                        Map.entry("contractAttachmentId", contractFile),
                        Map.entry("signatures", signatures))));
        assertEquals("BUILDING_MAINTENANCE_FUND", contract.path("fundSource").asText());
        assertEquals("OFFLINE", contract.path("signingMethod").asText());
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_contract_signature
                WHERE contract_id = ? AND signer_user_id IS NULL
                """, Integer.class, contract.path("contractId").asLong()));
        assertEquals(PROPERTY_ENTERPRISE_NAME, jdbcTemplate.queryForObject("""
                SELECT payload_json ->> 'propertyEnterpriseName'
                FROM t_repair_project_event
                WHERE project_id = ? AND action = 'PROJECT_CONTRACT_EFFECTIVE'
                ORDER BY event_id DESC
                LIMIT 1
                """, String.class, locked.projectId()));
        String supplierToken = createSupplierIdentity(supplierDeptId);
        JsonNode assignedProjects = data(getOk("/api/v1/supplier/repair-projects", supplierToken));
        assertEquals(locked.projectId(), assignedProjects.get(0).path("project").path("projectId").asLong());
        JsonNode supplierProject = data(getOk(
                "/api/v1/supplier/repair-projects/" + locked.projectId(), supplierToken));
        assertEquals(locked.itemId(), supplierProject.path("items").get(0).path("itemId").asLong());
        assertEquals("2号楼外墙渗水；按锁定工程项维修2号楼外墙",
                supplierProject.path("activePlan").path("planDescription").asText());
        assertEquals(supplierDeptId, supplierProject.path("contract").path("supplierDeptId").asLong());
        assertEquals(true, supplierProject.path("currentPlanAllocationRooms").isMissingNode());

        postBadRequest(projectPath(locked.projectId(), "/payment-requests"), propertyToken,
                Map.of("milestoneType", "ADVANCE", "requestedAmount", 301,
                        "evidence", List.of(evidence("SIGNED_CONTRACT", contractFile))),
                "累计付款申请超过当前可申请上限");
        data(postOk(projectPath(locked.projectId(), "/payment-requests"), propertyToken,
                Map.of("milestoneType", "ADVANCE", "requestedAmount", 300,
                        "evidence", List.of(evidence("SIGNED_CONTRACT", contractFile)))));

        postOk(projectPath(locked.projectId(), "/execution/start"), propertyToken,
                Map.of("expectedProjectVersion", projectVersion(locked.projectId())));
        Map<String, Long> stageFiles = new LinkedHashMap<>();
        for (String stage : List.of(
                "BEFORE_CONSTRUCTION", "MATERIAL_ENTRY", "DURING_CONSTRUCTION",
                "CONCEALED_WORK", "COMPLETION")) {
            long attachmentId = upload(locked.projectId(), stage + ".jpg", stage, supplierToken);
            stageFiles.put(stage, attachmentId);
            JsonNode record = data(postOk(projectPath(locked.projectId(), "/execution-records"),
                    supplierToken, Map.of(
                            "itemId", locked.itemId(), "stage", stage,
                            "description", stage + " 现场原始记录",
                            "occurredAt", LocalDateTime.now().minusMinutes(1),
                            "attachmentIds", List.of(attachmentId))));
            postOk(projectPath(locked.projectId(), "/execution-records/"
                            + record.path("recordId").asLong() + "/verification"),
                    propertyToken, Map.of("status", "VERIFIED", "opinion", "现场核验一致"));
        }

        long materialCertificate = upload(
                locked.projectId(), "材料合格证明.pdf", "certificate", supplierToken);
        long materialPhoto = upload(
                locked.projectId(), "材料进场照片.jpg", "material", supplierToken);
        Map<String, Object> incompleteMaterial = new LinkedHashMap<>();
        incompleteMaterial.put("itemId", locked.itemId());
        incompleteMaterial.put("materialName", "防水涂料");
        incompleteMaterial.put("model", "M-1");
        incompleteMaterial.put("specification", "20kg/桶");
        incompleteMaterial.put("quantity", 2);
        incompleteMaterial.put("unit", "桶");
        incompleteMaterial.put("manufacturer", "测试材料厂");
        incompleteMaterial.put("qualificationAttachmentId", materialCertificate);
        incompleteMaterial.put("photoAttachmentIds", List.of(materialPhoto));
        mockMvc.perform(post(projectPath(locked.projectId(), "/material-inspections"))
                        .header("Authorization", bearer(supplierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(incompleteMaterial)))
                .andExpect(status().isBadRequest());

        incompleteMaterial.put("brand", "测试品牌");
        JsonNode material = data(postOk(projectPath(locked.projectId(), "/material-inspections"),
                supplierToken, incompleteMaterial));
        postOk(projectPath(locked.projectId(), "/material-inspections/"
                        + material.path("inspectionId").asLong() + "/verification"),
                propertyToken, Map.of("status", "VERIFIED", "opinion", "品牌规格数量与证明一致"));

        long settlementFile = upload(
                locked.projectId(), "竣工结算单.pdf", "settlement", supplierToken);
        data(postOk(projectPath(locked.projectId(), "/settlement"), supplierToken,
                Map.of("settlementAttachmentId", settlementFile,
                        "items", List.of(Map.of(
                                "projectItemId", locked.itemId(),
                                "actualQuantity", 10,
                                "unit", "平方米",
                                "actualUnitPrice", 90,
                                "taxRate", 0,
                                "varianceReason", "现场复核后单价降低")))));
        postOk(projectPath(locked.projectId(), "/settlement/verification"), propertyToken,
                Map.of("expectedProjectVersion", projectVersion(locked.projectId()),
                        "approved", true, "opinion", "实际工程量与结算一致"));

        long acceptanceResult = upload(locked.projectId(), "楼栋维修验收定案.pdf", "acceptance");
        postBadRequest(projectPath(locked.projectId(), "/acceptance/finalization"), leaderToken,
                Map.of("expectedProjectVersion", projectVersion(locked.projectId()),
                        "resultAttachmentId", acceptanceResult),
                "楼组长尚未验收通过");
        postOk(projectPath(locked.projectId(), "/acceptance/building-leader"), leaderToken,
                Map.of("conclusion", "PASSED", "participantName", "2号楼楼组长", "opinion", "现场验收通过"));
        postBadRequest(projectPath(locked.projectId(), "/acceptance/finalization"), leaderToken,
                Map.of("expectedProjectVersion", projectVersion(locked.projectId()),
                        "resultAttachmentId", acceptanceResult),
                "受影响业主未达到方案锁定的最低有效验收人数");

        JsonNode ownerTasks = data(getOk(
                "/api/v1/me/repair-projects/acceptance-tasks", ownerToken));
        assertEquals(locked.projectId(), ownerTasks.get(0).path("projectId").asLong());
        JsonNode ownerTask = data(getOk(
                "/api/v1/me/repair-projects/" + locked.projectId() + "/acceptance", ownerToken));
        long affectedRoomId = ownerTask.path("affectedRoomIds").get(0).asLong();
        postOk("/api/v1/me/repair-projects/" + locked.projectId() + "/acceptance", ownerToken,
                Map.of("roomId", affectedRoomId, "conclusion", "PASSED",
                        "participantName", "受影响业主", "opinion", "维修后使用正常"));
        JsonNode accepted = data(postOk(projectPath(locked.projectId(), "/acceptance/finalization"),
                leaderToken, Map.of("expectedProjectVersion", projectVersion(locked.projectId()),
                        "resultAttachmentId", acceptanceResult, "remark", "楼栋验收条件全部满足")));
        assertEquals("PASSED", accepted.path("status").asText());

        JsonNode completionPayment = data(postOk(
                projectPath(locked.projectId(), "/payment-requests"), propertyToken,
                Map.of("milestoneType", "COMPLETION", "requestedAmount", 600,
                        "evidence", List.of(
                                evidence("ACCEPTANCE", acceptanceResult),
                                evidence("SETTLEMENT", settlementFile)))));
        assertEquals(900, completionPayment.path("cumulativeRequestedAmount").asInt());
        assertEquals("PENDING_FINANCE", completionPayment.path("status").asText());

        long notice = upload(locked.projectId(), "完工告示.pdf", "notice");
        long propertyReport = upload(locked.projectId(), "物业书面维修报告.pdf", "report");
        long completionPhoto = upload(locked.projectId(), "完工现场.jpg", "photo");
        JsonNode disclosure = data(postOk(projectPath(locked.projectId(), "/completion-disclosure"),
                propertyToken, Map.of(
                        "expectedProjectVersion", projectVersion(locked.projectId()),
                        "noticeStartDate", LocalDate.now(),
                        "noticeEndDate", LocalDate.now(),
                        "postingScope", "2号楼公告栏及单元入口",
                        "noticeAttachmentId", notice,
                        "propertyReportAttachmentId", propertyReport,
                        "sitePhotoAttachmentIds", List.of(completionPhoto),
                        "warrantyStartDate", LocalDate.now())));
        assertEquals(LocalDate.now().plusDays(30).toString(), disclosure.path("warrantyEndDate").asText());

        long warrantyCertificate = upload(locked.projectId(), "质保责任期满证明.pdf", "warranty");
        postBadRequest(projectPath(locked.projectId(), "/payment-requests"), propertyToken,
                Map.of("milestoneType", "WARRANTY_RELEASE", "requestedAmount", 100,
                        "evidence", List.of(evidence("WARRANTY_EXPIRED_CERTIFICATE", warrantyCertificate))),
                "当前项目状态或证明不满足付款节点条件");
        postBadRequest(projectPath(locked.projectId(), "/archive"), propertyToken,
                Map.of("expectedProjectVersion", projectVersion(locked.projectId())),
                "质保责任期尚未届满，不能归档");

        assertEquals("WARRANTY", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_project WHERE project_id = ?",
                String.class, locked.projectId()));
        assertEquals(5, count("SELECT COUNT(*) FROM t_repair_execution_record WHERE project_id = ?",
                locked.projectId()));
        assertEquals(1, count("SELECT COUNT(*) FROM t_repair_material_inspection WHERE project_id = ?",
                locked.projectId()));
        assertEquals(2, count("SELECT COUNT(*) FROM t_repair_payment_request WHERE project_id = ?",
                locked.projectId()));
    }

    @Test
    void processHistoryReturnsBusinessNodesWithoutOwnerAcceptanceDetails() throws Exception {
        LockedProject locked = createAndAuthorizeProject();
        jdbcTemplate.update("""
                INSERT INTO t_repair_project_event (
                    project_id, tenant_id, action, actor_account_id, actor_owner_uid, payload_json
                ) VALUES (?, ?, 'PROJECT_AFFECTED_OWNER_ACCEPTANCE', ?, ?, CAST(? AS JSONB))
                """, locked.projectId(), TENANT, ACCOUNT_AFFECTED_OWNER, UID_AFFECTED_OWNER,
                "{\"roomId\":20000001001,\"conclusion\":\"PASSED\"}");

        mockMvc.perform(get(projectPath(locked.projectId(), "/process-history"))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden());

        JsonNode history = data(getOk(projectPath(locked.projectId(), "/process-history"), propertyToken));

        assertTrue(history.isArray());
        assertTrue(history.size() >= 4);
        assertTrue(history.findValuesAsText("title").contains("创建维修工程项目"));
        assertFalse(history.toString().contains("PROJECT_AFFECTED_OWNER_ACCEPTANCE"));
        assertFalse(history.toString().contains("roomId"));
        assertFalse(history.toString().contains("payloadJson"));
        assertFalse(history.toString().contains("actorAccountId"));
        assertFalse(history.toString().contains("actorUserId"));
    }

    private LockedProject createAndAuthorizeProject() throws Exception {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planDescription", "2号楼外墙渗水；按锁定工程项维修2号楼外墙");
        plan.put("budgetTotal", 1000);
        plan.put("allocationRuleType", "BY_BUILDING_AREA");
        plan.put("allocationRuleDescription", "按2号楼锁定房屋建筑面积分摊");
        plan.put("supplierSelectionMethod", "COMPETITIVE_QUOTATION");
        plan.put("supplierSelectionReason", "物业询价比较后按锁定方案组织签约");
        plan.put("constructionManagementRequirements", "物业核验施工阶段和实际工程量");
        plan.put("evidenceRequirements", evidenceRequirements());
        plan.put("safetyRequirements", "设置围挡并落实高空作业安全措施");
        plan.put("acceptanceMethod", "楼组长与受影响业主按最低人数共同验收");
        plan.put("affectedOwnerScopeDescription", "2号楼维修资金承担范围业主");
        plan.put("minimumAffectedOwnerAcceptors", 1);
        plan.put("affectedOwnerPassRule", "ALL");
        plan.put("affectedOwnerApprovalRatio", 1);
        plan.put("settlementMethod", "ACTUAL_QUANTITY");
        plan.put("plannedStartDate", LocalDate.now().plusDays(1));
        plan.put("plannedCompletionDate", LocalDate.now().plusDays(10));
        plan.put("warrantyDays", 30);
        plan.put("priceReviewRequired", false);
        plan.put("paymentMilestones", paymentMilestones());
        plan.put("attachments", List.of());
        plan.put("items", List.of(Map.ofEntries(
                Map.entry("itemNo", "WATERPROOF-1"),
                Map.entry("buildingId", BUILDING_ID),
                Map.entry("locationText", "2号楼外墙"),
                Map.entry("workContent", "渗水点清理并实施防水"),
                Map.entry("quantity", 10),
                Map.entry("unit", "平方米"),
                Map.entry("estimatedUnitPrice", 100),
                Map.entry("estimatedAmount", 1000),
                Map.entry("linkedWorkOrderIds", List.of()))));
        JsonNode created = data(postOk("/api/v1/admin/repair-projects", propertyToken,
                Map.of("projectName", PROJECT_PREFIX + System.nanoTime(),
                        "scopeType", "BUILDING", "buildingId", BUILDING_ID,
                        "fundSource", "BUILDING_MAINTENANCE_FUND",
                        "governancePath", "BUILDING_REPAIR_DECISION", "plan", plan)));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();
        long itemId = created.path("currentPlanItems").get(0).path("itemId").asLong();
        RepairProjectSourcingTestSupport.SelectedSupplier selectedSupplier =
                RepairProjectSourcingTestSupport.completeCompetitiveSourcing(
                        mockMvc, objectMapper, propertyToken, SUPPLIER_PREFIX, projectId, 1000);
        long photo = upload(projectId, "现场照片.jpg", "photo");
        postOk(projectPath(projectId, "/plans/" + planId + "/attachments"), propertyToken,
                Map.of("attachmentId", photo, "purpose", "SITE_PHOTO"));
        postOk(projectPath(projectId, "/plans/" + planId + "/lock"), propertyToken,
                Map.of("expectedProjectVersion", 0));
        jdbcTemplate.update("UPDATE t_repair_project SET status = 'AUTHORIZED' WHERE project_id = ?", projectId);
        return new LockedProject(projectId, itemId, selectedSupplier.supplierDeptId());
    }

    /**
     * 工程执行只验证合同环节，不重复覆盖物业服务组织登记流程；这里准备一个已启用组织，
     * 确保物业方的法定主体来自当前小区已核验企业而不是前端输入。
     */
    private void activatePropertyServiceOrganization() {
        deleteTestPropertyServiceOrganization();
        long enterpriseId = jdbcTemplate.queryForObject("""
                INSERT INTO t_property_service_enterprise (legal_name, unified_social_credit_code)
                VALUES (?, ?)
                RETURNING enterprise_id
                """, Long.class, PROPERTY_ENTERPRISE_NAME, PROPERTY_ENTERPRISE_USCC);
        jdbcTemplate.update("""
                INSERT INTO t_property_service_organization (
                    tenant_id, enterprise_id, project_dept_id, project_dept_name,
                    service_contact_name, service_contact_phone, service_basis,
                    service_start_date, status, verified_by_account_id, verified_by_user_id, verified_at
                ) VALUES (?, ?, 102, '求是物业项目部', ?, ?,
                    'PRELIMINARY_PROPERTY_SERVICE', CURRENT_DATE, 'ACTIVE', ?, ?, CURRENT_TIMESTAMP)
                """, TENANT, enterpriseId,
                Sm4Util.encryptHex("赵经理", sm4KeyHex),
                Sm4Util.encryptHex("13800000021", sm4KeyHex),
                ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
    }

    private void deleteTestPropertyServiceOrganization() {
        jdbcTemplate.update("""
                DELETE FROM t_property_service_organization
                WHERE enterprise_id IN (
                    SELECT enterprise_id
                    FROM t_property_service_enterprise
                    WHERE unified_social_credit_code = ?
                )
                """, PROPERTY_ENTERPRISE_USCC);
        jdbcTemplate.update("""
                DELETE FROM t_property_service_enterprise
                WHERE unified_social_credit_code = ?
                """, PROPERTY_ENTERPRISE_USCC);
    }

    private String createSupplierIdentity(long supplierDeptId) {
        String phone = "13988" + String.format("%06d", supplierDeptId % 1_000_000);
        long accountId = jdbcTemplate.queryForObject("""
                INSERT INTO t_account (
                    phone, real_name, real_name_verified, status,
                    last_active_identity_type
                ) VALUES (?, '测试施工单位经办人', 1, 1, 'SYS_USER')
                RETURNING account_id
                """, Long.class, phone);
        long userId = jdbcTemplate.queryForObject("""
                INSERT INTO sys_user (account_id, dept_id, user_name, nick_name, status)
                VALUES (?, ?, ?, '测试施工单位经办人', '0')
                RETURNING user_id
                """, Long.class, accountId, supplierDeptId, "supplier-project-" + supplierDeptId);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id, effective_data_scope, granted_by)
                SELECT ?, role_id, 'ORG_ONLY', ?
                FROM sys_role WHERE role_key = 'SERVICE_PROVIDER_STAFF'
                """, userId, USER_PROPERTY_MANAGER);
        return token(accountId, "SYS_USER", userId);
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

    private Map<String, Object> signature(
            String partyType, String signerName, Long signerUserId, long attachmentId) {
        return signature(partyType, signerName, signerUserId, attachmentId, "PAPER_SCAN");
    }

    private Map<String, Object> signature(
            String partyType, String signerName, Long signerUserId, long attachmentId,
            String signatureMethod) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("partyType", partyType);
        result.put("signerName", signerName);
        if (signerUserId != null) {
            result.put("signerUserId", signerUserId);
        }
        result.put("signatureMethod", signatureMethod);
        result.put("signatureAttachmentId", attachmentId);
        result.put("signedAt", LocalDateTime.now().minusMinutes(1));
        return result;
    }

    private Map<String, Object> evidence(String code, long attachmentId) {
        return Map.of("evidenceCode", code, "attachmentId", attachmentId);
    }

    private long upload(long projectId, String fileName, String content) throws Exception {
        return upload(projectId, fileName, content, propertyToken);
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

    private String postOk(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void postBadRequest(
            String path, String token, Object body, String expectedMessage) throws Exception {
        mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is(expectedMessage)));
    }

    private String getOk(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode data(String response) throws Exception {
        return objectMapper.readTree(response).path("data");
    }

    private int projectVersion(long projectId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM t_repair_project WHERE project_id = ?", Integer.class, projectId);
    }

    private String token(long accountId, String identityType, long identityId) {
        return jwtTokenProvider.generateToken(accountId, identityType, identityId, TENANT);
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

    private record LockedProject(long projectId, long itemId, long supplierDeptId) {
    }
}
