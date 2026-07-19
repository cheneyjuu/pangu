// 关联业务：验证未接入可信资金、决定和授权快照的维修草稿不能借旧治理接口进入楼栋征询流程。
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
class RepairProjectGovernanceFlowTest {

    private static final long TENANT = 10001L;
    private static final long ACCOUNT_PROPERTY_MANAGER = 999821L;
    private static final long USER_PROPERTY_MANAGER = 800201L;
    private static final String PROJECT_PREFIX = "IT-可信快照治理阻断-";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String propertyToken;
    private long buildingId;

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
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM t_repair_project WHERE project_name LIKE ?", PROJECT_PREFIX + "%");
    }

    @Test
    void draftCannotStartBuildingGovernanceBeforeTrustedSnapshotsAreFrozen() throws Exception {
        long projectId = createDraftProject();

        mockMvc.perform(post("/api/v1/admin/repair-projects/" + projectId + "/building-governance/start")
                        .header("Authorization", "Bearer " + propertyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedProjectVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg", is("当前办理进度不能发起相关业主表决，请刷新后查看")));

        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM t_repair_project WHERE project_id = ?", String.class, projectId));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_repair_building_process WHERE project_id = ?
                """, Integer.class, projectId));
    }

    private long createDraftProject() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO t_repair_project (
                    tenant_id, project_name, workflow_type, scope_type, building_id,
                    status, created_by_account_id, created_by_user_id
                ) VALUES (?, ?, 'BUILDING_REPAIR', 'BUILDING', ?, 'DRAFT', ?, ?)
                RETURNING project_id
                """, Long.class, TENANT, PROJECT_PREFIX + System.nanoTime(), buildingId,
                ACCOUNT_PROPERTY_MANAGER, USER_PROPERTY_MANAGER);
    }
}
