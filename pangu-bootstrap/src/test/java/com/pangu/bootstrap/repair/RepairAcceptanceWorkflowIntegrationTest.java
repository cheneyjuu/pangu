// 关联业务：验证全小区公共维修必须完成业委会负责人在线同意、用印和专业共同签署。
package com.pangu.bootstrap.repair;

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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepairAcceptanceWorkflowIntegrationTest {

    private static final long TENANT = 10001L;
    private static final long ACC_PROPERTY_STAFF = 999822L;
    private static final long USR_PROPERTY_STAFF = 800202L;
    private static final long ACC_PROPERTY_MANAGER = 999821L;
    private static final long USR_PROPERTY_MANAGER = 800201L;
    private static final long ACC_COMMITTEE_MEMBER = 999813L;
    private static final long USR_COMMITTEE_MEMBER = 800103L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clean() {
        jdbcTemplate.update("""
                DELETE FROM t_repair_acceptance_party
                WHERE acceptance_id IN (
                    SELECT acceptance.acceptance_id
                    FROM t_repair_acceptance acceptance
                    JOIN t_repair_work_order work_order ON work_order.work_order_id = acceptance.work_order_id
                    WHERE work_order.title LIKE 'IT-验收-%'
                )
                """);
        jdbcTemplate.update("""
                DELETE FROM t_committee_seal_usage
                WHERE business_type = 'REPAIR_ACCEPTANCE'
                  AND business_id IN (
                    SELECT acceptance.acceptance_id
                    FROM t_repair_acceptance acceptance
                    JOIN t_repair_work_order work_order ON work_order.work_order_id = acceptance.work_order_id
                    WHERE work_order.title LIKE 'IT-验收-%'
                  )
                """);
        jdbcTemplate.update("DELETE FROM t_repair_work_order WHERE title LIKE 'IT-验收-%'");
        jdbcTemplate.update("""
                UPDATE t_committee_member_position
                SET position = 'MEMBER', update_time = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND user_id = ? AND status = 1
                """, TENANT, USR_COMMITTEE_MEMBER);
    }

    @Test
    void communityPublicRepairRequiresViceDirectorSealAndProfessionalCosign() throws Exception {
        String staffToken = token(ACC_PROPERTY_STAFF, USR_PROPERTY_STAFF);
        String managerToken = token(ACC_PROPERTY_MANAGER, USR_PROPERTY_MANAGER);
        String viceDirectorToken = token(ACC_COMMITTEE_MEMBER, USR_COMMITTEE_MEMBER);
        jdbcTemplate.update("""
                UPDATE t_committee_member_position
                SET position = 'VICE_DIRECTOR', update_time = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND user_id = ? AND status = 1
                """, TENANT, USR_COMMITTEE_MEMBER);

        String created = mockMvc.perform(post("/api/v1/admin/repair-work-orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "publicAreaScope", "COMMUNITY",
                                "locationText", "小区主干道",
                                "title", "IT-验收-全小区道路维修-" + System.nanoTime(),
                                "description", "全体业主共同使用的道路维修",
                                "category", "PUBLIC_FACILITY"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long workOrderId = objectMapper.readTree(created).path("data").path("workOrderId").asLong();

        long surveyImageId = readyAttachment(
                workOrderId, "SURVEY_IMAGE", ACC_PROPERTY_STAFF, "survey-image-etag");
        action(staffToken, workOrderId, "submit-inspection", Map.of(
                "publicAreaScope", "COMMUNITY",
                "locationText", "小区主干道",
                "surveySummary", "道路破损，需整体翻修",
                "riskLevel", "MEDIUM",
                "evidenceImageAttachmentIds", List.of(surveyImageId),
                "remark", "物业完成踏勘"))
                .andExpect(jsonPath("$.data.status", is("SURVEY_COMPLETED")));

        action(staffToken, workOrderId, "submit-plan", Map.of(
                "planBudget", new BigDecimal("120000.00"),
                "publicCeilingPrice", new BigDecimal("118000.00"),
                "fundSource", "COMMUNITY_MAINTENANCE_FUND",
                "remark", "全小区维修方案确认"))
                .andExpect(jsonPath("$.data.status", is("PLAN_SUBMITTED")));
        assertEquals("COMMUNITY_PUBLIC_REPAIR", jdbcTemplate.queryForObject("""
                SELECT workflow_type
                FROM t_repair_acceptance_policy_snapshot
                WHERE work_order_id = ? AND status = 'ACTIVE'
                """, String.class, workOrderId));

        // 本测试聚焦验收切片；合同与业主大会链路由各自流程测试覆盖。
        jdbcTemplate.update("""
                UPDATE t_repair_work_order
                SET status = 'CONTRACT_EFFECTIVE', version = version + 1
                WHERE work_order_id = ?
                """, workOrderId);
        action(staffToken, workOrderId, "start-work", Map.of("remark", "施工单位进场"))
                .andExpect(jsonPath("$.data.status", is("IN_PROGRESS")));
        action(staffToken, workOrderId, "submit-acceptance", Map.of("remark", "施工单位提交完工验收"))
                .andExpect(jsonPath("$.data.status", is("PENDING_ACCEPTANCE")));

        actionRaw(viceDirectorToken, workOrderId, "accept-completed", Map.of("remark", "缺少验收动作"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("业委会主任或副主任尚未在线同意")));

        long sealedDocumentId = readyAttachment(
                workOrderId, "ACCEPTANCE_SEALED_DOCUMENT", ACC_COMMITTEE_MEMBER,
                "acceptance-sealed-etag");
        actionRaw(viceDirectorToken, workOrderId, "acceptance-seal", Map.of(
                "sealedAttachmentId", sealedDocumentId,
                "remark", "错误尝试在负责人审批前用印"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("业委会主任或副主任尚未在线同意，禁止办理验收用印")));

        action(viceDirectorToken, workOrderId, "acceptance-records", Map.of(
                "participantType", "COMMITTEE_EXECUTIVE_APPROVER",
                "participantName", "钱副主任",
                "conclusion", "PASSED",
                "remark", "副主任在线同意验收"));
        actionRaw(viceDirectorToken, workOrderId, "accept-completed", Map.of("remark", "仍缺少用印"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("验收文件尚未完成业委会公章用印及登记")));

        action(managerToken, workOrderId, "acceptance-records", Map.of(
                "participantType", "PROPERTY_TECHNICAL_COSIGNER",
                "participantName", "物业项目负责人",
                "participantOrganization", "测试物业服务企业",
                "conclusion", "PASSED",
                "signatureHash", "property-acceptance-signature",
                "remark", "物业核对工程量并共同签署"));
        actionRaw(viceDirectorToken, workOrderId, "accept-completed", Map.of("remark", "仍缺少用印"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", is("验收文件尚未完成业委会公章用印及登记")));

        action(viceDirectorToken, workOrderId, "acceptance-seal", Map.of(
                "sealedAttachmentId", sealedDocumentId,
                "remark", "业委会验收表用印登记"));

        action(viceDirectorToken, workOrderId, "accept-completed", Map.of("remark", "三项条件全部满足"))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        assertEquals(3, jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT party_role)
                FROM t_repair_acceptance_party party
                JOIN t_repair_acceptance acceptance ON acceptance.acceptance_id = party.acceptance_id
                WHERE acceptance.work_order_id = ?
                  AND party.party_role IN (
                    'COMMITTEE_EXECUTIVE_APPROVER', 'COMMITTEE_SEAL_OPERATOR',
                    'PROPERTY_TECHNICAL_COSIGNER'
                  )
                """, Integer.class, workOrderId));
        assertEquals("VICE_DIRECTOR", jdbcTemplate.queryForObject("""
                SELECT committee_position
                FROM t_repair_acceptance_party party
                JOIN t_repair_acceptance acceptance ON acceptance.acceptance_id = party.acceptance_id
                WHERE acceptance.work_order_id = ?
                  AND party.party_role = 'COMMITTEE_EXECUTIVE_APPROVER'
                ORDER BY party.party_id DESC LIMIT 1
                """, String.class, workOrderId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_committee_seal_usage usage
                JOIN t_repair_acceptance acceptance ON acceptance.acceptance_id = usage.business_id
                WHERE acceptance.work_order_id = ?
                  AND usage.business_type = 'REPAIR_ACCEPTANCE'
                """, Integer.class, workOrderId));
    }

    private ResultActions action(String token, long workOrderId, String action, Object body) throws Exception {
        return actionRaw(token, workOrderId, action, body).andExpect(status().isOk());
    }

    private ResultActions actionRaw(String token, long workOrderId, String action, Object body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/repair-work-orders/" + workOrderId + "/" + action)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private long readyAttachment(long workOrderId, String kind, long accountId, String etag) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_attachment (
                    work_order_id, tenant_id, attachment_kind, object_key, original_file_name,
                    content_type, declared_size, actual_size, etag, status, uploaded_by_account_id
                ) VALUES (?, ?, ?, ?, ?, 'image/png', 1024, 1024, ?, 'READY', ?)
                RETURNING attachment_id
                """, Long.class, workOrderId, TENANT, kind,
                "repair/test/" + workOrderId + "/" + kind + "/" + System.nanoTime(),
                kind + ".png", etag, accountId);
    }

    private String token(long accountId, long userId) {
        return jwtTokenProvider.generateToken(accountId, "SYS_USER", userId, TENANT);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
