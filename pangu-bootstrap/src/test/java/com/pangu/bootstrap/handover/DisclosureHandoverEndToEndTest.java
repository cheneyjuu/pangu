package com.pangu.bootstrap.handover;

import com.pangu.interfaces.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 换届熔断端到端：业委会换届选举在途 → 财务公示发布被 {@code 409 HANDOVER_IN_PROGRESS(41109)} 熔断；
 * 选举结算后 → 同一发布请求自动恢复 {@code 200}（查询派生「自动解除」无需任何手工解锁）。
 *
 * <p>走真实 HTTP 链：周主任（COMMITTEE_DIRECTOR，持 {@code disclosure:publish}）JWT 打
 * {@code POST /api/v1/disclosures/{snapshotId}/publish}。
 *
 * <p>种子隔离：DRAFT 快照直插到求是租户 {@code 10001} 的专用 period {@code 2099-12}；
 * 换届选举用 {@code HANDOVER-E2E-} 前缀。不触碰真实 seed 数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class DisclosureHandoverEndToEndTest {

    private static final long TENANT_RUSHI = 10001L;
    // 周主任 COMMITTEE_DIRECTOR：compose + publish
    private static final long ACC_DIR = 999811L, USR_DIR = 800101L;

    private static final String TEST_PERIOD = "2099-12";
    private static final String TITLE_PREFIX = "HANDOVER-E2E-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本类直插的 DRAFT 快照 id（用于精准清理其治理锁）。 */
    private Long composedSnapshotId;

    @BeforeEach
    public void setUp() {
        cleanUp();
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        // 先删快照（解除对 governance_lock 的 FK 引用），再删恢复发布时产生的治理锁
        jdbcTemplate.update(
                "DELETE FROM t_finance_disclosure_snapshot WHERE tenant_id = ? AND period = ?",
                TENANT_RUSHI, TEST_PERIOD);
        if (composedSnapshotId != null) {
            jdbcTemplate.update(
                    "DELETE FROM t_governance_lock WHERE tenant_id = ? AND entity_type = 'FINANCE_DISCLOSURE' "
                            + "AND entity_id = ?",
                    TENANT_RUSHI, composedSnapshotId);
        }
        jdbcTemplate.update(
                "DELETE FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?",
                TENANT_RUSHI, TITLE_PREFIX + "%");
        composedSnapshotId = null;
    }

    /** 直插一条 DRAFT 财务公示快照（绕过 compose 的资金台账聚合，聚焦熔断闸门）。 */
    private long seedDraftSnapshot() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_finance_disclosure_snapshot(tenant_id, period, disclosure_type, status, "
                        + "data_payload, statistics_version, payload_hash, composed_by_user_id) "
                        + "VALUES(?, ?, 'MAINTENANCE_FUND', 1, CAST('{}' AS JSONB), 1, ?, ?) RETURNING snapshot_id",
                Long.class, TENANT_RUSHI, TEST_PERIOD, "a".repeat(64), USR_DIR);
    }

    /** 种一条在途 ELECTION（PUBLISHED）。 */
    private long seedPublishedElection() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, max_winners) "
                        + "VALUES(?, ?, 1, 1, 2, 2) RETURNING subject_id",
                Long.class, TENANT_RUSHI, TITLE_PREFIX + "业委会换届");
    }

    private Integer snapshotStatus(long snapshotId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM t_finance_disclosure_snapshot WHERE snapshot_id = ?",
                Integer.class, snapshotId);
    }

    @Test
    public void publishFrozenDuringHandover_thenAutoRecoversAfterSettlement() throws Exception {
        String dir = "Bearer " + jwtTokenProvider.generateToken(ACC_DIR, "SYS_USER", USR_DIR, TENANT_RUSHI);

        composedSnapshotId = seedDraftSnapshot();
        long electionId = seedPublishedElection();

        // 1. 换届在途 → 发布被熔断 409 / 41109
        mockMvc.perform(post("/api/v1/disclosures/" + composedSnapshotId + "/publish")
                        .header("Authorization", dir))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(41109)));
        // 熔断在治理锁之前发生，事务回滚，快照仍为 DRAFT(1)
        assertEquals(1, snapshotStatus(composedSnapshotId), "熔断后快照应仍为 DRAFT");

        // 2. 选举结算（SETTLED）→ 查询派生自动解除熔断
        jdbcTemplate.update(
                "UPDATE t_voting_subject SET status = 5, settled_at = CURRENT_TIMESTAMP WHERE subject_id = ?",
                electionId);

        // 3. 同一发布请求自动恢复 200，快照推进到 PUBLISHED(3)
        mockMvc.perform(post("/api/v1/disclosures/" + composedSnapshotId + "/publish")
                        .header("Authorization", dir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PUBLISHED")));
        assertEquals(3, snapshotStatus(composedSnapshotId), "恢复后快照应为 PUBLISHED");
    }
}
