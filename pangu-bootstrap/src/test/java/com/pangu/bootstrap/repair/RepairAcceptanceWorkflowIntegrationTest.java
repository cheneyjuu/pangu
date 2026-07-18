// 关联业务：验证未形成可信验收规则、施工和结算事实的维修草稿不能借旧验收接口提前定案。
package com.pangu.bootstrap.repair;

import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepairAcceptanceWorkflowIntegrationTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_COMMITTEE_DIRECTOR = 999811L;
    private static final long USER_COMMITTEE_DIRECTOR = 800101L;
    private static final String PROJECT_PREFIX = "IT-可信验收阻断-";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String directorToken;

    @BeforeEach
    void setUp() {
        directorToken = jwtTokenProvider.generateToken(
                ACCOUNT_COMMITTEE_DIRECTOR, "SYS_USER", USER_COMMITTEE_DIRECTOR, TENANT);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM t_repair_project WHERE project_name LIKE ?", PROJECT_PREFIX + "%");
    }

    @Test
    void draftCannotFinalizeAcceptanceWithoutTrustedExecutionAndAcceptanceSnapshots() throws Exception {
        long projectId = createCommunityDraftProject();

        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/acceptance/finalization")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedProjectVersion\":0,\"resultAttachmentId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg", is("当前项目状态不允许该动作 status=DRAFT")));

        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_project WHERE project_id = ?", String.class, projectId));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_acceptance WHERE project_id = ?
                """, Integer.class, projectId));
    }

    private long createCommunityDraftProject() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_project (
                    tenant_id, project_name, workflow_type, scope_type,
                    status, created_by_account_id, created_by_user_id
                ) VALUES (?, ?, 'COMMUNITY_PUBLIC_REPAIR', 'COMMUNITY', 'DRAFT', ?, ?)
                RETURNING project_id
                """, Long.class, TENANT, PROJECT_PREFIX + System.nanoTime(),
                ACCOUNT_COMMITTEE_DIRECTOR, USER_COMMITTEE_DIRECTOR);
    }
}
