// 关联业务：验证单一决定范围草稿、责任初判、相关业主决定提案与实施方案锁定的真实前后关系。
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepairProjectFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final long ACCOUNT_COMMITTEE_DIRECTOR = 999811L;
    private static final long USER_COMMITTEE_DIRECTOR = 800101L;
    private static final String PROJECT_PREFIX = "IT-维修点位-";
    private static final String CASE_PREFIX = "IT-维修点位来源-";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String propertyToken;
    private String committeeDirectorToken;
    private long buildingId;
    private long roomId;
    private long otherBuildingId;

    @BeforeEach
    void setUp() {
        propertyToken = jwtTokenProvider.generateToken(
                ACCOUNT_PROPERTY_MANAGER, "SYS_USER", USER_PROPERTY_MANAGER, TENANT);
        committeeDirectorToken = jwtTokenProvider.generateToken(
                ACCOUNT_COMMITTEE_DIRECTOR, "SYS_USER", USER_COMMITTEE_DIRECTOR, TENANT);
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2), "repair-project-flow-etag"));
        buildingId = jdbcTemplate.queryForObject("""
                SELECT building_id
                FROM c_owner_property
                WHERE tenant_id = ? AND account_status = 1
                GROUP BY building_id
                ORDER BY COUNT(DISTINCT room_id) DESC, building_id
                LIMIT 1
                """, Long.class, TENANT);
        roomId = jdbcTemplate.queryForObject("""
                SELECT room_id
                FROM c_owner_property
                WHERE tenant_id = ? AND building_id = ? AND account_status = 1
                ORDER BY room_id
                LIMIT 1
                """, Long.class, TENANT, buildingId);
        otherBuildingId = jdbcTemplate.queryForObject("""
                SELECT building_id
                FROM c_owner_property
                WHERE tenant_id = ? AND account_status = 1 AND building_id <> ?
                GROUP BY building_id
                ORDER BY building_id
                LIMIT 1
                """, Long.class, TENANT, buildingId);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM t_repair_project WHERE project_name LIKE ?", PROJECT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_repair_work_order WHERE title LIKE ?", CASE_PREFIX + "%");
    }

    @Test
    void createsConfirmedBuildingDraftWithReferenceRoomWorkPointWithoutFundingOrAllocation() throws Exception {
        long sourceId = createConfirmedBuildingSource(buildingId);

        JsonNode created = createProject(buildingRequest(List.of(
                referenceRoomWorkPoint(buildingId, roomId, List.of(sourceId)))));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();

        assertEquals("BUILDING_REPAIR", created.path("project").path("workflowType").asText());
        assertTrue(created.path("project").path("fundSource").isNull());
        assertTrue(created.path("project").path("governancePath").isNull());
        assertEquals("CONFIRMED", created.path("decisionScope").path("verificationStatus").asText());
        assertTrue(created.path("fundingSlices").isEmpty());
        assertTrue(created.path("currentPlanAffectedOwners").isEmpty());
        assertEquals(1, created.path("currentPlanWorkPoints").size());
        JsonNode workPoint = created.path("currentPlanWorkPoints").get(0);
        assertEquals("REFERENCE_ROOM", workPoint.path("locationType").asText());
        assertEquals(roomId, workPoint.path("referenceRoomId").asLong());
        assertEquals("PENDING_INVESTIGATION", workPoint.path("causeStatus").asText());
        assertFalse(workPoint.path("businessName").asText().startsWith("ITEM-"));

        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_project_item WHERE project_id = ?
                """, Integer.class, projectId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_work_point WHERE project_id = ? AND plan_id = ?
                """, Integer.class, projectId, planId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_repair_work_point_source source
                JOIN t_repair_work_point point ON point.work_point_id = source.work_point_id
                WHERE point.project_id = ? AND source.work_order_id = ?
                """, Integer.class, projectId, sourceId));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_plan_allocation_room WHERE plan_id = ?
                """, Integer.class, planId));
    }

    @Test
    void supportsMultipleWorkPointsInOneScopeAndPublicAreaWithoutFakeRoom() throws Exception {
        JsonNode created = createProject(buildingRequest(List.of(
                referenceRoomWorkPoint(buildingId, roomId, List.of()),
                commonAreaWorkPoint(buildingId, "楼栋大厅玻璃", "大厅东侧双层中空玻璃"))));

        JsonNode workPoints = created.path("currentPlanWorkPoints");
        assertEquals(2, workPoints.size());
        assertEquals("REFERENCE_ROOM", workPoints.get(0).path("locationType").asText());
        assertEquals("COMMON_AREA", workPoints.get(1).path("locationType").asText());
        assertTrue(workPoints.get(1).path("referenceRoomId").isNull());
        assertEquals("大厅东侧双层中空玻璃", workPoints.get(1).path("commonAreaName").asText());
        assertEquals("PENDING_VERIFICATION",
                created.path("decisionScope").path("verificationStatus").asText());
    }

    @Test
    void rejectsSourceFromAnotherDecisionScopeInsteadOfMergingIt() throws Exception {
        long sameScopeSource = createConfirmedBuildingSource(buildingId);
        long otherScopeSource = createConfirmedBuildingSource(otherBuildingId);
        List<Map<String, Object>> workPoints = List.of(
                referenceRoomWorkPoint(buildingId, roomId, List.of(sameScopeSource)),
                commonAreaWorkPoint(buildingId, "本楼栋屋面", "屋面排水沟")
        );
        workPoints.get(1).put("linkedWorkOrderIds", List.of(otherScopeSource));

        mockMvc.perform(post("/api/v1/admin/repair-projects")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(buildingRequest(workPoints))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is(
                        "来源报修事项不属于当前决定范围（报修事项编号：" + otherScopeSource + "）")));
    }

    @Test
    void pendingScopeCanBeReverifiedOnlyAsDraftAndCannotBeLocked() throws Exception {
        JsonNode created = createProject(buildingRequest(List.of(
                commonAreaWorkPoint(buildingId, "楼栋大厅玻璃", "大厅东侧双层中空玻璃"))));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();

        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/decision-scope/reverify")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decisionScope.verificationStatus", is("PENDING_VERIFICATION")));

        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/lock")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg", is("维修范围尚待核对，不能确认实施方案")));
    }

    @Test
    void confirmedScopeWithoutResponsibilityDeterminationCannotLockPlan() throws Exception {
        long sourceId = createConfirmedBuildingSource(buildingId);
        JsonNode created = createProject(buildingRequest(List.of(
                referenceRoomWorkPoint(buildingId, roomId, List.of(sourceId)))));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();

        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/lock")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("expectedProjectVersion", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg", is(
                        "责任与费用初步意见尚待业委会确认，不能确认实施方案")));
    }

    @Test
    void sharedRepairDerivesOwnerDecisionAndFreezesAuthorizationProposalBeforeAnyFinalPlanLockOrLedgerVerification() throws Exception {
        long sourceId = createConfirmedBuildingSource(buildingId);
        JsonNode created = createProject(buildingRequest(List.of(
                referenceRoomWorkPoint(buildingId, roomId, List.of(sourceId)))));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();
        int freezeVersion = confirmSharedSpecialFundResponsibility(
                projectId, 0, "EXISTING_AUTHORIZATION", true);
        long acceptanceBasisAttachmentId = uploadProjectAttachment(
                projectId, propertyToken, "工程验收约定.pdf", "物业与受影响业主现场验收约定");

        String frozenResponse = mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId
                        + "/plans/" + planId + "/freeze-for-authorization")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(authorizationFreezeRequest(
                                freezeVersion, acceptanceBasisAttachmentId, 3, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.status", is("AUTHORIZATION_IN_PROGRESS")))
                .andExpect(jsonPath("$.data.plans[0].status", is("AUTHORIZATION_FROZEN")))
                .andExpect(jsonPath("$.data.plans[0].authorizationSnapshotHash").isString())
                .andExpect(jsonPath("$.data.plans[0].snapshotHash").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode frozen = objectMapper.readTree(frozenResponse).path("data");
        int lockVersion = frozen.path("project").path("version").asInt();
        assertTrue(frozen.path("currentPlanAffectedOwners").isEmpty());
        assertTrue(frozen.path("fundingSlices").isEmpty());
        assertTrue(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) > 0 FROM t_repair_plan_allocation_room WHERE plan_id = ?
                """, Boolean.class, planId));

        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/lock")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", lockVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg", is("请先提交实施方案并完成相关业主表决，再确认最终实施方案")));
    }

    @Test
    void propertyContractResponsibilityLocksWithoutPretendingThereIsAnOwnerSideSupplierSelection() throws Exception {
        long sourceId = createConfirmedBuildingSource(buildingId);
        JsonNode created = createProject(buildingRequest(List.of(
                referenceRoomWorkPoint(buildingId, roomId, List.of(sourceId)))));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();

        long basisAttachmentId = uploadProjectAttachment(
                projectId, propertyToken, "物业服务合同维修责任条款.pdf", "物业服务合同约定的日常维修责任");
        String proposedResponse = mockMvc.perform(post(
                        "/api/v1/admin/repair-projects/" + projectId + "/responsibility-determinations")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedProjectVersion", 0,
                                "responsibilityPath", "PROPERTY_SERVICE_CONTRACT",
                                "fundingSourceType", "PROPERTY_SERVICE_CONTRACT",
                                "executionAuthorityType", "OWNER_DECISION",
                                "basisAttachmentId", basisAttachmentId,
                                "basisReference", "物业服务合同第六条约定本类日常维修由物业承担。",
                                "responsiblePartyName", "江湾国际公寓物业服务中心",
                                "responsiblePartyReference", "物业服务合同第六条"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responsibilityDetermination.status", is("PENDING_CONFIRMATION")))
                .andExpect(jsonPath("$.data.responsibilityDetermination.executionAuthorityType",
                        is("CONTRACTUAL_EXECUTION")))
                .andExpect(jsonPath("$.data.responsibilityDetermination.approvedAmount").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode proposed = objectMapper.readTree(proposedResponse).path("data");
        long determinationId = proposed.path("responsibilityDetermination").path("determinationId").asLong();
        int confirmationVersion = proposed.path("project").path("version").asInt();

        String confirmedResponse = mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId
                        + "/responsibility-determinations/" + determinationId + "/confirm")
                        .header("Authorization", bearer(committeeDirectorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedProjectVersion", confirmationVersion,
                                "confirmationNote", "已核验物业服务合同责任范围。"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responsibilityDetermination.status", is("CONFIRMED")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int lockVersion = objectMapper.readTree(confirmedResponse).path("data")
                .path("project").path("version").asInt();

        String lockedResponse = mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/lock")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", lockVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.status", is("AUTHORIZED")))
                .andExpect(jsonPath("$.data.fundingSlices[0].sourceType", is("PROPERTY_SERVICE_CONTRACT")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int contractVersion = objectMapper.readTree(lockedResponse).path("data").path("project").path("version").asInt();

        mockMvc.perform(get("/api/v1/admin/repair-projects/" + projectId + "/sourcing")
                        .header("Authorization", bearer(propertyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectionAuthorization.status", is("UNSUPPORTED_WORKFLOW")))
                .andExpect(jsonPath("$.data.selectionAuthorization.blockingReason", is(
                        "本工程已确认由物业服务合同责任方承担，应按相应合同、保修或责任材料办理，无需由业主另行确定施工单位")));

        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/contract")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedProjectVersion", contractVersion,
                                "supplierDeptId", 1,
                                "supplierName", "不应进入的业主侧施工单位",
                                "contractAmount", 1,
                                "contractAttachmentId", basisAttachmentId,
                                "signatures", List.of(
                                        Map.of(
                                                "partyType", "PROPERTY",
                                                "signerName", "物业经办人",
                                                "signatureMethod", "PAPER_SCAN",
                                                "signatureAttachmentId", basisAttachmentId,
                                                "signedAt", "2026-07-19T12:00:00"),
                                        Map.of(
                                                "partyType", "SUPPLIER",
                                                "signerName", "不应进入的施工单位",
                                                "signatureMethod", "PAPER_SCAN",
                                                "signatureAttachmentId", basisAttachmentId,
                                                "signedAt", "2026-07-19T12:00:00"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("当前工程已确认由直接责任方履行，不适用业主侧施工合同归档")));
    }

    @Test
    void rejectsBlankRichTextAfterACompleteWorkPointIsProvided() throws Exception {
        Map<String, Object> request = buildingRequest(List.of(
                commonAreaWorkPoint(buildingId, "楼栋屋面", "南侧落水口")));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) request.get("plan");
        plan.put("planDescription", "<p>   </p>");

        mockMvc.perform(post("/api/v1/admin/repair-projects")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("问题与维修方案 必填")));
    }

    private JsonNode createProject(Map<String, Object> request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/repair-projects")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data");
    }

    /**
     * 旧客户端携带的执行依据和初判金额必须被忽略：共有维修只能由服务端派生为待取得相关业主决定，
     * 决定提案预算只会在冻结实施方案时写入快照。
     */
    private int confirmSharedSpecialFundResponsibility(
            long projectId, int expectedProjectVersion, String legacyExecutionAuthorityType,
            boolean assertPropertyCannotConfirm) throws Exception {
        long basisAttachmentId = uploadProjectAttachment(
                projectId, propertyToken, "专项维修资金责任初判依据.pdf", legacyExecutionAuthorityType);
        String proposedResponse = mockMvc.perform(post(
                        "/api/v1/admin/repair-projects/" + projectId + "/responsibility-determinations")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedProjectVersion", expectedProjectVersion,
                                "responsibilityPath", "SHARED_COMMON_REPAIR",
                                "fundingSourceType", "SPECIAL_MAINTENANCE_LEDGER",
                                "executionAuthorityType", legacyExecutionAuthorityType,
                                "basisAttachmentId", basisAttachmentId,
                                "basisReference", "已完成共有部位与专项维修资金使用条件的初步勘验，尚需取得相关业主决定。",
                                "approvedAmount", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responsibilityDetermination.status", is("PENDING_CONFIRMATION")))
                .andExpect(jsonPath("$.data.responsibilityDetermination.executionAuthorityType",
                        is("OWNER_DECISION")))
                .andExpect(jsonPath("$.data.responsibilityDetermination.approvedAmount").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode proposed = objectMapper.readTree(proposedResponse).path("data");
        long determinationId = proposed.path("responsibilityDetermination").path("determinationId").asLong();
        int confirmationVersion = proposed.path("project").path("version").asInt();

        if (assertPropertyCannotConfirm) {
            mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId
                            + "/responsibility-determinations/" + determinationId + "/confirm")
                            .header("Authorization", bearer(propertyToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "expectedProjectVersion", confirmationVersion,
                                    "confirmationNote", "物业无权自行确认。"))))
                    .andExpect(status().isForbidden());
        }

        String confirmedResponse = mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId
                        + "/responsibility-determinations/" + determinationId + "/confirm")
                        .header("Authorization", bearer(committeeDirectorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedProjectVersion", confirmationVersion,
                                "confirmationNote", "已核对共有维修责任、资金路径和本次预算上限；尚需取得相关业主决定。"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responsibilityDetermination.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.responsibilityDetermination.confirmedByUserId",
                        is((int) USER_COMMITTEE_DIRECTOR)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(confirmedResponse).path("data").path("project").path("version").asInt();
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

    private Map<String, Object> authorizationFreezeRequest(
            int expectedVersion, long acceptanceBasisAttachmentId,
            int minimumInvitedSupplierCount, int minimumValidQuoteCount) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("expectedProjectVersion", expectedVersion);
        request.put("supplierSelectionMethod", "COMPETITIVE_QUOTATION");
        request.put("supplierEvaluationRule", "LOWEST_COMPLIANT_QUOTE");
        request.put("minimumInvitedSupplierCount", minimumInvitedSupplierCount);
        request.put("minimumValidQuoteCount", minimumValidQuoteCount);
        request.put("constructionManagementRequirements", "物业按实施方案核验施工阶段和现场留存材料");
        request.put("evidenceRequirements", List.of(
                Map.of("stage", "BEFORE_CONSTRUCTION", "description", "施工前现场记录", "required", true),
                Map.of("stage", "DURING_CONSTRUCTION", "description", "施工过程记录", "required", true),
                Map.of("stage", "COMPLETION", "description", "完工现场记录", "required", true)));
        request.put("safetyRequirements", "施工期间设置现场防护并及时清场");
        request.put("settlementMethod", "ACTUAL_QUANTITY");
        request.put("plannedStartDate", LocalDate.now().plusDays(1));
        request.put("plannedCompletionDate", LocalDate.now().plusDays(10));
        request.put("warrantyDays", 365);
        request.put("acceptanceMethod", "物业项目负责人和受影响业主按竣工资料现场验收");
        request.put("acceptanceRequirements", List.of(
                Map.of("requirementCode", "PROPERTY", "businessName", "物业现场验收",
                        "eligibleRoles", List.of("PROPERTY_TECHNICAL_COSIGNER"),
                        "minimumPassingCount", 1, "evidenceRequired", true),
                Map.of("requirementCode", "AFFECTED_OWNER", "businessName", "受影响业主验收",
                        "eligibleRoles", List.of("AFFECTED_OWNER"),
                        "minimumPassingCount", 1, "evidenceRequired", false)));
        request.put("acceptanceFinalizerRoles", List.of("PROPERTY_TECHNICAL_COSIGNER"));
        request.put("acceptanceBasisAttachmentIds", List.of(acceptanceBasisAttachmentId));
        request.put("acceptanceBasisSummary", "依据工程验收约定，由物业和费用承担房屋业主共同验收");
        request.put("affectedOwnerScopeDescription", "本实施方案费用承担房屋的已核验业主");
        request.put("minimumAffectedOwnerAcceptors", 1);
        request.put("affectedOwnerPassRule", "ALL");
        return request;
    }

    private long createConfirmedBuildingSource(long sourceBuildingId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/repair-work-orders")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "publicAreaScope", "BUILDING",
                                "buildingId", sourceBuildingId,
                                "locationText", "楼栋外墙窗框交界",
                                "title", CASE_PREFIX + System.nanoTime(),
                                "description", "现场发现渗水现象，待形成维修点位",
                                "category", "WATERPROOFING"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long sourceId = objectMapper.readTree(response).path("data").path("workOrderId").asLong();
        jdbcTemplate.update("""
                UPDATE t_repair_work_order
                SET status = 'SURVEY_COMPLETED',
                    location_locked = 1,
                    need_manual_location = 0,
                    fund_gate_blocked = 0,
                    survey_summary = '已完成现场勘验',
                    risk_level = 'MEDIUM'
                WHERE work_order_id = ?
                """, sourceId);
        return sourceId;
    }

    private Map<String, Object> buildingRequest(List<Map<String, Object>> workPoints) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planDescription", "已记录可观察现象、拟定维修措施和点位范围，尚未形成资金或治理快照。");
        plan.put("budgetTotal", 2207);
        plan.put("workPoints", workPoints);
        plan.put("attachments", List.of());
        return Map.of(
                "projectName", PROJECT_PREFIX + System.nanoTime(),
                "scopeType", "BUILDING",
                "buildingId", buildingId,
                "plan", plan);
    }

    private Map<String, Object> referenceRoomWorkPoint(
            long pointBuildingId, long referenceRoomId, List<Long> sourceIds) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("businessName", "参照房屋窗户与外墙交界渗水点");
        point.put("buildingId", pointBuildingId);
        point.put("locationType", "REFERENCE_ROOM");
        point.put("referenceRoomId", referenceRoomId);
        point.put("spaceName", "次卧");
        point.put("orientation", "北侧");
        point.put("component", "窗框与外墙交界");
        point.put("specificPart", "窗框周边密封部位");
        point.put("symptom", "雨后窗框周边可见渗水痕迹");
        point.put("causeStatus", "PENDING_INVESTIGATION");
        point.put("proposedMeasure", "清理既有密封层并按勘验结论修复防水节点");
        point.put("technicalRequirements", "施工前后留存同角度照片并避免破坏室内饰面");
        point.put("preliminaryEstimatedAmount", 2024);
        point.put("estimateSource", "现场初步估算记录");
        point.put("linkedWorkOrderIds", sourceIds);
        return point;
    }

    private Map<String, Object> commonAreaWorkPoint(
            long pointBuildingId, String businessName, String commonAreaName) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("businessName", businessName);
        point.put("buildingId", pointBuildingId);
        point.put("locationType", "COMMON_AREA");
        point.put("commonAreaName", commonAreaName);
        point.put("spaceName", "公共区域");
        point.put("component", "玻璃或排水构件");
        point.put("specificPart", "待现场复核具体部位");
        point.put("symptom", "现场可见破损或渗漏现象");
        point.put("causeStatus", "PENDING_INVESTIGATION");
        point.put("proposedMeasure", "按勘验结论更换破损构件并修复相关节点");
        point.put("technicalRequirements", "保留施工前、施工中和完工照片");
        point.put("linkedWorkOrderIds", List.of());
        return point;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
