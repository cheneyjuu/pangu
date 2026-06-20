package com.pangu.bootstrap.voting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.voting.ProposalLifecycleService;
import com.pangu.application.voting.VotingApplicationService;
import com.pangu.application.voting.command.SettleSubjectCommand;
import com.pangu.domain.repository.VotingResultRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3-3 ELECTION 选举全流程端到端测试：立项 → 提名 → 资格审查 → 公示 → 开票 → 投票 → 结算。
 *
 * <p>管理侧动作（立项 / 提名 / 审查 / 公示 / 列表）全部走真实 HTTP endpoint + 求是小区 seed
 * 用户的 JWT 鉴权链；开票（scheduler 职责）通过 {@link ProposalLifecycleService#openVoting}
 * 触发；选票与业主分母数据用 JdbcTemplate 直插（业主投票 endpoint 的闸门由
 * {@code ElectionVoteSubmissionTest} 单测覆盖，本类聚焦"工作流贯通 + 真实结算落库"）。
 *
 * <p>使用独立测试楼栋 {@code 990010}（BUILDING scope），保证分母只统计本类种入的业主，
 * 结算结果确定可断言；不触碰 tenant 10001 的真实 seed 数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ElectionWorkflowEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProposalLifecycleService proposalLifecycleService;

    @Autowired
    private VotingApplicationService votingApplicationService;

    @Autowired
    private VotingResultRepository votingResultRepository;

    private static final long TENANT_RUSHI = 10001L;
    private static final long TEST_BUILDING = 990010L;

    private static final long ACC_GRID = 999804L, USR_GRID = 800004L;   // 陈网格员（提名）
    private static final long ACC_COMM = 999803L, USR_COMM = 800003L;   // 刘主任（立项 / 资格审查 / 公示 / 列表）
    private static final long ACC_PARTY = 999802L, USR_PARTY = 800002L; // 李书记（党组书记前置审查）

    private static final String TITLE_PREFIX = "M33E2E-";

    private String sysToken(long acc, long userId) {
        return jwtTokenProvider.generateToken(acc, "SYS_USER", userId, TENANT_RUSHI);
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
                "DELETE FROM t_voting_result WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?)",
                TENANT_RUSHI, TITLE_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM t_voting_denominator_item_snapshot WHERE snapshot_id IN "
                        + "(SELECT snapshot_id FROM t_voting_denominator_snapshot WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?))",
                TENANT_RUSHI, TITLE_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM t_voting_denominator_snapshot WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?)",
                TENANT_RUSHI, TITLE_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM t_vote_item WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?)",
                TENANT_RUSHI, TITLE_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM t_election_candidate WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?)",
                TENANT_RUSHI, TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE tenant_id = ? AND title LIKE ?",
                TENANT_RUSHI, TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM c_owner_property WHERE tenant_id = ? AND building_id = ?",
                TENANT_RUSHI, TEST_BUILDING);
        jdbcTemplate.update(
                "DELETE FROM c_user WHERE account_id IN ("
                        + "SELECT account_id FROM t_account WHERE phone LIKE '776%' AND length(phone) = 11)");
        jdbcTemplate.update("DELETE FROM t_account WHERE phone LIKE '776%' AND length(phone) = 11");
    }

    private Long insertUser(String phone) {
        Long accountId = jdbcTemplate.queryForObject(
                "INSERT INTO t_account(phone, real_name, real_name_verified, status) "
                        + "VALUES(?, '选举业主', 0, 1) RETURNING account_id",
                Long.class, phone);
        return jdbcTemplate.queryForObject(
                "INSERT INTO c_user(account_id, auth_level) VALUES(?, 1) RETURNING uid",
                Long.class, accountId);
    }

    private long insertOwnership(Long uid, Long roomId, BigDecimal area) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO c_owner_property(uid, tenant_id, building_id, room_id, build_area, "
                        + "is_voting_delegate, account_status) VALUES(?, ?, ?, ?, ?, 1, 1) RETURNING opid",
                Long.class, uid, TENANT_RUSHI, TEST_BUILDING, roomId, area);
    }

    private void insertSupportVote(Long subjectId, long opid, Long uid, Long candidateId, BigDecimal area) {
        jdbcTemplate.update(
                "INSERT INTO t_vote_item(subject_id, opid, uid, target_id, property_area, choice) "
                        + "VALUES(?, ?, ?, ?, ?, 1)",
                subjectId, opid, uid, candidateId, area);
    }

    private String proposeBody() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", TITLE_PREFIX + "业委会换届选举");
        body.put("subjectType", "ELECTION");
        body.put("scope", "BUILDING");
        body.put("scopeReferenceId", TEST_BUILDING);
        body.put("voteStartAt", "2026-06-01T00:00:00Z");
        body.put("voteEndAt", "2026-07-15T00:00:00Z");
        body.put("maxWinners", 2);
        return objectMapper.writeValueAsString(body);
    }

    private String nominateBody(long uid, String name, boolean party) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("uid", uid);
        body.put("name", name);
        body.put("partyMember", party);
        return objectMapper.writeValueAsString(body);
    }

    private long dataLong(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path(field).asLong();
    }

    @Test
    public void fullElectionPipeline_proposeNominateReviewVoteSettle() throws Exception {
        String comm = "Bearer " + sysToken(ACC_COMM, USR_COMM);
        String grid = "Bearer " + sysToken(ACC_GRID, USR_GRID);
        String party = "Bearer " + sysToken(ACC_PARTY, USR_PARTY);

        // 1. 立项 ELECTION（刘主任，maxWinners=2，BUILDING scope）
        MvcResult proposeResult = mockMvc.perform(post("/api/v1/voting-subjects")
                        .header("Authorization", comm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposeBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subjectType", org.hamcrest.Matchers.is("ELECTION")))
                .andReturn();
        long subjectId = dataLong(proposeResult, "subjectId");

        // 2. 提名 3 名候选人（网格员）：2 党员 + 1 非党员
        long c1 = dataLong(mockMvc.perform(post("/api/v1/voting-subjects/" + subjectId + "/candidates")
                .header("Authorization", grid).contentType(MediaType.APPLICATION_JSON)
                .content(nominateBody(8870001L, "党员候选人甲", true)))
                .andExpect(status().isCreated()).andReturn(), "candidateId");
        long c2 = dataLong(mockMvc.perform(post("/api/v1/voting-subjects/" + subjectId + "/candidates")
                .header("Authorization", grid).contentType(MediaType.APPLICATION_JSON)
                .content(nominateBody(8870002L, "党员候选人乙", true)))
                .andExpect(status().isCreated()).andReturn(), "candidateId");
        long c3 = dataLong(mockMvc.perform(post("/api/v1/voting-subjects/" + subjectId + "/candidates")
                .header("Authorization", grid).contentType(MediaType.APPLICATION_JSON)
                .content(nominateBody(8870003L, "非党员候选人丙", false)))
                .andExpect(status().isCreated()).andReturn(), "candidateId");

        // 3. 党组书记前置审查（李书记）：c1/c2/c3 三人前置审查通过 → PENDING_COMMITTEE_REVIEW
        for (long cid : new long[]{c1, c2, c3}) {
            mockMvc.perform(post("/api/v1/candidates/" + cid + "/party-review")
                            .header("Authorization", party).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("approve", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.qualificationStatus",
                            org.hamcrest.Matchers.is("PENDING_COMMITTEE_REVIEW")));
        }

        // 4. 居委会资格审查（刘主任）：c1/c2 通过、c3 拒绝
        mockMvc.perform(post("/api/v1/candidates/" + c1 + "/review")
                        .header("Authorization", comm).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("approve", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualificationStatus", org.hamcrest.Matchers.is("APPROVED")));
        mockMvc.perform(post("/api/v1/candidates/" + c2 + "/review")
                        .header("Authorization", comm).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("approve", true))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/candidates/" + c3 + "/review")
                        .header("Authorization", comm).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("approve", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qualificationStatus", org.hamcrest.Matchers.is("REJECTED")));

        // 5. 管理端列表（刘主任）：3 名候选人都在
        mockMvc.perform(get("/api/v1/voting-subjects/" + subjectId + "/candidates")
                        .header("Authorization", comm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", org.hamcrest.Matchers.is(3)));

        // 6. 公示（刘主任有 voting:subject:publish）：DRAFT → PUBLISHED
        mockMvc.perform(post("/api/v1/voting-subjects/" + subjectId + "/publish")
                        .header("Authorization", comm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.is("PUBLISHED")));

        // 7. 业主 + 房产（3 户各 100 ㎡，同栋 990010）
        Long uid1 = insertUser("77600000001");
        Long uid2 = insertUser("77600000002");
        Long uid3 = insertUser("77600000003");
        long opid1 = insertOwnership(uid1, 660001L, new BigDecimal("100.00"));
        long opid2 = insertOwnership(uid2, 660002L, new BigDecimal("100.00"));
        long opid3 = insertOwnership(uid3, 660003L, new BigDecimal("100.00"));

        // 8. 开票（scheduler 职责，手工触发）：PUBLISHED → VOTING
        proposalLifecycleService.openVoting(subjectId, Instant.now());

        // 9. 投票：3 户各对两名 APPROVED 候选人投 SUPPORT（100% 参与）
        for (long[] owner : new long[][]{{opid1, uid1}, {opid2, uid2}, {opid3, uid3}}) {
            insertSupportVote(subjectId, owner[0], owner[1], c1, new BigDecimal("100.00"));
            insertSupportVote(subjectId, owner[0], owner[1], c2, new BigDecimal("100.00"));
        }

        // 10. 结算（手工触发）：路由进 ElectionVotingEngine
        VotingResultRepository.Snapshot snapshot = votingApplicationService.settle(
                new SettleSubjectCommand(subjectId, "MANUAL"));
        assertTrue(snapshot.resultPayloadJson().contains("\"subjectType\":\"ELECTION\""),
                "结算应路由进选举引擎，payload=" + snapshot.resultPayloadJson());
        assertTrue(snapshot.quorumSatisfied(), "全员参与应满足双 2/3");
        assertNotNull(snapshot.attestationTxHash());
        assertTrue(snapshot.attestationTxHash().startsWith("STUB-"));

        // 11. 议题翻转 SETTLED + 候选人终态落库（2 APPROVED + 1 REJECTED）
        Integer finalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM t_voting_subject WHERE subject_id = ?", Integer.class, subjectId);
        assertEquals(5, finalStatus, "议题应翻转为 SETTLED(5)");
        Integer approved = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_election_candidate WHERE subject_id = ? AND qualification_status = 2",
                Integer.class, subjectId);
        Integer rejected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_election_candidate WHERE subject_id = ? AND qualification_status = 3",
                Integer.class, subjectId);
        assertEquals(2, approved, "应有 2 名 APPROVED 候选人");
        assertEquals(1, rejected, "应有 1 名 REJECTED 候选人");
    }
}
