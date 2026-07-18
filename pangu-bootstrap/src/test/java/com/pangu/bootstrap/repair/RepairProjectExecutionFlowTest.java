// 关联业务：验证已进入实施状态的工程在 MyBatis 中只写维修点位引用和结算单头税率，不把测试夹具当作新草稿可绕过可信快照的流程。
package com.pangu.bootstrap.repair;

import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.EvidenceStage;
import com.pangu.domain.model.repair.RepairProjectExecution.ExecutionRecord;
import com.pangu.domain.model.repair.RepairProjectExecution.MaterialInspection;
import com.pangu.domain.model.repair.RepairProjectExecution.Settlement;
import com.pangu.domain.model.repair.RepairProjectExecution.SettlementItem;
import com.pangu.domain.model.repair.RepairProjectExecution.SettlementStatus;
import com.pangu.domain.model.repair.RepairProjectExecution.VerificationStatus;
import com.pangu.domain.repository.RepairProjectExecutionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class RepairProjectExecutionFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final String PROJECT_PREFIX = "IT-维修点位实施映射-";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RepairProjectExecutionRepository executionRepository;

    private long buildingId;

    @BeforeEach
    void setUp() {
        buildingId = jdbcTemplate.queryForObject("""
                SELECT building_id
                FROM c_owner_property
                WHERE tenant_id = ? AND account_status = 1
                GROUP BY building_id
                ORDER BY COUNT(DISTINCT room_id) DESC, building_id
                LIMIT 1
                """, Long.class, TENANT);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM t_repair_project WHERE project_name LIKE ?", PROJECT_PREFIX + "%");
    }

    @Test
    void persistsWorkPointReferencesAndHeaderTaxWithoutWritingLegacyItemColumns() {
        ExecutionFixture fixture = createExecutionPersistenceFixture();

        ExecutionRecord execution = executionRepository.insertExecutionRecord(new ExecutionRecord(
                null, fixture.projectId(), fixture.planId(), fixture.workPointId(), TENANT,
                EvidenceStage.DURING_CONSTRUCTION, "玻璃安装过程留痕", LocalDateTime.now().minusMinutes(1),
                USER_PROPERTY_MANAGER, null, VerificationStatus.PENDING, null, null, List.of(), null));
        assertNotNull(execution.recordId());
        assertEquals(fixture.workPointId(), jdbcTemplate.queryForObject("""
                SELECT work_point_id FROM t_repair_execution_record WHERE record_id = ?
                """, Long.class, execution.recordId()));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM t_repair_execution_record
                WHERE record_id = ? AND item_id IS NOT NULL
                """, execution.recordId()));

        MaterialInspection material = executionRepository.insertMaterialInspection(new MaterialInspection(
                null, fixture.projectId(), fixture.planId(), null, TENANT,
                "密封胶", "测试品牌", "M-100", "耐候型", new BigDecimal("2.000"), "支",
                "测试生产商", fixture.attachmentId(), List.of(), USER_PROPERTY_MANAGER,
                VerificationStatus.PENDING, null, null, null, null));
        assertNotNull(material.inspectionId());
        assertNull(jdbcTemplate.queryForObject("""
                SELECT work_point_id FROM t_repair_material_inspection WHERE inspection_id = ?
                """, Long.class, material.inspectionId()));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM t_repair_material_inspection
                WHERE inspection_id = ? AND item_id IS NOT NULL
                """, material.inspectionId()));

        Settlement submitted = executionRepository.insertSettlement(new Settlement(
                null, fixture.projectId(), fixture.planId(), fixture.contractId(), TENANT, 1,
                SettlementStatus.SUBMITTED, new BigDecimal("1000.00"), new BigDecimal("9.000"),
                new BigDecimal("90.00"), new BigDecimal("1090.00"), fixture.attachmentId(),
                USER_PROPERTY_MANAGER, null, null, null, null, List.of()));
        executionRepository.insertSettlementItems(submitted.settlementId(), List.of(
                new SettlementItem(null, null, fixture.workPointId(), new BigDecimal("1.000"), "项",
                        new BigDecimal("900.00"), new BigDecimal("900.00"), "按现场工程量确认"),
                new SettlementItem(null, null, null, new BigDecimal("1.000"), "项",
                        new BigDecimal("100.00"), new BigDecimal("100.00"), "通用清运费用")));

        Settlement persisted = executionRepository.findActiveSettlement(fixture.projectId(), TENANT).orElseThrow();
        assertEquals(0, new BigDecimal("9").compareTo(persisted.taxRate()));
        assertEquals(0, new BigDecimal("90").compareTo(persisted.taxAmount()));
        assertEquals(0, new BigDecimal("1090").compareTo(persisted.totalAmount()));
        assertEquals(2, persisted.items().size());
        assertEquals(fixture.workPointId(), persisted.items().get(0).workPointId());
        assertNull(persisted.items().get(1).workPointId());
        assertEquals(0, count("""
                SELECT COUNT(*) FROM t_repair_project_settlement_item
                WHERE settlement_id = ? AND project_item_id IS NOT NULL
                """, submitted.settlementId()));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM t_repair_project_settlement_item
                WHERE settlement_id = ?
                  AND (tax_rate IS NOT NULL OR tax_amount IS NOT NULL OR amount_including_tax IS NOT NULL)
                """, submitted.settlementId()));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM t_repair_project_settlement_item
                WHERE settlement_id = ? AND work_point_id = ?
                """, submitted.settlementId(), fixture.workPointId()));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM t_repair_project_settlement_item
                WHERE settlement_id = ? AND work_point_id IS NULL
                """, submitted.settlementId()));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM t_repair_project_item WHERE project_id = ?
                """, fixture.projectId()));
    }

    private ExecutionFixture createExecutionPersistenceFixture() {
        long projectId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_project (
                    tenant_id, project_name, workflow_type, scope_type, building_id,
                    fund_source, governance_path, status, created_by_account_id, created_by_user_id
                ) VALUES (?, ?, 'BUILDING_REPAIR', 'BUILDING', ?,
                          'BUILDING_MAINTENANCE_FUND', 'BUILDING_REPAIR_DECISION', 'IN_PROGRESS', ?, ?)
                RETURNING project_id
                """, Long.class, TENANT, PROJECT_PREFIX + System.nanoTime(), buildingId,
                ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        long planId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_plan_version (
                    project_id, tenant_id, version_no, plan_description, budget_total,
                    fund_source, allocation_rule_type, allocation_rule_description,
                    supplier_selection_method, supplier_selection_reason,
                    construction_management_requirements, evidence_requirements_json,
                    safety_requirements, acceptance_method, required_acceptance_roles_json,
                    affected_owner_scope_description, minimum_affected_owner_acceptors,
                    affected_owner_pass_rule, affected_owner_approval_ratio, settlement_method,
                    planned_start_date, planned_completion_date, warranty_days, governance_path,
                    price_review_required, payment_milestones_json, status, snapshot_hash,
                    created_by_account_id, created_by_user_id, locked_by_user_id, locked_at
                ) VALUES (?, ?, 1, '已授权工程的实施方案', 2000.00,
                          'BUILDING_MAINTENANCE_FUND', 'BY_BUILDING_AREA', '历史已核定费用范围',
                          'COMPETITIVE_QUOTATION', '已完成原有定商程序',
                          '按已授权方案实施', '[]'::JSONB,
                          '按现场安全措施施工', '按已授权验收规则执行', '[]'::JSONB,
                          '既有决定范围内业主', 1, 'ALL', 1.0000, 'ACTUAL_QUANTITY',
                          CURRENT_DATE, CURRENT_DATE + 30, 365, 'BUILDING_REPAIR_DECISION',
                          0, '[]'::JSONB, 'LOCKED', ?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING plan_id
                """, Long.class, projectId, TENANT, "a".repeat(64), ACCOUNT_PROPERTY_MANAGER,
                USER_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        jdbcTemplate.update("""
                UPDATE t_repair_project
                SET active_plan_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ?
                """, planId, projectId);
        long workPointId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_work_point (
                    project_id, plan_id, tenant_id, business_name, building_id,
                    location_type, common_area_name, symptom, cause_status, proposed_measure, sort_order
                ) VALUES (?, ?, ?, '楼栋大厅玻璃', ?,
                          'COMMON_AREA', '大厅东侧玻璃', '玻璃破损并存在渗水风险',
                          'PENDING_INVESTIGATION', '更换破损玻璃并恢复密封', 1)
                RETURNING work_point_id
                """, Long.class, projectId, planId, TENANT, buildingId);
        long attachmentId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_project_attachment (
                    project_id, tenant_id, object_key, original_file_name, content_type,
                    file_size, etag, sha256, uploaded_by_account_id, uploaded_by_user_id
                ) VALUES (?, ?, ?, '实施结算依据.pdf', 'application/pdf',
                          1, 'execution-mapping-etag', ?, ?, ?)
                RETURNING attachment_id
                """, Long.class, projectId, TENANT, "it/execution-mapping-" + System.nanoTime() + ".pdf",
                "b".repeat(64), ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
        long contractId = jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_contract (
                    work_order_id, project_id, plan_id, tenant_id, supplier_name, contract_amount,
                    repair_scope_hash, fund_source, signing_method, contract_file_hash,
                    contract_attachment_id, status, created_by_user_id, effective_at
                ) VALUES (NULL, ?, ?, ?, '已核验施工单位', 2000.00,
                          ?, 'BUILDING_MAINTENANCE_FUND', 'OFFLINE', ?,
                          ?, 'EFFECTIVE', ?, CURRENT_TIMESTAMP)
                RETURNING contract_id
                """, Long.class, projectId, planId, TENANT, "c".repeat(64), "d".repeat(64),
                attachmentId, USER_PROPERTY_MANAGER);
        return new ExecutionFixture(projectId, planId, workPointId, attachmentId, contractId);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private record ExecutionFixture(
            long projectId, long planId, long workPointId, long attachmentId, long contractId) {
    }
}
