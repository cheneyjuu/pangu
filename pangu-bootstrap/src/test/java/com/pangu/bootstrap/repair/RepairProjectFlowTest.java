// 关联业务：验证单一决定范围草稿、维修点位、来源边界，以及按可信专项维修资金账簿锁定实施方案。
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepairProjectFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final String PROJECT_PREFIX = "IT-维修点位-";
    private static final String CASE_PREFIX = "IT-维修点位来源-";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String propertyToken;
    private long buildingId;
    private long roomId;
    private long otherBuildingId;
    private Long createdFundingAccountId;

    @BeforeEach
    void setUp() {
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
        if (createdFundingAccountId != null) {
            jdbcTemplate.update("DELETE FROM t_fund_ledger_entry WHERE account_id = ?", createdFundingAccountId);
            jdbcTemplate.update("DELETE FROM t_maintenance_fund_account WHERE account_id = ?", createdFundingAccountId);
        }
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
                        "来源报修事项不属于当前决定范围 workOrderId=" + otherScopeSource)));
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
                .andExpect(jsonPath("$.msg", is("决定范围尚待核验，不能锁定实施方案")));
    }

    @Test
    void confirmedScopeWithoutScopedMaintenanceAccountBlocksLockBeforeAnyQuoteRequirement() throws Exception {
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
                        "当前楼栋尚未接入专项维修资金账户，不能锁定实施方案")));
    }

    @Test
    void confirmedBuildingScopeLocksPlanWithScopedMaintenanceAccountAndFrozenAllocationSnapshot() throws Exception {
        long sourceId = createConfirmedBuildingSource(buildingId);
        JsonNode created = createProject(buildingRequest(List.of(
                referenceRoomWorkPoint(buildingId, roomId, List.of(sourceId)))));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();
        createdFundingAccountId = seedBuildingMaintenanceAccount(new BigDecimal("2207.00"));

        String response = mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/lock")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.status", is("PLAN_LOCKED")))
                .andExpect(jsonPath("$.data.fundingSlices.length()", is(1)))
                .andExpect(jsonPath("$.data.fundingSlices[0].sourceType", is("SPECIAL_MAINTENANCE_LEDGER")))
                .andExpect(jsonPath("$.data.fundingSlices[0].sourceRecordId",
                        is(createdFundingAccountId.toString())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode locked = objectMapper.readTree(response).path("data");
        assertEquals(64, locked.path("plans").get(0).path("snapshotHash").asText().length());
        int allocationRoomCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_plan_allocation_room WHERE plan_id = ?
                """, Integer.class, planId);
        assertTrue(allocationRoomCount > 0);
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_repair_funding_slice
                WHERE project_id = ?
                  AND source_record_type = 'MAINTENANCE_FUND_ACCOUNT'
                  AND source_record_id = ?
                  AND allocation_snapshot_hash ~ '^[0-9a-f]{64}$'
                """, Integer.class, projectId, createdFundingAccountId.toString()));
    }

    @Test
    void scopedMaintenanceAccountWithInsufficientAvailableBalanceDoesNotPersistAnySnapshot() throws Exception {
        long sourceId = createConfirmedBuildingSource(buildingId);
        JsonNode created = createProject(buildingRequest(List.of(
                referenceRoomWorkPoint(buildingId, roomId, List.of(sourceId)))));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();
        createdFundingAccountId = seedBuildingMaintenanceAccount(new BigDecimal("2206.99"));

        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/lock")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg", is("专项维修资金账户可用余额不足，不能锁定实施方案")));

        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_plan_allocation_room WHERE plan_id = ?
                """, Integer.class, planId));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_funding_slice WHERE project_id = ?
                """, Integer.class, projectId));
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
                .andExpect(jsonPath("$.msg", is("planDescription 必填")));
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

    /** 测试仅写入与当前楼栋严格绑定的账簿记录，避免用社区账户替代楼栋资金范围。 */
    private long seedBuildingMaintenanceAccount(BigDecimal totalBalance) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_maintenance_fund_account (
                    tenant_id, account_level, reference_id, ancestors, total_balance, frozen_balance
                ) VALUES (?, 2, ?, '0', ?, 0)
                RETURNING account_id
                """, Long.class, TENANT, buildingId, totalBalance);
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
