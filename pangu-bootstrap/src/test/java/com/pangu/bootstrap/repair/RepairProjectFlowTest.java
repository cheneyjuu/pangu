// 关联业务：验证维修工程项目、两类真实流程路由、方案快照、项目附件及不可变版本链。
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "platform.public-api-base-url=https://api.example.test/api/v1")
@AutoConfigureMockMvc
class RepairProjectFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final String PROJECT_PREFIX = "IT-维修工程-";
    private static final String CASE_PREFIX = "IT-维修工程报修-";
    private static final String SUPPLIER_PREFIX = "IT-维修工程供应商-";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String token;
    private String ownerToken;
    private long buildingId;

    @BeforeEach
    void setUp() throws Exception {
        token = jwtTokenProvider.generateToken(
                ACCOUNT_PROPERTY_MANAGER, "SYS_USER", USER_PROPERTY_MANAGER, TENANT);
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
        Map<String, Object> owner = jdbcTemplate.queryForMap("""
                SELECT owner.account_id, owner.uid
                FROM c_owner_property property
                JOIN c_user owner ON owner.uid = property.uid
                WHERE property.tenant_id = ?
                  AND property.building_id = ?
                  AND property.account_status = 1
                ORDER BY property.opid
                LIMIT 1
                """, TENANT, buildingId);
        ownerToken = jwtTokenProvider.generateToken(
                ((Number) owner.get("account_id")).longValue(), "C_USER",
                ((Number) owner.get("uid")).longValue(), TENANT);
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2),
                        "project-etag"));
        when(objectStorage.createDownloadUrl(anyString(), any()))
                .thenReturn(URI.create("https://oss.example.test/repair-project").toURL());
        when(objectStorage.read(anyString())).thenReturn("photo".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM t_repair_project WHERE project_name LIKE ?", PROJECT_PREFIX + "%");
        RepairProjectSourcingTestSupport.cleanSuppliers(jdbcTemplate, SUPPLIER_PREFIX);
        jdbcTemplate.update("DELETE FROM t_repair_narrative_image WHERE tenant_id = ? AND status = 'DRAFT'", TENANT);
        jdbcTemplate.update("DELETE FROM t_repair_work_order WHERE title LIKE ?", CASE_PREFIX + "%");
    }

    @Test
    void buildingProjectLocksPlanAndSupportsImmutableRevision() throws Exception {
        long workOrderId = createBuildingRepairCase();
        JsonNode created = createProject(buildingProjectRequest(workOrderId, "1"));
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();
        assertEquals("BY_BUILDING_AREA",
                created.path("plans").get(0).path("allocationRuleType").asText());
        assertTrue(created.path("plans").get(0).path("allocationRuleDescription").asText()
                .contains("《上海市商品住宅专项维修资金管理办法》第十六条"));
        assertTrue(created.path("plans").get(0).path("supplierSelectionReason").isNull());
        assertTrue(created.path("currentPlanAffectedOwners").size() > 0);
        assertEquals("SYSTEM_RECOMMENDED",
                created.path("currentPlanAffectedOwners").get(0).path("sourceType").asText());
        assertEquals("PROJECT_LINKED", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_work_order WHERE work_order_id = ?",
                String.class, workOrderId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_repair_work_order_event
                WHERE work_order_id = ? AND action = 'LINK_PROJECT'
                """, Integer.class, workOrderId));

        mockMvc.perform(post("/api/v1/admin/repair-work-orders/" + workOrderId + "/submit-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "planBudget", 1000,
                                "fundSource", "BUILDING_MAINTENANCE_FUND"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg", is(
                        "共有部分维修已切换到维修工程项目；勘验完成后请在工程项目台账关联本工单，旧工单项目级写接口仅保留历史只读")));

        RepairProjectSourcingTestSupport.completeCompetitiveSourcing(
                mockMvc, objectMapper, token, SUPPLIER_PREFIX, projectId, 1000);
        long photoAttachmentId = upload(projectId, "现场照片.jpg", "image/jpeg", "photo");
        link(projectId, planId, photoAttachmentId, "SITE_PHOTO");

        String lockedBody = mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId
                        + "/plans/" + planId + "/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.workflowType", is("BUILDING_REPAIR")))
                .andExpect(jsonPath("$.data.project.status", is("PLAN_LOCKED")))
                .andExpect(jsonPath("$.data.project.version", is(1)))
                .andExpect(jsonPath("$.data.plans[0].status", is("LOCKED")))
                .andExpect(jsonPath("$.data.plans[0].snapshotHash").isString())
                .andExpect(jsonPath("$.data.currentPlanItems", hasSize(1)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode locked = objectMapper.readTree(lockedBody).path("data");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_repair_project_item_case WHERE work_order_id = ?",
                Integer.class, workOrderId));
        assertEquals("BUILDING_MAINTENANCE_FUND", jdbcTemplate.queryForObject(
                "SELECT fund_source FROM t_repair_project WHERE project_id = ?",
                String.class, projectId));
        assertNotNull(locked.path("plans").get(0).path("snapshotHash").textValue());

        Map<String, Object> revisionRequest = new LinkedHashMap<>();
        revisionRequest.put("expectedProjectVersion", 1);
        Map<String, Object> revisionPlan = buildingPlan(workOrderId, "2");
        revisionPlan.put("attachments", List.of(
                Map.of("attachmentId", photoAttachmentId, "purpose", "SITE_PHOTO")));
        revisionRequest.put("plan", revisionPlan);
        String revisedBody = mockMvc.perform(post(
                        "/api/v1/admin/repair-projects/" + projectId + "/plan-versions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(revisionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plans[0].versionNo", is(2)))
                .andExpect(jsonPath("$.data.plans[0].status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long revisionPlanId = objectMapper.readTree(revisedBody).path("data").path("plans").get(0)
                .path("planId").asLong();
        RepairProjectSourcingTestSupport.completeCompetitiveSourcing(
                mockMvc, objectMapper, token, SUPPLIER_PREFIX, projectId, 1000);

        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId
                        + "/plans/" + revisionPlanId + "/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.version", is(2)))
                .andExpect(jsonPath("$.data.plans[0].status", is("LOCKED")))
                .andExpect(jsonPath("$.data.plans[1].status", is("SUPERSEDED")));
    }

    @Test
    void communityProjectNeedsNoFakeRepairCaseAndRejectsBuildingFundRoute() throws Exception {
        JsonNode created = createProject(communityProjectRequest());
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();
        assertEquals("COMMUNITY_PUBLIC_REPAIR", created.path("project").path("workflowType").asText());
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_repair_project_item_case link
                JOIN t_repair_project_item item ON item.item_id = link.item_id
                WHERE item.project_id = ?
                """, Integer.class, projectId));

        RepairProjectSourcingTestSupport.completeCompetitiveSourcing(
                mockMvc, objectMapper, token, SUPPLIER_PREFIX, projectId, 1000);
        long photoAttachmentId = upload(projectId, "道路现场.jpg", "image/jpeg", "photo");
        link(projectId, planId, photoAttachmentId, "SITE_PHOTO");
        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId
                        + "/plans/" + planId + "/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plans[0].requiredAcceptanceRoles",
                        hasSize(4)))
                .andExpect(jsonPath("$.data.plans[0].affectedOwnerScopeDescription").doesNotExist());

        Map<String, Object> invalid = new LinkedHashMap<>(communityProjectRequest());
        invalid.put("fundSource", "BUILDING_MAINTENANCE_FUND");
        mockMvc.perform(post("/api/v1/admin/repair-projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("全小区公共区域维修只能使用小区公共维修资金并履行业主大会流程")));

        mockMvc.perform(get("/api/v1/admin/repair-projects/" + projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.project.projectId", is((int) projectId)));
    }

    @Test
    void ownerReadsOnlySanitizedLockedPlanThroughVisibleWorkOrder() throws Exception {
        long workOrderId = createBuildingRepairCase();
        Map<String, Object> request = buildingProjectRequest(workOrderId, "OWNER-VIEW");
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) request.get("plan");
        long narrativeImageId = uploadNarrativeImage("渗水点.jpg", "image/jpeg", "photo");
        plan.put("planDescription", "<h3>现场问题与维修方案</h3><script>alert(1)</script>"
                + "<p onclick=\"steal()\">外墙渗水<strong>严重</strong>，按锁定工程项施工</p>"
                + "<img src=\"https://attacker.example/x.jpg\">"
                + "<img data-repair-image-id=\"" + narrativeImageId + "\" alt=\"渗水点\">");

        JsonNode created = createProject(request);
        long projectId = created.path("project").path("projectId").asLong();
        long planId = created.path("plans").get(0).path("planId").asLong();

        mockMvc.perform(get("/api/v1/me/repair-projects/by-work-order/" + workOrderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        RepairProjectSourcingTestSupport.SelectedSupplier selectedSupplier =
                RepairProjectSourcingTestSupport.completeCompetitiveSourcing(
                        mockMvc, objectMapper, token, SUPPLIER_PREFIX, projectId, 1000);
        long photoAttachmentId = upload(projectId, "业主端现场.jpg", "image/jpeg", "photo");
        link(projectId, planId, photoAttachmentId, "SITE_PHOTO");
        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId
                        + "/plans/" + planId + "/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedProjectVersion", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/repair-projects/by-work-order/" + workOrderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId", is((int) projectId)))
                .andExpect(jsonPath("$.data.plan.planDescription", containsString(
                        "<h3>现场问题与维修方案</h3><p>外墙渗水<strong>严重</strong>，按锁定工程项施工</p>")))
                .andExpect(jsonPath("$.data.plan.planDescription", containsString(
                        "<img src=\"https://api.example.test/api/v1/public/repair-plan-images/"
                                + narrativeImageId + "?ticket=")))
                .andExpect(jsonPath("$.data.plan.items", hasSize(1)))
                .andExpect(jsonPath("$.data.plan.allocationSummary.roomCount").isNumber())
                .andExpect(jsonPath("$.data.plan.attachments", hasSize(2)))
                .andExpect(jsonPath("$.data.plan.selectedSupplier.supplierDeptId",
                        is((int) selectedSupplier.supplierDeptId())))
                .andExpect(jsonPath("$.data.plan.selectedSupplier.taxRate", is(0.0)))
                .andExpect(jsonPath("$.data.plan.selectedSupplier.quoteLines[0].lineType",
                        is("CONSTRUCTION_MEASURE")))
                .andExpect(jsonPath("$.data.plan.selectedSupplier.quoteLines[0].unitPriceExcludingTax").isNumber())
                .andExpect(jsonPath("$.data.plan.selectedSupplier.quoteLines[0].taxIncludedUnitPrice").isNumber())
                .andExpect(jsonPath("$.data.plan.lockedAt").isString());

        String disclosureBody = mockMvc.perform(get(
                        "/api/v1/me/repair-projects/by-work-order/" + workOrderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String disclosedHtml = objectMapper.readTree(disclosureBody)
                .path("data").path("plan").path("planDescription").asText();
        Matcher imageSource = Pattern.compile("<img src=\"([^\"]+)\"")
                .matcher(disclosedHtml);
        assertTrue(imageSource.find());
        URI deliveryUri = URI.create(imageSource.group(1).replace("&amp;", "&"));
        String deliveryTicket = deliveryUri.getRawQuery().substring("ticket=".length());

        mockMvc.perform(get(deliveryUri.getRawPath())
                        .queryParam("ticket", deliveryTicket))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes("photo".getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(get(deliveryUri.getRawPath())
                        .queryParam("ticket", deliveryTicket + "tampered"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/me/repair-projects/by-work-order/" + workOrderId
                        + "/attachments/" + selectedSupplier.quoteAttachmentId() + "/download-ticket")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentId",
                        is((int) selectedSupplier.quoteAttachmentId())))
                .andExpect(jsonPath("$.data.downloadUrl", is(
                        "https://oss.example.test/repair-project")));

        String objectKey = jdbcTemplate.queryForObject("""
                SELECT object_key
                FROM t_repair_narrative_image
                WHERE image_id = ? AND plan_id = ? AND tenant_id = ?
                """, String.class, narrativeImageId, planId, TENANT);
        jdbcTemplate.update("""
                UPDATE t_repair_plan_version
                SET plan_description = ?
                WHERE plan_id = ? AND tenant_id = ?
                """, "<h3>历史方案</h3><img src=\"https://oss.example.test/"
                        + objectKey
                        + "?x-oss-date=old&amp;x-oss-signature=old\" alt=\"历史现场图\">",
                planId, TENANT);
        String legacyBody = mockMvc.perform(get(
                        "/api/v1/me/repair-projects/by-work-order/" + workOrderId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String resolvedLegacyHtml = objectMapper.readTree(legacyBody)
                .path("data").path("plan").path("planDescription").asText();
        assertTrue(resolvedLegacyHtml.contains(
                "https://api.example.test/api/v1/public/repair-plan-images/"
                        + narrativeImageId + "?ticket="));
        assertFalse(resolvedLegacyHtml.contains("x-oss-date=old"));
    }

    @Test
    void planRejectsRichTextWithoutVisibleContent() throws Exception {
        long workOrderId = createBuildingRepairCase();
        Map<String, Object> request = buildingProjectRequest(workOrderId, "EMPTY-RICH-TEXT");
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) request.get("plan");
        plan.put("planDescription", "<script>alert('only script')</script>");

        mockMvc.perform(post("/api/v1/admin/repair-projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("planDescription 必填")));
    }

    @Test
    void allocationPreviewComesFromVerifiedPropertyLedgerAndStatutoryRule() throws Exception {
        mockMvc.perform(get("/api/v1/admin/repair-projects/allocation-preview")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("scopeType", "BUILDING")
                        .queryParam("buildingId", String.valueOf(buildingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopeType", is("BUILDING")))
                .andExpect(jsonPath("$.data.fundSource", is("BUILDING_MAINTENANCE_FUND")))
                .andExpect(jsonPath("$.data.scopeLabel").isNotEmpty())
                .andExpect(jsonPath("$.data.roomCount").isNumber())
                .andExpect(jsonPath("$.data.totalBuildArea").isNumber())
                .andExpect(jsonPath("$.data.allocationRuleType", is("BY_BUILDING_AREA")))
                .andExpect(jsonPath("$.data.legalBasis",
                        is("《上海市商品住宅专项维修资金管理办法》第十六条")));
    }

    @Test
    void affectedOwnerPreviewCanBeAdjustedOnlyWithRecordedReason() throws Exception {
        String previewBody = mockMvc.perform(get("/api/v1/admin/repair-projects/affected-owner-preview")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("scopeType", "BUILDING")
                        .queryParam("buildingId", String.valueOf(buildingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopeLabel").isNotEmpty())
                .andExpect(jsonPath("$.data.recommendedOwnerCount").isNumber())
                .andExpect(jsonPath("$.data.candidates[0].roomName").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode candidates = objectMapper.readTree(previewBody).path("data").path("candidates");
        assertTrue(candidates.size() > 1, "测试楼栋应包含多套已核验产权房屋");

        long workOrderId = createBuildingRepairCase();
        Map<String, Object> invalid = buildingProjectRequest(workOrderId, "ADJUST");
        @SuppressWarnings("unchecked")
        Map<String, Object> invalidPlan = (Map<String, Object>) invalid.get("plan");
        invalidPlan.put("affectedOwners", List.of(Map.of(
                "roomId", candidates.get(0).path("roomId").asLong(),
                "affectedReason", "靠近本次外墙维修作业面")));

        mockMvc.perform(post("/api/v1/admin/repair-projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("调整系统推荐的受影响业主名单时必须填写调整原因")));

        invalidPlan.put("affectedOwnerAdjustmentReason", "现场勘验确认仅该房屋直接受施工影响");
        JsonNode created = createProject(invalid);
        assertEquals(1, created.path("currentPlanAffectedOwners").size());
        assertEquals("PROPERTY_ADJUSTED",
                created.path("currentPlanAffectedOwners").get(0).path("sourceType").asText());
        assertTrue(created.path("plans").get(0).path("affectedOwnerScopeDescription").asText()
                .contains("已锁定 1 名受影响业主"));
    }

    private JsonNode createProject(Map<String, Object> request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/repair-projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data");
    }

    private long createBuildingRepairCase() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/repair-work-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "publicAreaScope", "BUILDING",
                                "buildingId", buildingId,
                                "locationText", "楼栋外墙",
                                "title", CASE_PREFIX + System.nanoTime(),
                                "description", "外墙渗水待形成工程项目",
                                "category", "WATERPROOFING"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long workOrderId = objectMapper.readTree(response).path("data").path("workOrderId").asLong();
        jdbcTemplate.update("""
                UPDATE t_repair_work_order
                SET status = 'SURVEY_COMPLETED',
                    location_locked = 1,
                    need_manual_location = 0,
                    fund_gate_blocked = 0,
                    survey_summary = '已完成现场勘验',
                    risk_level = 'MEDIUM'
                WHERE work_order_id = ?
                """, workOrderId);
        return workOrderId;
    }

    private Map<String, Object> buildingProjectRequest(long workOrderId, String suffix) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectName", PROJECT_PREFIX + "楼栋渗水-" + System.nanoTime());
        request.put("scopeType", "BUILDING");
        request.put("buildingId", buildingId);
        request.put("fundSource", "BUILDING_MAINTENANCE_FUND");
        request.put("governancePath", "BUILDING_REPAIR_DECISION");
        request.put("plan", buildingPlan(workOrderId, suffix));
        return request;
    }

    private Map<String, Object> communityProjectRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectName", PROJECT_PREFIX + "小区道路-" + System.nanoTime());
        request.put("scopeType", "COMMUNITY");
        request.put("fundSource", "COMMUNITY_MAINTENANCE_FUND");
        request.put("governancePath", "COMMUNITY_ASSEMBLY_DECISION");
        request.put("plan", communityPlan());
        return request;
    }

    private Map<String, Object> buildingPlan(long workOrderId, String suffix) {
        Map<String, Object> plan = commonPlan("楼栋外墙渗水原因及维修范围-版本" + suffix);
        plan.put("minimumAffectedOwnerAcceptors", 1);
        plan.put("affectedOwnerPassRule", "ALL");
        plan.put("affectedOwnerApprovalRatio", 1);
        plan.put("items", List.of(Map.ofEntries(
                Map.entry("itemNo", "WATERPROOF-" + suffix),
                Map.entry("buildingId", buildingId),
                Map.entry("locationText", "楼栋外墙"),
                Map.entry("workContent", "外墙渗水点清理并实施防水"),
                Map.entry("quantity", 10),
                Map.entry("unit", "平方米"),
                Map.entry("estimatedUnitPrice", 100),
                Map.entry("estimatedAmount", 1000),
                Map.entry("linkedWorkOrderIds", List.of(workOrderId)))));
        return plan;
    }

    private Map<String, Object> communityPlan() {
        Map<String, Object> plan = commonPlan("小区道路破损原因及整体维修范围");
        plan.put("items", List.of(Map.ofEntries(
                Map.entry("itemNo", "ROAD-1"),
                Map.entry("locationText", "小区主干道"),
                Map.entry("workContent", "破损路面铣刨并重铺"),
                Map.entry("quantity", 10),
                Map.entry("unit", "平方米"),
                Map.entry("estimatedUnitPrice", 100),
                Map.entry("estimatedAmount", 1000),
                Map.entry("linkedWorkOrderIds", List.of()))));
        return plan;
    }

    private Map<String, Object> commonPlan(String cause) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planDescription", cause + "；按工程项清单及锁定范围施工");
        plan.put("budgetTotal", 1000);
        plan.put("supplierSelectionMethod", "COMPETITIVE_QUOTATION");
        plan.put("constructionManagementRequirements", "物业项目负责人组织现场和工程量管理");
        plan.put("evidenceRequirements", evidenceRequirements());
        plan.put("safetyRequirements", "设置围挡并落实高空和用电安全措施");
        plan.put("acceptanceMethod", "按锁定工程项核对实际数量、单价、金额和过程证据");
        plan.put("settlementMethod", "ACTUAL_QUANTITY");
        plan.put("plannedStartDate", LocalDate.now().plusDays(10));
        plan.put("plannedCompletionDate", LocalDate.now().plusDays(30));
        plan.put("warrantyDays", 365);
        plan.put("priceReviewRequired", true);
        plan.put("paymentMilestones", paymentMilestones());
        plan.put("attachments", List.of());
        return plan;
    }

    private List<Map<String, Object>> evidenceRequirements() {
        List<Map<String, Object>> requirements = new ArrayList<>();
        for (String stage : List.of(
                "BEFORE_CONSTRUCTION", "MATERIAL_ENTRY", "DURING_CONSTRUCTION",
                "CONCEALED_WORK", "COMPLETION", "ACCEPTANCE")) {
            requirements.add(Map.of(
                    "stage", stage,
                    "description", stage + " 原始照片、核验人和时间",
                    "required", true));
        }
        return requirements;
    }

    private List<Map<String, Object>> paymentMilestones() {
        return List.of(
                Map.of("type", "ADVANCE", "maximumContractRatio", 0.30,
                        "requiredEvidenceCodes", List.of("SIGNED_CONTRACT")),
                Map.of("type", "PROGRESS", "maximumContractRatio", 0.90,
                        "requiredEvidenceCodes", List.of("PROGRESS_RECORD")),
                Map.of("type", "COMPLETION", "maximumContractRatio", 1.00,
                        "requiredEvidenceCodes", List.of("ACCEPTANCE", "SETTLEMENT")),
                Map.of("type", "WARRANTY_RELEASE", "maximumContractRatio", 1.00,
                        "requiredEvidenceCodes", List.of("WARRANTY_EXPIRED_CERTIFICATE")));
    }

    private long upload(long projectId, String fileName, String contentType, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, contentType, content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart(
                        "/api/v1/admin/repair-projects/" + projectId + "/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("attachmentId").asLong();
    }

    private long uploadNarrativeImage(String fileName, String contentType, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, contentType, content.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/admin/repair-projects/narrative-images")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value(org.hamcrest.Matchers.startsWith("repair-image://")))
                .andExpect(jsonPath("$.data.previewUrl", is("https://oss.example.test/repair-project")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("imageId").asLong();
    }

    private void link(long projectId, long planId, long attachmentId, String purpose) throws Exception {
        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId
                        + "/plans/" + planId + "/attachments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("attachmentId", attachmentId, "purpose", purpose))))
                .andExpect(status().isOk());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
