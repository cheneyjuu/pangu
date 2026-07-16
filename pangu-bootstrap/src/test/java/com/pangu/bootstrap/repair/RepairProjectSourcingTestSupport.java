// 关联业务：为非询价主题的维修工程集成测试构造完整、可审计的竞争性询价前置条件。
package com.pangu.bootstrap.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class RepairProjectSourcingTestSupport {

    private RepairProjectSourcingTestSupport() {
    }

    static SelectedSupplier completeCompetitiveSourcing(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String propertyToken,
            String supplierPrefix,
            long projectId,
            int budgetAmount
    ) throws Exception {
        List<Long> supplierIds = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            supplierIds.add(registerVerifiedSupplier(
                    mockMvc, objectMapper, propertyToken, supplierPrefix + System.nanoTime() + "-" + index));
        }
        postJson(mockMvc, objectMapper, propertyToken, sourcingPath(projectId, "/invitations"), Map.of(
                "supplierDeptIds", supplierIds,
                "deadline", LocalDateTime.now().plusDays(3)));

        JsonNode selectedQuote = null;
        for (int index = 0; index < supplierIds.size(); index++) {
            long attachmentId = uploadQuote(
                    mockMvc, objectMapper, propertyToken, projectId, index + 1);
            JsonNode quote = postJson(mockMvc, objectMapper, propertyToken, sourcingPath(projectId, "/quotes"), Map.of(
                    "supplierDeptId", supplierIds.get(index),
                    "quoteAmount", budgetAmount + index * 50,
                    "quoteSummary", "第 " + (index + 1) + " 家供应商纸质报价，物业核验原件后录入",
                    "attachmentId", attachmentId,
                    "confirmationStatus", "OFFLINE_EVIDENCE_VERIFIED",
                    "originalSource", "PAPER"));
            if (index == 0) {
                selectedQuote = quote;
            }
        }

        JsonNode sourcing = postJson(
                mockMvc, objectMapper, propertyToken, sourcingPath(projectId, "/selection"), Map.of(
                        "quoteId", selectedQuote.path("quoteId").asLong(),
                        "recommendationReason", "三家有效报价比较后，选择报价最低且符合方案要求的供应商"));
        JsonNode selection = sourcing.path("selection");
        return new SelectedSupplier(
                selection.path("supplierDeptId").asLong(),
                selection.path("quoteId").asLong(),
                selectedQuote.path("attachmentId").asLong(),
                selection.path("quoteAmount").asInt());
    }

    static void cleanSuppliers(JdbcTemplate jdbcTemplate, String supplierPrefix) {
        jdbcTemplate.update("""
                DELETE FROM t_supplier_activation_invitation
                WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, supplierPrefix + "%");
        jdbcTemplate.update("""
                DELETE FROM t_supplier_tenant_relation
                WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, supplierPrefix + "%");
        jdbcTemplate.update("""
                DELETE FROM t_supplier_enterprise_verification
                WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, supplierPrefix + "%");
        jdbcTemplate.update("""
                DELETE FROM t_supplier_org_profile
                WHERE supplier_dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_name LIKE ?)
                """, supplierPrefix + "%");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE dept_name LIKE ?", supplierPrefix + "%");
    }

    private static long registerVerifiedSupplier(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String propertyToken,
            String supplierName
    ) throws Exception {
        JsonNode supplier = postJson(mockMvc, objectMapper, propertyToken,
                "/api/v1/admin/supplier-organizations", Map.of("legalName", supplierName));
        long supplierDeptId = supplier.isNumber()
                ? supplier.asLong()
                : supplier.path("supplierDeptId").asLong();
        postCreated(mockMvc, objectMapper, propertyToken,
                "/api/v1/admin/supplier-organizations/" + supplierDeptId + "/manual-verifications", Map.of(
                        "unifiedSocialCreditCode", "91310000" + String.format("%010d", supplierDeptId),
                        "sourceCode", "GSXT_WEB",
                        "verificationResult", "PASSED",
                        "remark", "集成测试中已核对企业登记信息"));
        return supplierDeptId;
    }

    private static long uploadQuote(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String propertyToken,
            long projectId,
            int supplierNo
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "供应商" + supplierNo + "报价原件.pdf",
                "application/pdf",
                ("supplier-quote-" + supplierNo).getBytes(StandardCharsets.UTF_8));
        String response = mockMvc.perform(multipart(
                        "/api/v1/admin/repair-projects/" + projectId + "/attachments")
                        .file(file)
                        .header("Authorization", bearer(propertyToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("attachmentId").asLong();
    }

    private static JsonNode postJson(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String token,
            String path,
            Object body
    ) throws Exception {
        String response = mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data");
    }

    private static JsonNode postCreated(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            String token,
            String path,
            Object body
    ) throws Exception {
        String response = mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data");
    }

    private static String sourcingPath(long projectId, String suffix) {
        return "/api/v1/admin/repair-projects/" + projectId + "/sourcing" + suffix;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    record SelectedSupplier(
            long supplierDeptId,
            long quoteId,
            long quoteAttachmentId,
            int quoteAmount
    ) {
    }
}
