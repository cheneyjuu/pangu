// 关联业务：验证草稿阶段可询价，且旧物业推荐不能越过决定/授权快照被显示为最终中选供应商。
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
class RepairProjectSourcingFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final long ACCOUNT_COMMITTEE_DIRECTOR = 999811L;
    private static final long USER_COMMITTEE_DIRECTOR = 800101L;
    private static final String PROJECT_PREFIX = "IT-维修点位询价-";
    private static final String SUPPLIER_PREFIX = "IT-维修点位询价供应商-";
    private static final String COMMITTEE_VICE_PHONE_PREFIX = "13986";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private RepairEvidenceObjectStorage objectStorage;

    private String propertyToken;
    private String committeeDirectorToken;
    private long buildingId;
    private Long temporaryViceAccountId;

    @BeforeEach
    void setUp() throws Exception {
        propertyToken = jwtTokenProvider.generateToken(
                ACCOUNT_PROPERTY_MANAGER, "SYS_USER", USER_PROPERTY_MANAGER, TENANT);
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
                        invocation.getArgument(2), "sourcing-etag"));
        when(objectStorage.createDownloadUrl(anyString(), any()))
                .thenReturn(URI.create("https://oss.example.test/repair-project-sourcing").toURL());
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
        if (temporaryViceAccountId != null) {
            jdbcTemplate.update("""
                    DELETE FROM t_committee_member_position
                    WHERE user_id IN (SELECT user_id FROM sys_user WHERE account_id = ?)
                    """, temporaryViceAccountId);
            jdbcTemplate.update("""
                    DELETE FROM sys_user_role
                    WHERE user_id IN (SELECT user_id FROM sys_user WHERE account_id = ?)
                    """, temporaryViceAccountId);
            jdbcTemplate.update("DELETE FROM sys_user WHERE account_id = ?", temporaryViceAccountId);
            jdbcTemplate.update("DELETE FROM t_account WHERE account_id = ?", temporaryViceAccountId);
        }
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
        jdbcTemplate.update("DELETE FROM t_account WHERE phone LIKE '13987%'");
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
    void draftQuoteUsesOptionalWorkPointAndHeaderTaxWhilePropertyCannotConfirmSelection() throws Exception {
        JsonNode created = data(postOk("/api/v1/admin/repair-projects", propertyToken, projectRequest()));
        long projectId = created.path("project").path("projectId").asLong();
        long workPointId = created.path("currentPlanWorkPoints").get(0).path("workPointId").asLong();
        long supplierDeptId = registerVerifiedSupplier();

        JsonNode invited = data(postOk(sourcingPath(projectId, "/invitations"), propertyToken, Map.of(
                "supplierDeptIds", List.of(supplierDeptId),
                "deadline", LocalDateTime.now().plusDays(3))));
        assertEquals(1, invited.path("invitations").size());

        String supplierToken = createSupplierIdentity(supplierDeptId);
        JsonNode opportunities = data(getOk(
                "/api/v1/supplier/repair-projects/quote-opportunities", supplierToken));
        JsonNode opportunity = opportunities.get(0);
        assertEquals(projectId, opportunity.path("projectId").asLong());
        assertEquals(workPointId, opportunity.path("workPoints").get(0).path("workPointId").asLong());
        assertTrue(opportunity.path("items").isMissingNode());

        long invitationId = opportunity.path("invitation").path("invitationId").asLong();
        long attachmentId = upload(projectId, "供应商报价原件.pdf", "quote", supplierToken);

        mockMvc.perform(post("/api/v1/supplier/repair-projects/" + projectId + "/quotes")
                        .header("Authorization", bearer(supplierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(quoteRequest(invitationId, attachmentId, workPointId + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("报价明细包含当前实施方案以外的维修点位")));

        JsonNode quote = data(postOk(
                "/api/v1/supplier/repair-projects/" + projectId + "/quotes",
                supplierToken, quoteRequest(invitationId, attachmentId, workPointId)));
        assertEquals("ONLINE_CONFIRMED", quote.path("confirmationStatus").asText());
        assertEquals(1000, quote.path("amountExcludingTax").asInt());
        assertEquals(9, quote.path("taxRate").asInt());
        assertEquals(90, quote.path("taxAmount").asInt());
        assertEquals(1090, quote.path("quoteAmount").asInt());
        assertEquals(workPointId, quote.path("quoteLines").get(0).path("workPointId").asLong());
        assertTrue(quote.path("quoteLines").get(1).path("workPointId").isNull());

        long propertySelectionEvidence = upload(projectId, "物业无权确认定商记录.pdf", "selection", propertyToken);
        mockMvc.perform(post(sourcingPath(projectId, "/selection"))
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "quoteId", quote.path("quoteId").asLong(),
                                "selectionRationale", "物业无权确认的测试请求",
                                "selectionEvidenceAttachmentId", propertySelectionEvidence))))
                .andExpect(status().isForbidden());

        long planId = jdbcTemplate.queryForObject("""
                SELECT plan_id FROM t_repair_plan_version
                WHERE project_id = ? AND status = 'DRAFT'
                """, Long.class, projectId);
        jdbcTemplate.update("""
                INSERT INTO t_repair_project_supplier_selection (
                    project_id, plan_id, tenant_id, quote_id, supplier_dept_id, supplier_name,
                    quote_amount, selection_method, selection_rationale,
                    insufficient_quote_reason, confirmed_by_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPETITIVE_QUOTATION',
                          '历史物业比价建议，不具备最终授权依据', NULL, ?)
                """, projectId, planId, TENANT, quote.path("quoteId").asLong(), supplierDeptId,
                quote.path("supplierName").asText(), quote.path("quoteAmount").decimalValue(),
                USER_PROPERTY_MANAGER);

        mockMvc.perform(get(sourcingPath(projectId, ""))
                        .header("Authorization", bearer(propertyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectionAuthorization.status", is("PENDING_AUTHORIZATION")))
                .andExpect(jsonPath("$.data.selectionAuthorization.blockingReason", is(
                        "请先完成责任与费用确认、实施方案公示和相关业主表决，再确定施工单位")))
                .andExpect(jsonPath("$.data.selectionAuthorization.currentActorMayConfirm", is(false)))
                .andExpect(jsonPath("$.data.selection", is(nullValue())));
    }

    @Test
    void currentCommitteeDirectorCanSealAuthorizedRuleAndConfirmSupplier() throws Exception {
        AuthorizedSelectionFixture fixture = createCommitteeApprovedFixture();

        JsonNode sealed = data(postOk(
                "/api/v1/admin/repair-projects/" + fixture.projectId() + "/building-governance/seal",
                committeeDirectorToken, Map.of(
                        "expectedProcessVersion", 0,
                        "sealedAttachmentId", fixture.sealedAttachmentId(),
                        "supplierSelectionAuthorization", Map.of(
                                "selectionMethod", "DIRECT_AWARD",
                                "evaluationRule", "AUTHORIZED_DIRECT_SELECTION",
                                "nonCompetitiveSelectionBasis", "已盖章授权文件明确该紧急抢修采用直接定商"))));
        assertEquals("AUTHORIZED", sealed.path("process").path("status").asText());
        long governanceBasisId = jdbcTemplate.queryForObject("""
                SELECT basis_id FROM t_repair_governance_basis
                WHERE project_id = ? AND plan_id = ? AND status = 'ACTIVE'
                """, Long.class, fixture.projectId(), fixture.planId());
        assertEquals("DIRECT_AWARD", jdbcTemplate.queryForObject("""
                SELECT approved_supplier_selection_method FROM t_repair_governance_basis
                WHERE basis_id = ?
                """, String.class, governanceBasisId));
        assertEquals("AUTHORIZED_DIRECT_SELECTION", jdbcTemplate.queryForObject("""
                SELECT approved_supplier_evaluation_rule FROM t_repair_governance_basis
                WHERE basis_id = ?
                """, String.class, governanceBasisId));

        long supplierDeptId = registerVerifiedSupplier();
        long selectionEvidenceAttachmentId = insertProjectAttachment(
                fixture.projectId(), "业委会定商记录.pdf", "selection-evidence-" + System.nanoTime());
        long quoteId = insertConfirmedQuote(
                fixture, supplierDeptId, 900, "已确认的紧急维修报价原件");

        JsonNode selection = data(postOk(sourcingPath(fixture.projectId(), "/selection"), committeeDirectorToken,
                Map.of(
                        "quoteId", quoteId,
                        "selectionRationale", "根据盖章授权文件和本次评审记录确认该施工单位。",
                        "selectionEvidenceAttachmentId", selectionEvidenceAttachmentId)));
        assertEquals("AUTHORIZED", selection.path("selectionAuthorization").path("status").asText());
        assertTrue(selection.path("selectionAuthorization").path("currentActorMayConfirm").asBoolean());
        assertEquals("DIRECT_AWARD", selection.path("selection").path("selectionMethod").asText());
        assertEquals("AUTHORIZED_DIRECT_SELECTION",
                selection.path("selection").path("selectionEvaluationRule").asText());
        assertEquals(selectionEvidenceAttachmentId,
                selection.path("selection").path("selectionEvidenceAttachmentId").asLong());
        assertEquals(governanceBasisId, selection.path("selection").path("governanceBasisId").asLong());
        assertEquals(USER_COMMITTEE_DIRECTOR, jdbcTemplate.queryForObject("""
                SELECT confirmed_by_user_id FROM t_repair_project_supplier_selection
                WHERE project_id = ? AND quote_id = ?
                """, Long.class, fixture.projectId(), quoteId));
    }

    @Test
    void currentViceDirectorCanConfirmWhenDifferentFromEarlierApprover() throws Exception {
        AuthorizedSelectionFixture fixture = createCommitteeApprovedFixture();
        data(postOk(
                "/api/v1/admin/repair-projects/" + fixture.projectId() + "/building-governance/seal",
                committeeDirectorToken, Map.of(
                        "expectedProcessVersion", 0,
                        "sealedAttachmentId", fixture.sealedAttachmentId(),
                        "supplierSelectionAuthorization", Map.of(
                                "selectionMethod", "DIRECT_AWARD",
                                "evaluationRule", "AUTHORIZED_DIRECT_SELECTION",
                                "nonCompetitiveSelectionBasis", "盖章授权文件允许由当前在任业委会确认直接定商"))));
        long supplierDeptId = registerVerifiedSupplier();
        long quoteId = insertConfirmedQuote(
                fixture, supplierDeptId, 900, "供副主任确认的有效报价原件");
        long evidenceId = insertProjectAttachment(
                fixture.projectId(), "副主任定商评审记录.pdf", "vice-selection-evidence-" + System.nanoTime());
        CommitteeViceIdentity vice = createCurrentViceDirectorIdentity();
        assertTrue(USER_COMMITTEE_DIRECTOR != vice.userId());

        mockMvc.perform(get(sourcingPath(fixture.projectId(), ""))
                        .header("Authorization", bearer(vice.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectionAuthorization.currentActorMayConfirm", is(true)));

        data(postOk(sourcingPath(fixture.projectId(), "/selection"), vice.token(), Map.of(
                "quoteId", quoteId,
                "selectionRationale", "当前在任副主任根据已盖章授权和本次评审记录确认施工单位。",
                "selectionEvidenceAttachmentId", evidenceId)));
        assertEquals(vice.userId(), jdbcTemplate.queryForObject("""
                SELECT confirmed_by_user_id FROM t_repair_project_supplier_selection
                WHERE project_id = ? AND quote_id = ?
                """, Long.class, fixture.projectId(), quoteId));
    }

    @Test
    void lowestCompliantQuoteHonorsSealedNumericGatesAndRejectsHigherQuote() throws Exception {
        AuthorizedSelectionFixture insufficientFixture = createCommitteeApprovedFixture();
        long insufficientSupplier = registerVerifiedSupplier();
        insertInitialInvitation(insufficientFixture, insufficientSupplier);
        long insufficientQuoteId = insertConfirmedQuote(
                insufficientFixture, insufficientSupplier, 800, "仅有一家受邀供应商的有效报价");
        long insufficientEvidenceId = insertProjectAttachment(
                insufficientFixture.projectId(), "未达到邀价数量的评审记录.pdf", "insufficient-evidence-" + System.nanoTime());
        sealLowestCompliantQuoteAuthorization(insufficientFixture);

        mockMvc.perform(post(sourcingPath(insufficientFixture.projectId(), "/selection"))
                        .header("Authorization", bearer(committeeDirectorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "quoteId", insufficientQuoteId,
                                "selectionRationale", "已完成资料核验，但授权快照要求的邀请数量尚未达到。",
                                "selectionEvidenceAttachmentId", insufficientEvidenceId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("实施方案要求邀请的施工单位数量尚未达到")));

        AuthorizedSelectionFixture competitiveFixture = createCommitteeApprovedFixture();
        long lowSupplier = registerVerifiedSupplier();
        long highSupplier = registerVerifiedSupplier();
        insertInitialInvitation(competitiveFixture, lowSupplier);
        insertInitialInvitation(competitiveFixture, highSupplier);
        long lowQuoteId = insertConfirmedQuote(
                competitiveFixture, lowSupplier, 800, "最低有效报价原件");
        long highQuoteId = insertConfirmedQuote(
                competitiveFixture, highSupplier, 900, "高于最低价的有效报价原件");
        long selectionEvidenceId = insertProjectAttachment(
                competitiveFixture.projectId(), "两家供应商比价评审记录.pdf", "lowest-evidence-" + System.nanoTime());
        JsonNode sealed = sealLowestCompliantQuoteAuthorization(competitiveFixture);
        assertEquals("AUTHORIZED", sealed.path("process").path("status").asText());
        long governanceBasisId = jdbcTemplate.queryForObject("""
                SELECT basis_id FROM t_repair_governance_basis
                WHERE project_id = ? AND plan_id = ? AND status = 'ACTIVE'
                """, Long.class, competitiveFixture.projectId(), competitiveFixture.planId());
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT minimum_invited_supplier_count FROM t_repair_governance_basis
                WHERE basis_id = ?
                """, Integer.class, governanceBasisId));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT minimum_valid_quote_count FROM t_repair_governance_basis
                WHERE basis_id = ?
                """, Integer.class, governanceBasisId));

        mockMvc.perform(post(sourcingPath(competitiveFixture.projectId(), "/selection"))
                        .header("Authorization", bearer(committeeDirectorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "quoteId", highQuoteId,
                                "selectionRationale", "错误地尝试确认高于当前最低合格报价的供应商。",
                                "selectionEvidenceAttachmentId", selectionEvidenceId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("当前采用最低合格报价规则，请先说明或处理金额更低的合格报价")));

        JsonNode selection = data(postOk(
                sourcingPath(competitiveFixture.projectId(), "/selection"), committeeDirectorToken, Map.of(
                        "quoteId", lowQuoteId,
                        "selectionRationale", "在盖章授权的两家有效报价中确认最低合格报价。",
                        "selectionEvidenceAttachmentId", selectionEvidenceId)));
        assertEquals("LOWEST_COMPLIANT_QUOTE",
                selection.path("selection").path("selectionEvaluationRule").asText());
        assertEquals(lowQuoteId, selection.path("selection").path("quoteId").asLong());
    }

    /**
     * 夹具直接落库的是盖章前已经完成的决定、审价和主任确认事实；不把该测试夹具当作新草稿锁定路径。
     */
    private AuthorizedSelectionFixture createCommitteeApprovedFixture() {
        long projectId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_project (
                    tenant_id, project_name, workflow_type, scope_type, building_id,
                    fund_source, governance_path, status, created_by_account_id, created_by_user_id
                ) VALUES (?, ?, 'BUILDING_REPAIR', 'BUILDING', ?,
                          'BUILDING_MAINTENANCE_FUND', 'BUILDING_REPAIR_DECISION',
                          'GOVERNANCE_IN_PROGRESS', ?, ?)
                RETURNING project_id
                """, Long.class, TENANT, PROJECT_PREFIX + "授权定商-" + System.nanoTime(), buildingId,
                ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        long planId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_plan_version (
                    project_id, tenant_id, version_no, plan_description, budget_total,
                    status, snapshot_hash, created_by_account_id, created_by_user_id,
                    locked_by_user_id, locked_at
                ) VALUES (?, ?, 1, '已完成楼栋决定和审价的锁定实施方案', 1000.00,
                          'LOCKED', ?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING plan_id
                """, Long.class, projectId, TENANT, "a".repeat(64), ACCOUNT_PROPERTY_MANAGER,
                USER_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        jdbcTemplate.update("UPDATE t_repair_project SET active_plan_id = ? WHERE project_id = ?", planId, projectId);
        long officialDocumentAttachmentId = insertProjectAttachment(
                projectId, "物业正式报审文件.pdf", "official-" + System.nanoTime());
        long sealedAttachmentId = insertProjectAttachment(
                projectId, "业委会盖章授权文件.pdf", "sealed-" + System.nanoTime());
        long policySnapshotId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_decision_policy_snapshot (
                    project_id, plan_id, tenant_id, rule_document_attachment_id,
                    rule_version, rule_hash, decision_channel, delivery_rule,
                    non_response_rule, status, created_by_user_id
                ) VALUES (?, ?, ?, ?, 'IT-1', ?, 'WECHAT', '已按登记业主范围送达',
                          'NOT_PARTICIPATED', 'LOCKED', ?)
                RETURNING policy_snapshot_id
                """, Long.class, projectId, planId, TENANT, officialDocumentAttachmentId,
                "b".repeat(64), USER_COMMITTEE_DIRECTOR);
        long decisionId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_local_decision (
                    work_order_id, project_id, plan_id, tenant_id, building_id, scope_type,
                    decision_channel, scope_label, total_owner_count, total_area,
                    participated_owner_count, participated_area, agree_owner_count, agree_area,
                    evidence_attachment_hash, printed_and_attached, result
                ) VALUES (NULL, ?, ?, ?, ?, 'BUILDING',
                          'WECHAT', '测试楼栋费用承担范围内业主', 1, 1.00,
                          1, 1.00, 1, 1.00, ?, 1, 'PASSED')
                RETURNING decision_id
                """, Long.class, projectId, planId, TENANT, buildingId, "c".repeat(64));
        jdbcTemplate.update("""
                INSERT INTO t_repair_building_process (
                    project_id, plan_id, tenant_id, policy_snapshot_id, decision_id, status,
                    official_document_attachment_id, review_mode, reviewed_amount,
                    price_review_report_attachment_id, price_review_conclusion,
                    price_reviewed_by_user_id, price_reviewed_at, approved_by_user_id,
                    approver_position, approval_opinion, approved_at, process_version
                ) VALUES (?, ?, ?, ?, ?, 'COMMITTEE_APPROVED',
                          ?, 'INTERNAL_PRICE_REVIEW', 1000.00,
                          ?, 'APPROVED', ?, CURRENT_TIMESTAMP, ?,
                          'DIRECTOR', '已确认按盖章授权文件执行', CURRENT_TIMESTAMP, 0)
                """, projectId, planId, TENANT, policySnapshotId, decisionId,
                officialDocumentAttachmentId, officialDocumentAttachmentId, USER_COMMITTEE_DIRECTOR,
                USER_COMMITTEE_DIRECTOR);
        return new AuthorizedSelectionFixture(projectId, planId, sealedAttachmentId);
    }

    private long insertProjectAttachment(long projectId, String fileName, String objectName) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_project_attachment (
                    project_id, tenant_id, object_key, original_file_name, content_type,
                    file_size, etag, sha256, uploaded_by_account_id, uploaded_by_user_id
                ) VALUES (?, ?, ?, ?, 'application/pdf',
                          1, 'selection-flow-etag', ?, ?, ?)
                RETURNING attachment_id
                """, Long.class, projectId, TENANT, "it/sourcing/" + objectName + ".pdf", fileName,
                "e".repeat(64), ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
    }

    /**
     * 测试夹具补录锁定方案前已发出的初始邀价，用于验证盖章授权中真实数量门槛的执行。
     */
    private void insertInitialInvitation(AuthorizedSelectionFixture fixture, long supplierDeptId) {
        jdbcTemplate.update("""
                INSERT INTO t_repair_project_quote_invitation (
                    project_id, plan_id, tenant_id, supplier_dept_id, invited_by_user_id,
                    deadline, status, invitation_round, invitation_type
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '7 days',
                          'SUBMITTED', 1, 'INITIAL')
                """, fixture.projectId(), fixture.planId(), TENANT, supplierDeptId, USER_PROPERTY_MANAGER);
    }

    /**
     * 测试夹具补录已有报价原件和核验事实，最终定商仍必须通过真实管理端接口执行。
     */
    private long insertConfirmedQuote(
            AuthorizedSelectionFixture fixture,
            long supplierDeptId,
            int quoteAmount,
            String quoteSummary) {
        long quoteAttachmentId = insertProjectAttachment(
                fixture.projectId(), "供应商有效报价.pdf", "quote-" + System.nanoTime());
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_project_supplier_quote (
                    project_id, plan_id, tenant_id, supplier_dept_id, supplier_name,
                    amount_excluding_tax, tax_rate, tax_amount, quote_amount,
                    quote_summary, attachment_id, attachment_hash,
                    submitted_by_user_id, submitted_by_role_key, submission_source,
                    confirmation_status, original_source, construction_period_days, warranty_days,
                    original_amount_confirmed, quote_status, revision_no
                ) VALUES (?, ?, ?, ?, ?,
                          ?, 0.000, 0.00, ?,
                          ?, ?, ?,
                          ?, 'PROPERTY_MANAGER', 'PROPERTY_ENTRY',
                          'OFFLINE_EVIDENCE_VERIFIED', 'OFFLINE_ORIGINAL', 7, 365,
                          TRUE, 'ACTIVE', 1)
                RETURNING quote_id
                """, Long.class, fixture.projectId(), fixture.planId(), TENANT, supplierDeptId,
                jdbcTemplate.queryForObject(
                        "SELECT legal_name FROM t_supplier_org_profile WHERE supplier_dept_id = ?",
                        String.class, supplierDeptId), quoteAmount, quoteAmount, quoteSummary, quoteAttachmentId,
                "d".repeat(64), USER_PROPERTY_MANAGER);
    }

    private JsonNode sealLowestCompliantQuoteAuthorization(AuthorizedSelectionFixture fixture) throws Exception {
        return data(postOk(
                "/api/v1/admin/repair-projects/" + fixture.projectId() + "/building-governance/seal",
                committeeDirectorToken, Map.of(
                        "expectedProcessVersion", 0,
                        "sealedAttachmentId", fixture.sealedAttachmentId(),
                        "supplierSelectionAuthorization", Map.of(
                                "selectionMethod", "COMPETITIVE_QUOTATION",
                                "evaluationRule", "LOWEST_COMPLIANT_QUOTE",
                                "minimumInvitedSupplierCount", 2,
                                "minimumValidQuoteCount", 2))));
    }

    private Map<String, Object> projectRequest() {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("businessName", "楼栋公共区域排水泵维修点");
        point.put("buildingId", buildingId);
        point.put("locationType", "COMMON_AREA");
        point.put("commonAreaName", "地下车库排水泵房");
        point.put("component", "排水泵及控制柜");
        point.put("specificPart", "泵组和控制柜连接部位");
        point.put("symptom", "设备运行异常并伴随排水不畅");
        point.put("causeStatus", "PENDING_INVESTIGATION");
        point.put("proposedMeasure", "在完成专业勘验后更换故障部件并恢复排水能力");
        point.put("linkedWorkOrderIds", List.of());

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planDescription", "本草稿记录维修点位和询价边界，报价行与点位不强制一一对应。");
        plan.put("budgetTotal", 1090);
        plan.put("workPoints", List.of(point));
        plan.put("attachments", List.of());
        return Map.of(
                "projectName", PROJECT_PREFIX + System.nanoTime(),
                "scopeType", "BUILDING",
                "buildingId", buildingId,
                "plan", plan);
    }

    private Map<String, Object> quoteRequest(long invitationId, long attachmentId, long workPointId) {
        return Map.of(
                "invitationId", invitationId,
                "quoteAmount", 1090,
                "taxRate", 9,
                "quoteSummary", "报价原件已核对，税率以报价单头为准",
                "attachmentId", attachmentId,
                "constructionPeriodDays", 10,
                "warrantyDays", 365,
                "originalAmountConfirmed", true,
                "quoteLines", List.of(
                        Map.of(
                                "workPointId", workPointId,
                                "itemName", "排水泵维修材料和人工",
                                "lineType", "CONSTRUCTION_MEASURE",
                                "workDescription", "按勘验结论维修泵组和控制柜",
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
    }

    private long registerVerifiedSupplier() throws Exception {
        String supplierName = SUPPLIER_PREFIX + System.nanoTime();
        JsonNode supplier = data(postOk("/api/v1/admin/supplier-organizations", propertyToken,
                Map.of("legalName", supplierName)));
        long supplierDeptId = supplier.isNumber() ? supplier.asLong() : supplier.path("supplierDeptId").asLong();
        mockMvc.perform(post("/api/v1/admin/supplier-organizations/" + supplierDeptId + "/manual-verifications")
                        .header("Authorization", bearer(propertyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "unifiedSocialCreditCode", "91310000" + String.format("%010d", supplierDeptId),
                                "sourceCode", "GSXT_WEB",
                                "verificationResult", "PASSED",
                                "remark", "测试中已核对企业登记信息"))))
                .andExpect(status().isCreated());
        return supplierDeptId;
    }

    /**
     * 副主任与原审批人分离，验证最终定商权限按当前在任职务和治理权限判断，而非固定到历史审批账号。
     */
    private CommitteeViceIdentity createCurrentViceDirectorIdentity() {
        String phone = COMMITTEE_VICE_PHONE_PREFIX + String.format("%06d",
                Math.floorMod(System.nanoTime(), 1_000_000));
        long accountId = jdbcTemplate.queryForObject("""
                INSERT INTO t_account (phone, real_name, real_name_verified, status, last_active_identity_type)
                VALUES (?, '测试业委会副主任', 1, 1, 'SYS_USER')
                RETURNING account_id
                """, Long.class, phone);
        temporaryViceAccountId = accountId;
        long committeeDeptId = jdbcTemplate.queryForObject(
                "SELECT dept_id FROM sys_user WHERE user_id = ?", Long.class, USER_COMMITTEE_DIRECTOR);
        long userId = jdbcTemplate.queryForObject("""
                INSERT INTO sys_user (account_id, dept_id, user_name, nick_name, status)
                VALUES (?, ?, ?, '测试业委会副主任', '0')
                RETURNING user_id
                """, Long.class, accountId, committeeDeptId, "committee-vice-" + accountId);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id, effective_data_scope, granted_by)
                SELECT ?, role_id, 'ALL_COMMUNITY', ?
                FROM sys_role WHERE role_key = 'COMMITTEE_MEMBER'
                """, userId, USER_COMMITTEE_DIRECTOR);
        jdbcTemplate.update("""
                INSERT INTO t_committee_member_position (tenant_id, user_id, position, status)
                VALUES (?, ?, 'VICE_DIRECTOR', 1)
                """, TENANT, userId);
        return new CommitteeViceIdentity(
                userId, jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, TENANT));
    }

    private String createSupplierIdentity(long supplierDeptId) {
        String phone = "13987" + String.format("%06d", supplierDeptId % 1_000_000);
        long accountId = jdbcTemplate.queryForObject("""
                INSERT INTO t_account (phone, real_name, real_name_verified, status, last_active_identity_type)
                VALUES (?, '项目报价经办人', 1, 1, 'SYS_USER')
                RETURNING account_id
                """, Long.class, phone);
        long userId = jdbcTemplate.queryForObject("""
                INSERT INTO sys_user (account_id, dept_id, user_name, nick_name, status)
                VALUES (?, ?, ?, '项目报价经办人', '0')
                RETURNING user_id
                """, Long.class, accountId, supplierDeptId, "project-quote-" + supplierDeptId);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id, effective_data_scope, granted_by)
                SELECT ?, role_id, 'ORG_ONLY', ?
                FROM sys_role WHERE role_key = 'SERVICE_PROVIDER_STAFF'
                """, userId, USER_PROPERTY_MANAGER);
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, null);
    }

    private long upload(long projectId, String fileName, String content, String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "application/pdf", content.getBytes(StandardCharsets.UTF_8));
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

    private String getOk(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode data(String response) throws Exception {
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

    private record AuthorizedSelectionFixture(long projectId, long planId, long sealedAttachmentId) {
    }

    private record CommitteeViceIdentity(long userId, String token) {
    }
}
