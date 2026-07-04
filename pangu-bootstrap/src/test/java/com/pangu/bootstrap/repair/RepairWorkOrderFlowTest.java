package com.pangu.bootstrap.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private static final long DEPT_GRID = 104L;
    private static final long TENANT_CROSS = 10002L;
    private static final long CROSS_ROOM = 10002300101L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    public void clean() {
        jdbcTemplate.update("DELETE FROM t_repair_work_order WHERE title LIKE 'IT-报修-%'");
        jdbcTemplate.update("""
                DELETE FROM sys_dept_building_scope
                WHERE dept_id = ? AND tenant_id = ? AND building_id = 30001
                """, DEPT_GRID, TENANT_CROSS);
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
        action(managerToken, id, "assign", Map.of(
                "assignedUserId", USR_PROPERTY_STAFF,
                "assigneeRoleKey", "PROPERTY_STAFF",
                "remark", "派给工程员工"))
                .andExpect(jsonPath("$.data.status", is("ASSIGNED")));
        action(staffToken, id, "start-survey", Map.of("remark", "开始初勘"))
                .andExpect(jsonPath("$.data.status", is("SURVEYING")));
        action(staffToken, id, "submit-plan", Map.of(
                "surveySummary", "厨房立管接头老化，需更换阀门并恢复墙面",
                "riskLevel", "LOW",
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
        action(directorToken, id, "accept-completed", Map.of("remark", "验收通过"))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        mockMvc.perform(post("/api/v1/me/repairs/" + id + "/evaluation")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("satisfactionScore", 5, "comment", "响应及时"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("EVALUATED")))
                .andExpect(jsonPath("$.data.satisfactionScore", is(5)));

        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id + "/events")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(10)));
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

        action(staffToken, id, "correct-location", Map.of(
                "buildingId", 30002,
                "locationText", "2号楼大堂门禁",
                "reason", "现场确认在2号楼"))
                .andExpect(jsonPath("$.data.status", is("PENDING_VERIFY")))
                .andExpect(jsonPath("$.data.needManualLocation", is(false)));
        action(staffToken, id, "verify-location", Map.of("remark", "位置锁定"))
                .andExpect(jsonPath("$.data.status", is("VERIFIED")))
                .andExpect(jsonPath("$.data.locationLocked", is(true)));
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
                INSERT INTO sys_dept_building_scope (dept_id, tenant_id, building_id, assigned_by, status)
                VALUES (?, ?, 30001, 800003, 1)
                ON CONFLICT (dept_id, tenant_id, building_id)
                DO UPDATE SET status = 1, assigned_by = EXCLUDED.assigned_by, updated_at = now()
                """, DEPT_GRID, TENANT_CROSS);
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
    public void streetAdminWithoutTenantHint_canSeeAllDistrictRepairOrders() throws Exception {
        String title = "IT-报修-街道跨租户视野-" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO t_repair_work_order
                    (tenant_id, title, description, source, space_scope, status,
                     reporter_account_id, reporter_uid, building_id, location_text, category)
                VALUES (?, ?, '街道全辖区工单可见性测试', 'C_OWNER_APP', 'PUBLIC', 'SUBMITTED',
                        999812, 70001, 30001, '跨小区公共区域', 'PUBLIC_FACILITY')
                """, TENANT_CROSS, title);
        String streetToken = token(ACC_STREET, "SYS_USER", USR_STREET, null);

        String response = mockMvc.perform(get("/api/v1/admin/repair-work-orders")
                        .param("keyword", title)
                        .header("Authorization", "Bearer " + streetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.items[0].tenantId", is((int) TENANT_CROSS)))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).path("data").path("items").get(0).path("workOrderId").asLong();

        mockMvc.perform(get("/api/v1/admin/repair-work-orders/" + id)
                        .header("Authorization", "Bearer " + streetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId", is((int) TENANT_CROSS)));
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
                .andReturn().getResponse().getContentAsString();
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

    private String token(long accountId, String identityType, long activeIdentityId) {
        return jwtTokenProvider.generateToken(accountId, identityType, activeIdentityId, TENANT);
    }

    private String token(long accountId, String identityType, long activeIdentityId, Long tenantId) {
        return jwtTokenProvider.generateToken(accountId, identityType, activeIdentityId, tenantId);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private long dataId(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        return root.path("data").path("workOrderId").asLong();
    }
}
