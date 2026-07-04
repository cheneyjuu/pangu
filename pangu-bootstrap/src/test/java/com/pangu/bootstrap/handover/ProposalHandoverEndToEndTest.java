package com.pangu.bootstrap.handover;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 换届熔断扩展到立项的端到端：业委会换届选举在途 → 新 GENERAL 议题立项被
 * {@code 409 PROPOSE_FROZEN_HANDOVER(40926)} 熔断；选举结算后 → 同一立项请求自动恢复
 * {@code 201}（查询派生「自动解除」无需任何手工解锁）。
 *
 * <p>走真实 HTTP 链：刘主任（COMMUNITY_ADMIN，持 {@code voting:subject:create}）JWT 打
 * {@code POST /api/v1/voting-subjects}。
 *
 * <p>种子隔离：换届选举与立项产物统一用 {@code HANDOVER-PROP-} 前缀，租户 {@code 10001}；
 * 不触碰真实 seed 数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ProposalHandoverEndToEndTest {

    private static final long TENANT_RUSHI = 10001L;
    // 刘主任 COMMUNITY_ADMIN：持 voting:subject:create
    private static final long ACC_COMM = 999803L, USR_COMM = 800003L;

    private static final String TITLE_PREFIX = "HANDOVER-PROP-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<ActiveElectionBackup> suppressedActiveElections = List.of();

    @BeforeEach
    public void setUp() {
        cleanUp();
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        restoreSuppressedActiveElections();
        jdbcTemplate.update(
                "DELETE FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?",
                TENANT_RUSHI, TITLE_PREFIX + "%");
    }

    private void suppressExistingActiveElections() {
        suppressedActiveElections = jdbcTemplate.query(
                "SELECT subject_id, status FROM t_voting_subject "
                        + "WHERE tenant_id = ? AND subject_type = 1 AND status IN (2, 3, 4) "
                        + "AND title NOT LIKE ?",
                (rs, rowNum) -> new ActiveElectionBackup(rs.getLong("subject_id"), rs.getInt("status")),
                TENANT_RUSHI, TITLE_PREFIX + "%");
        for (ActiveElectionBackup backup : suppressedActiveElections) {
            jdbcTemplate.update(
                    "UPDATE t_voting_subject SET status = 5, settled_at = CURRENT_TIMESTAMP WHERE subject_id = ?",
                    backup.subjectId());
        }
    }

    private void restoreSuppressedActiveElections() {
        for (ActiveElectionBackup backup : suppressedActiveElections) {
            jdbcTemplate.update(
                    "UPDATE t_voting_subject SET status = ?, settled_at = NULL WHERE subject_id = ?",
                    backup.status(), backup.subjectId());
        }
        suppressedActiveElections = List.of();
    }

    /** 种一条在途 ELECTION（PUBLISHED）。 */
    private long seedPublishedElection() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, max_winners) "
                        + "VALUES(?, ?, 1, 1, 2, 2) RETURNING subject_id",
                Long.class, TENANT_RUSHI, TITLE_PREFIX + "业委会换届");
    }

    private String generalProposeBody() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", TITLE_PREFIX + "加装电梯表决");
        body.put("subjectType", "GENERAL");
        body.put("content", "<p>加装电梯表决方案</p>");
        body.put("scope", "COMMUNITY");
        body.put("voteStartAt", "2026-07-01T00:00:00Z");
        body.put("voteEndAt", "2026-07-15T00:00:00Z");
        return objectMapper.writeValueAsString(body);
    }

    @Test
    public void generalProposeFrozenDuringHandover_thenAutoRecoversAfterSettlement() throws Exception {
        String comm = "Bearer " + jwtTokenProvider.generateToken(ACC_COMM, "SYS_USER", USR_COMM, TENANT_RUSHI);
        suppressExistingActiveElections();
        long electionId = seedPublishedElection();

        // 1. 换届在途 → 新 GENERAL 立项被熔断 409 / 40926
        mockMvc.perform(post("/api/v1/voting-subjects")
                        .header("Authorization", comm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generalProposeBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(40926)));

        // 2. 选举结算（SETTLED）→ 查询派生自动解除熔断
        jdbcTemplate.update(
                "UPDATE t_voting_subject SET status = 5, settled_at = CURRENT_TIMESTAMP WHERE subject_id = ?",
                electionId);

        // 3. 同一立项请求自动恢复 201
        mockMvc.perform(post("/api/v1/voting-subjects")
                        .header("Authorization", comm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generalProposeBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subjectType", is("GENERAL")));
    }

    private record ActiveElectionBackup(long subjectId, int status) {
    }
}
