// 关联业务：端到端验证楼栋维修从勘验、参考询价、统一表决到施工单位确认的真实状态交接。
package com.pangu.bootstrap.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private Long createdOwnersAssemblyRuleId;
    private Long previousActiveRuleId;
    private final Map<Long, Integer> originalVotingDelegates = new LinkedHashMap<>();
    private final Map<Long, Integer> originalOwnerAuthLevels = new LinkedHashMap<>();

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
        previousActiveRuleId = jdbcTemplate.query(
                "SELECT rule_id FROM t_owners_assembly_rule WHERE tenant_id = ? AND status = 'ACTIVE'",
                resultSet -> resultSet.next() ? resultSet.getLong(1) : null,
                TENANT);
        configureUniqueBuildingDelegates();
        when(objectStorage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new RepairEvidenceObjectStorage.StoredObjectMetadata(
                        ((byte[]) invocation.getArgument(1)).length,
                        invocation.getArgument(2), "repair-project-e2e-etag"));
    }

    @AfterEach
    void clean() {
        cleanupUnifiedVoting();
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
        if (createdOwnersAssemblyRuleId != null) {
            jdbcTemplate.update(
                    "DELETE FROM t_owners_assembly_rule_field_confirmation WHERE rule_id = ?",
                    createdOwnersAssemblyRuleId);
            jdbcTemplate.update(
                    "DELETE FROM t_owners_assembly_rule_audit WHERE rule_id = ?",
                    createdOwnersAssemblyRuleId);
            jdbcTemplate.update(
                    "DELETE FROM t_owners_assembly_rule WHERE rule_id = ?",
                    createdOwnersAssemblyRuleId);
        }
        if (previousActiveRuleId != null) {
            jdbcTemplate.update("""
                    UPDATE t_owners_assembly_rule
                    SET status = 'ACTIVE', update_time = CURRENT_TIMESTAMP
                    WHERE rule_id = ?
                    """, previousActiveRuleId);
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
        originalVotingDelegates.forEach((opid, value) -> jdbcTemplate.update(
                "UPDATE c_owner_property SET is_voting_delegate = ? WHERE opid = ?", value, opid));
        originalOwnerAuthLevels.forEach((uid, value) -> jdbcTemplate.update(
                "UPDATE c_user SET auth_level = ? WHERE uid = ?", value, uid));
    }

    @Test
    void surveyedBuildingRepairRunsFromReferenceQuoteToAuthorizedSupplierSelection() throws Exception {
        activateOnlineOwnersAssemblyRule();
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
                propertyManagerToken, Map.of(
                        "expectedProjectVersion",
                        responsibilityConfirmed.path("project").path("version").asInt(),
                        "supplierSelectionMethod", "COMPETITIVE_QUOTATION",
                        "supplierEvaluationRule", "LOWEST_COMPLIANT_QUOTE",
                        "minimumInvitedSupplierCount", 1,
                        "minimumValidQuoteCount", 1));
        assertEquals("AUTHORIZATION_IN_PROGRESS", frozen.path("project").path("status").asText());
        assertEquals("AUTHORIZATION_FROZEN", frozen.path("plans").get(0).path("status").asText());
        assertEquals(64, frozen.path("plans").get(0).path("authorizationSnapshotHash").asText().length());
        assertTrue(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) > 0
                FROM t_repair_plan_allocation_room
                WHERE plan_id = ?
                """, Boolean.class, planId));

        long ballotTemplateAttachmentId = uploadProjectAttachment(
                projectId, committeeDirectorToken, "相关业主表决票模板.pdf", "ballot-template");
        Instant voteStartAt = Instant.now().plus(2, ChronoUnit.MINUTES);
        JsonNode prepared = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/voting/prepare",
                committeeDirectorToken, Map.of(
                        "expectedProjectVersion", frozen.path("project").path("version").asInt(),
                        "collectionMode", "ONLINE_WITH_PAPER_ASSISTANCE",
                        "paperBallotTemplateAttachmentId", ballotTemplateAttachmentId,
                        "voteStartAt", voteStartAt.toString(),
                        "voteEndAt", voteStartAt.plus(30, ChronoUnit.MINUTES).toString()));
        assertEquals("PREPARED", prepared.path("voting").path("status").asText());
        long executionPackageId = prepared.path("executionPackage").path("packageId").asLong();
        long subjectId = prepared.path("subject").path("subjectId").asLong();
        makeVotingOpenable(executionPackageId, subjectId);
        JsonNode opened = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/voting/open",
                committeeDirectorToken,
                Map.of("expectedLinkVersion", prepared.path("voting").path("version").asLong()));
        assertEquals("VOTING", opened.path("voting").path("status").asText());

        List<Map<String, Object>> voters = jdbcTemplate.queryForList("""
                SELECT electorate.representative_uid AS owner_uid,
                       electorate.representative_opid AS opid,
                       owner.account_id,
                       owner.auth_level
                FROM t_voting_electorate_item_snapshot electorate
                JOIN t_voting_electorate_snapshot snapshot
                  ON snapshot.snapshot_id = electorate.snapshot_id
                JOIN c_user owner ON owner.uid = electorate.representative_uid
                WHERE snapshot.package_id = ?
                ORDER BY electorate.snapshot_item_id
                """, executionPackageId);
        assertTrue(!voters.isEmpty());
        for (Map<String, Object> voter : voters) {
            long ownerUid = ((Number) voter.get("owner_uid")).longValue();
            long ownerAccountId = ((Number) voter.get("account_id")).longValue();
            long opid = ((Number) voter.get("opid")).longValue();
            originalOwnerAuthLevels.putIfAbsent(ownerUid, ((Number) voter.get("auth_level")).intValue());
            jdbcTemplate.update("UPDATE c_user SET auth_level = 3 WHERE uid = ?", ownerUid);
            String ownerToken = jwtTokenProvider.generateToken(ownerAccountId, "C_USER", ownerUid, TENANT);
            JsonNode disclosure = getData(
                    "/api/v1/me/repair-projects/" + projectId + "/voting", ownerToken);
            assertTrue(!disclosure.path("properties").get(0).path("buildingName").asText().isBlank());
            assertTrue(!disclosure.path("properties").get(0).path("roomName").asText().isBlank());
            String packageHash = disclosure.path("packageHash").asText();
            postData(
                    "/api/v1/me/repair-projects/" + projectId + "/voting/acknowledgements",
                    ownerToken, Map.of("opid", opid, "packageHash", packageHash, "confirmed", true));
            postOwnerBallot(projectId, ownerToken, opid, packageHash, subjectId);
        }
        makeVotingClosable(executionPackageId, subjectId);
        JsonNode settled = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/voting/settle",
                committeeDirectorToken,
                Map.of("expectedLinkVersion", opened.path("voting").path("version").asLong()));
        assertEquals("SETTLED", settled.path("voting").path("status").asText());
        assertEquals("PASSED", settled.path("voting").path("result").asText());
        assertEquals("AUTHORIZED", settled.path("project").path("status").asText());
        assertEquals(voters.stream().map(item -> item.get("owner_uid")).distinct().count(),
                settled.path("result").path("supportOwnerCount").asLong());
        assertEquals(0L, settled.path("result").path("againstOwnerCount").asLong());
        assertEquals(0L, settled.path("result").path("abstainOwnerCount").asLong());
        Map<String, Object> firstVoter = voters.getFirst();
        String firstOwnerToken = jwtTokenProvider.generateToken(
                ((Number) firstVoter.get("account_id")).longValue(),
                "C_USER",
                ((Number) firstVoter.get("owner_uid")).longValue(),
                TENANT);
        JsonNode ownerResult = getData(
                "/api/v1/me/repair-projects/" + projectId + "/voting", firstOwnerToken);
        assertTrue(ownerResult.path("result").path("passed").asBoolean());
        assertEquals(voters.stream().map(item -> item.get("owner_uid")).distinct().count(),
                ownerResult.path("result").path("participatingOwnerCount").asLong());
        assertEquals(voters.stream().map(item -> item.get("owner_uid")).distinct().count(),
                ownerResult.path("result").path("supportOwnerCount").asLong());
        assertEquals(0L, ownerResult.path("result").path("againstOwnerCount").asLong());
        assertEquals(0L, ownerResult.path("result").path("abstainOwnerCount").asLong());
        assertTrue(!ownerResult.toString().contains("choice"));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_repair_governance_basis
                WHERE project_id = ? AND plan_id = ? AND status = 'ACTIVE'
                  AND basis_type = 'OWNER_VOTING_DECISION'
                  AND approved_supplier_selection_method = 'COMPETITIVE_QUOTATION'
                  AND approved_budget_amount = ?
                """, Integer.class, projectId, planId, PLAN_BUDGET));

        JsonNode locked = postData(
                "/api/v1/admin/repair-projects/" + projectId + "/plans/" + planId + "/lock",
                propertyManagerToken, Map.of(
                        "expectedProjectVersion", settled.path("project").path("version").asInt()));
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
                "selectionRationale", "该报价满足实施方案要求，且为当前有效报价中的最低价格。",
                "selectionEvidenceAttachmentId", selectionEvidenceAttachmentId));
        assertEquals("AUTHORIZED", selected.path("selectionAuthorization").path("status").asText());
        assertTrue(selected.path("selectionAuthorization").path("currentActorMayConfirm").asBoolean());
        assertEquals("COMPETITIVE_QUOTATION", selected.path("selection").path("selectionMethod").asText());
        assertEquals(quoteId, selected.path("selection").path("quoteId").asLong());
        assertEquals(USER_COMMITTEE_DIRECTOR, jdbcTemplate.queryForObject("""
                SELECT confirmed_by_user_id
                FROM t_repair_project_supplier_selection
                WHERE project_id = ? AND quote_id = ?
                """, Long.class, projectId, quoteId));
    }

    /** 每个专有部分只保留一个表决代表，避免测试夹具中的历史共有产权数据造成名册歧义。 */
    private void configureUniqueBuildingDelegates() {
        List<Map<String, Object>> properties = jdbcTemplate.queryForList("""
                SELECT opid, room_id, is_voting_delegate
                FROM c_owner_property
                WHERE tenant_id = ? AND building_id = ? AND account_status = 1
                ORDER BY room_id, opid
                """, TENANT, buildingId);
        properties.forEach(property -> originalVotingDelegates.put(
                ((Number) property.get("opid")).longValue(),
                ((Number) property.get("is_voting_delegate")).intValue()));
        jdbcTemplate.update("""
                UPDATE c_owner_property
                SET is_voting_delegate = 0
                WHERE tenant_id = ? AND building_id = ? AND account_status = 1
                """, TENANT, buildingId);
        jdbcTemplate.update("""
                UPDATE c_owner_property owner_property
                SET is_voting_delegate = 1
                WHERE owner_property.opid IN (
                    SELECT MIN(opid)
                    FROM c_owner_property
                    WHERE tenant_id = ? AND building_id = ? AND account_status = 1
                    GROUP BY room_id
                )
                """, TENANT, buildingId);
    }

    /**
     * 本用例登记并逐字段确认一份只允许实名线上收集、可申请纸质协助的有效议事规则。
     * 规则内容来自测试原件，不复用或篡改环境中已有生效规则。
     */
    private void activateOnlineOwnersAssemblyRule() throws Exception {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("allowedMeetingForms", List.of("INTERNET"));
        configuration.put("planPublicityDays", 0);
        configuration.put("meetingNoticeDays", 0);
        configuration.put("validDeliveryMethods", List.of("ELECTRONIC", "DOOR_TO_DOOR"));
        configuration.put("nonResponsePolicy", "NOT_PARTICIPATED");
        configuration.put("proxyVotingPolicy", "NOT_ALLOWED");
        configuration.put("votingChannelPolicy", "ONLINE_ONLY");
        configuration.put("onlineIdentityVerificationRequired", true);
        configuration.put("paperBallotSealRequired", true);
        configuration.put("duplicateVotePolicy", "NOT_APPLICABLE");
        configuration.put("countingRules", Map.of(
                "GENERAL", countingRule(),
                "MAJOR", countingRule()));
        configuration.put("resultAnnouncementDays", 0);
        configuration.put("sourceClauseReferences", allSourceReferences());

        String version = "2026-IT-repair-online-" + System.nanoTime();
        MockMultipartFile configurationPart = new MockMultipartFile(
                "configuration", "configuration.json", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(configuration));
        MockMultipartFile sourceFile = new MockMultipartFile(
                "file", "维修相关业主线上表决规则.pdf", MediaType.APPLICATION_PDF_VALUE,
                "repair-online-rule".getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart("/api/v1/admin/owners-assembly-rules/drafts")
                        .file(configurationPart)
                        .file(sourceFile)
                        .param("ruleName", "IT-维修相关业主表决规则-" + version)
                        .param("ruleVersion", version)
                        .param("effectiveDate", LocalDate.now().minusDays(1).toString())
                        .param("changeReason", "楼栋维修统一表决端到端验证")
                        .header("Authorization", bearer(propertyManagerToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        createdOwnersAssemblyRuleId = objectMapper.readTree(response).path("data").path("ruleId").asLong();
        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + createdOwnersAssemblyRuleId + "/submit")
                        .header("Authorization", bearer(propertyManagerToken)))
                .andExpect(status().isOk());
        for (OwnersAssemblyRuleConfiguration.RuleConfigurationField field
                : OwnersAssemblyRuleConfiguration.RuleConfigurationField.values()) {
            mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + createdOwnersAssemblyRuleId
                            + "/field-confirmations/" + field.name() + "/confirm")
                            .header("Authorization", bearer(committeeDirectorToken)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/admin/owners-assembly-rules/" + createdOwnersAssemblyRuleId + "/activate")
                        .header("Authorization", bearer(committeeDirectorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    private Map<String, Object> countingRule() {
        return Map.of(
                "participationOwnerThreshold", threshold(1, 2, "AT_LEAST"),
                "participationAreaThreshold", threshold(1, 2, "AT_LEAST"),
                "approvalOwnerThreshold", threshold(1, 2, "GREATER_THAN"),
                "approvalAreaThreshold", threshold(1, 2, "GREATER_THAN"));
    }

    private Map<String, Object> threshold(int numerator, int denominator, String comparison) {
        return Map.of(
                "numerator", numerator,
                "denominator", denominator,
                "comparison", comparison);
    }

    private Map<String, Object> allSourceReferences() {
        Map<String, Object> references = new LinkedHashMap<>();
        for (OwnersAssemblyRuleConfiguration.RuleConfigurationField field
                : OwnersAssemblyRuleConfiguration.RuleConfigurationField.values()) {
            references.put(field.name(), Map.of(
                    "pageNumber", 1,
                    "clause", "第 1 条：" + field.name()));
        }
        return references;
    }

    /** 测试只推进已经真实冻结的表决包时钟，不绕过生产状态校验。 */
    private void makeVotingOpenable(long executionPackageId, long subjectId) {
        jdbcTemplate.update("""
                UPDATE t_voting_execution_package
                SET vote_start_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE package_id = ?
                """, executionPackageId);
        jdbcTemplate.update("""
                UPDATE t_voting_subject
                SET vote_start_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE subject_id = ?
                """, subjectId);
    }

    /** 测试在完成真实逐户投票后推进截止时间，再调用正式结算服务。 */
    private void makeVotingClosable(long executionPackageId, long subjectId) {
        jdbcTemplate.update("""
                UPDATE t_voting_execution_package
                SET vote_end_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE package_id = ?
                """, executionPackageId);
        jdbcTemplate.update("""
                UPDATE t_voting_subject
                SET vote_end_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE subject_id = ?
                """, subjectId);
    }

    private JsonNode getData(String path, String token) throws Exception {
        String response = mockMvc.perform(get(path)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data");
    }

    private void postOwnerBallot(
            long projectId,
            String token,
            long opid,
            String packageHash,
            long subjectId) throws Exception {
        mockMvc.perform(post("/api/v1/me/repair-projects/" + projectId + "/voting/ballots")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "repair-e2e-" + projectId + "-" + opid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "opid", opid,
                                "packageHash", packageHash,
                                "confirmed", true,
                                "decisions", List.of(Map.of(
                                        "subjectId", subjectId,
                                        "choice", "SUPPORT"))))))
                .andExpect(status().isCreated());
    }

    /** 删除本用例产生的统一表决证据；真实生产记录不存在此类回收路径。 */
    private void cleanupUnifiedVoting() {
        List<Long> packageIds = jdbcTemplate.queryForList("""
                SELECT execution_package_id
                FROM t_repair_project_voting
                WHERE project_id IN (
                    SELECT project_id FROM t_repair_project WHERE project_name LIKE ?
                )
                """, Long.class, PROJECT_PREFIX + "%");
        for (Long packageId : packageIds) {
            List<Long> subjectIds = jdbcTemplate.queryForList(
                    "SELECT subject_id FROM t_voting_package_subject WHERE package_id = ?",
                    Long.class, packageId);
            for (Long subjectId : subjectIds) {
                jdbcTemplate.update("DELETE FROM t_voting_result WHERE subject_id = ?", subjectId);
            }
            jdbcTemplate.update("DELETE FROM t_online_ballot_submission_item WHERE submission_id IN "
                    + "(SELECT submission_id FROM t_online_ballot_submission WHERE package_id = ?)", packageId);
            jdbcTemplate.update("DELETE FROM t_online_ballot_submission WHERE package_id = ?", packageId);
            jdbcTemplate.update("DELETE FROM t_online_voting_acknowledgement WHERE package_id = ?", packageId);
            jdbcTemplate.update("DELETE FROM t_online_paper_assistance_request WHERE package_id = ?", packageId);
            jdbcTemplate.update("""
                    DELETE FROM t_paper_ballot_outcome
                    WHERE paper_ballot_id IN (
                        SELECT paper_ballot_id FROM t_paper_ballot WHERE package_id = ?
                    )
                    """, packageId);
            jdbcTemplate.update("""
                    DELETE FROM t_paper_ballot_entry_item
                    WHERE entry_id IN (
                        SELECT entry_id FROM t_paper_ballot_entry
                        WHERE paper_ballot_id IN (
                            SELECT paper_ballot_id FROM t_paper_ballot WHERE package_id = ?
                        )
                    )
                    """, packageId);
            jdbcTemplate.update("""
                    DELETE FROM t_paper_ballot_entry
                    WHERE paper_ballot_id IN (
                        SELECT paper_ballot_id FROM t_paper_ballot WHERE package_id = ?
                    )
                    """, packageId);
            jdbcTemplate.update("DELETE FROM t_paper_ballot WHERE package_id = ?", packageId);
            jdbcTemplate.update("DELETE FROM t_paper_voting_delivery WHERE package_id = ?", packageId);
            jdbcTemplate.update("DELETE FROM t_voting_ballot_record WHERE package_id = ?", packageId);
            for (Long subjectId : subjectIds) {
                jdbcTemplate.update("DELETE FROM t_vote_item WHERE subject_id = ?", subjectId);
                jdbcTemplate.update("""
                        DELETE FROM t_voting_denominator_item_snapshot
                        WHERE snapshot_id IN (
                            SELECT snapshot_id FROM t_voting_denominator_snapshot WHERE subject_id = ?
                        )
                        """, subjectId);
                jdbcTemplate.update(
                        "DELETE FROM t_voting_denominator_snapshot WHERE subject_id = ?", subjectId);
            }
            jdbcTemplate.update("DELETE FROM t_voting_delivery_record WHERE package_id = ?", packageId);
            jdbcTemplate.update("DELETE FROM t_repair_project_voting WHERE execution_package_id = ?", packageId);
            jdbcTemplate.update("DELETE FROM t_voting_package_subject WHERE package_id = ?", packageId);
            jdbcTemplate.update("""
                    UPDATE t_voting_execution_package
                    SET status = 'DRAFT', package_hash = NULL,
                        electorate_snapshot_id = NULL, frozen_at = NULL
                    WHERE package_id = ?
                    """, packageId);
            jdbcTemplate.update("DELETE FROM t_voting_electorate_snapshot WHERE package_id = ?", packageId);
            jdbcTemplate.update("DELETE FROM t_voting_execution_package WHERE package_id = ?", packageId);
            subjectIds.forEach(subjectId -> jdbcTemplate.update(
                    "DELETE FROM t_voting_subject WHERE subject_id = ?", subjectId));
        }
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
