package com.pangu.bootstrap.voting;

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
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3-3 候选人提名 / 资格审查 / 列表 endpoint 的 {@code @PreAuthorize} × seed user 矩阵
 * （与 {@code VotingEndpointMatrixTest} 同风格）。
 *
 * <p>V1.1 求是小区 seed + V1.4 角色授权：
 * <ul>
 *   <li>陈网格员（GRID_OPERATOR，role 4）—— 有 {@code candidate:nominate}，无 approve / audit；</li>
 *   <li>刘主任（COMMUNITY_ADMIN，role 2）—— 有 {@code candidate:approve} + {@code voting:subject:audit}，无 nominate；</li>
 *   <li>李四（C_USER）—— G 端候选人 endpoint 全 403，仅 C 端只读列表 isAuthenticated 通过。</li>
 * </ul>
 *
 * <p>断言两类码：PreAuthorize 拒绝 → {@code code=403}；通过 PreAuthorize 后命中 service 业务异常
 * → ElectionErrorCode（CANDIDATE_NOT_FOUND=40942 / OPID_NOT_OWNED=40331）。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ElectionCandidateEndpointMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long TENANT_RUSHI = 10001L;

    private static final long ACC_GRID = 999804L, USR_GRID = 800004L;   // 陈网格员（nominate，无 approve/audit）
    private static final long ACC_COMM = 999803L, USR_COMM = 800003L;   // 刘主任（approve + audit，无 nominate）
    private static final long ACC_LISI = 999913L, UID_LISI = 70002L;    // 李四 C_USER

    private static final String TITLE_PREFIX = "M33MTX-";

    private String sysToken(long acc, long userId) {
        return jwtTokenProvider.generateToken(acc, "SYS_USER", userId, TENANT_RUSHI);
    }

    private String cToken(long acc, long uid) {
        return jwtTokenProvider.generateToken(acc, "C_USER", uid, TENANT_RUSHI);
    }

    @BeforeEach
    public void setUp() {
        cleanUp();
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM t_election_candidate WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?)",
                TENANT_RUSHI, TITLE_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?",
                TENANT_RUSHI, TITLE_PREFIX + "%");
    }

    /** 在 tenant 10001 种一条 ELECTION 议题（带 max_winners 以过 trigger 13）。 */
    private Long insertElectionSubject(String titleSuffix, int status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, "
                        + "party_ratio_floor, max_winners, vote_start_at, vote_end_at) "
                        + "VALUES(?, ?, 1, 1, ?, 0.50, 3, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '7 day') RETURNING subject_id",
                Long.class, TENANT_RUSHI, TITLE_PREFIX + titleSuffix, status);
    }

    private Long insertPendingCandidate(Long subjectId, Long uid, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_election_candidate(subject_id, uid, name, is_party_member, qualification_status) "
                        + "VALUES(?, ?, ?, 0, 1) RETURNING candidate_id",
                Long.class, subjectId, uid, name);
    }

    private String nominateBody(long uid, String name, boolean party) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("uid", uid);
        body.put("name", name);
        body.put("partyMember", party);
        return objectMapper.writeValueAsString(body);
    }

    private String reviewBody(boolean approve) throws Exception {
        return objectMapper.writeValueAsString(Map.of("approve", approve));
    }

    // ===== 提名 nominate：candidate:nominate =====

    @Test
    public void gridOperatorNominate_passesPreAuth_created() throws Exception {
        Long subjectId = insertElectionSubject("nominate-draft", 1); // DRAFT
        mockMvc.perform(post("/api/v1/voting-subjects/" + subjectId + "/candidates")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nominateBody(8860001L, "提名候选人", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.candidateId").exists());
    }

    @Test
    public void communityAdminCannotNominate_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/candidates")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nominateBody(8860002L, "无权提名", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    public void cUserCannotNominate_403() throws Exception {
        mockMvc.perform(post("/api/v1/voting-subjects/99999/candidates")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nominateBody(8860003L, "业主无权提名", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== 资格审查 review：candidate:approve =====

    @Test
    public void communityAdminReview_approves_200() throws Exception {
        Long subjectId = insertElectionSubject("review-draft", 1);
        Long candidateId = insertPendingCandidate(subjectId, 8860010L, "待审候选人");
        mockMvc.perform(post("/api/v1/candidates/" + candidateId + "/review")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.qualificationStatus", is("APPROVED")));
    }

    @Test
    public void communityAdminReviewMissingCandidate_passesPreAuth_thenNotFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/candidates/99999999/review")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(40942)));
    }

    @Test
    public void gridOperatorCannotReview_403() throws Exception {
        mockMvc.perform(post("/api/v1/candidates/99999999/review")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== 管理端候选人列表 audit：voting:subject:audit =====

    @Test
    public void communityAdminListForAdmin_200() throws Exception {
        Long subjectId = insertElectionSubject("admin-list", 1);
        insertPendingCandidate(subjectId, 8860020L, "候选人甲");
        mockMvc.perform(get("/api/v1/voting-subjects/" + subjectId + "/candidates")
                        .header("Authorization", "Bearer " + sysToken(ACC_COMM, USR_COMM)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data[0].name", is("候选人甲")));
    }

    @Test
    public void gridOperatorCannotListForAdmin_403() throws Exception {
        mockMvc.perform(get("/api/v1/voting-subjects/99999/candidates")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    // ===== C 端只读候选人列表：isAuthenticated =====

    @Test
    public void cUserListForOwner_200() throws Exception {
        // PUBLISHED + COMMUNITY scope 议题对租户内业主可见；仅返回 APPROVED 候选人
        Long subjectId = insertElectionSubject("owner-list", 2); // PUBLISHED
        mockMvc.perform(get("/api/v1/me/voting-subjects/" + subjectId + "/candidates")
                        .header("Authorization", "Bearer " + cToken(ACC_LISI, UID_LISI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    public void sysUserHittingOwnerCandidateList_noUid_opidNotOwned_403() throws Exception {
        // SYS_USER（网格员）无 c_user.uid → OwnerVotingController#requireUid() 抛 OPID_NOT_OWNED
        mockMvc.perform(get("/api/v1/me/voting-subjects/99999/candidates")
                        .header("Authorization", "Bearer " + sysToken(ACC_GRID, USR_GRID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(40331)));
    }
}
