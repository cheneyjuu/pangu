package com.pangu.bootstrap.voting;

import com.pangu.application.voting.ProposalLifecycleService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingApplicationService;
import com.pangu.application.voting.command.ProposeSubjectCommand;
import com.pangu.application.voting.command.SettleSubjectCommand;
import com.pangu.domain.model.voting.ElectionSubject;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingResultRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3-3 选举立项落库 + 结算路由集成测试（{@code @SpringBootTest} + 真实 PostgreSQL）。
 *
 * <p>聚焦 ELECTION 在「立项 → 落库 → toAggregate 构造 ElectionSubject → 路由进
 * {@link com.pangu.domain.model.voting.ElectionVotingEngine}」这条新管道：
 * <ul>
 *   <li>propose ELECTION 缺 maxWinners → {@code ELECTION_MAX_WINNERS_REQUIRED}；</li>
 *   <li>propose ELECTION 带 maxWinners → 落库 max_winners，{@code findById} 还原为 ElectionSubject；</li>
 *   <li>trigger 13 反例：ELECTION 行缺 max_winners 被 DB 兜底拒绝；</li>
 *   <li>settle ELECTION：路由不再抛 UnsupportedSubjectType，进引擎产出快照并翻转 SETTLED。</li>
 * </ul>
 *
 * <p>选举计票/党员下限的正确性由纯引擎用例 {@code ElectionVotingEngineTest} 覆盖，本类只验证管道连通。
 */
@SpringBootTest
public class ElectionProposeAndRouterTest {

    private static final long TEST_TENANT_ID = 99005L;
    private static final long TEST_BUILDING = 990005L;
    private static final long PROPOSER_ID = 800105L;

    @Autowired
    private ProposalLifecycleService proposalLifecycleService;

    @Autowired
    private VotingApplicationService votingApplicationService;

    @Autowired
    private VotingSubjectRepository votingSubjectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?)", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_voting_denominator_item_snapshot WHERE snapshot_id IN "
                        + "(SELECT snapshot_id FROM t_voting_denominator_snapshot WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?))", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_voting_denominator_snapshot WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?)", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_vote_item WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?)", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_election_candidate WHERE subject_id IN "
                        + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?)", TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM c_owner_property WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM c_user WHERE account_id IN ("
                        + "SELECT account_id FROM t_account WHERE phone LIKE '775%' AND length(phone) = 11)");
        jdbcTemplate.update("DELETE FROM t_account WHERE phone LIKE '775%' AND length(phone) = 11");
        jdbcTemplate.update("DELETE FROM t_outbox_event WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    private ProposeSubjectCommand electionCmd(Integer maxWinners) {
        return new ProposeSubjectCommand(
                TEST_TENANT_ID,
                SubjectType.ELECTION,
                VotingScope.BUILDING,
                TEST_BUILDING,
                "业委会换届选举-立项",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-07-15T00:00:00Z"),
                PROPOSER_ID,
                null,
                maxWinners);
    }

    private Long insertUser(String phone) {
        Long accountId = jdbcTemplate.queryForObject(
                "INSERT INTO t_account(phone, real_name, real_name_verified, status) "
                        + "VALUES(?, '选举测试业主', 0, 1) RETURNING account_id",
                Long.class, phone);
        return jdbcTemplate.queryForObject(
                "INSERT INTO c_user(account_id, auth_level) VALUES(?, 1) RETURNING uid",
                Long.class, accountId);
    }

    private long insertOwnership(Long uid, Long roomId, BigDecimal area) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO c_owner_property(uid, tenant_id, building_id, room_id, build_area, "
                        + "is_voting_delegate, account_status) VALUES(?, ?, ?, ?, ?, 1, 1) RETURNING opid",
                Long.class, uid, TEST_TENANT_ID, TEST_BUILDING, roomId, area);
    }

    private Long insertApprovedCandidate(Long subjectId, Long uid, String name, int isParty) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_election_candidate(subject_id, uid, name, is_party_member, qualification_status) "
                        + "VALUES(?, ?, ?, ?, 2) RETURNING candidate_id",
                Long.class, subjectId, uid, name, isParty);
    }

    private void insertSupportVote(Long subjectId, long opid, Long uid, Long candidateId, BigDecimal area) {
        jdbcTemplate.update(
                "INSERT INTO t_vote_item(subject_id, opid, uid, target_id, property_area, choice) "
                        + "VALUES(?, ?, ?, ?, ?, 1)",
                subjectId, opid, uid, candidateId, area);
    }

    // ===== propose 校验 =====

    @Test
    public void propose_electionWithoutMaxWinners_rejected() {
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> proposalLifecycleService.propose(electionCmd(null)));
        assertEquals(VotingApplicationException.Reason.ELECTION_MAX_WINNERS_REQUIRED, ex.getReason());
    }

    @Test
    public void propose_electionWithMaxWinners_persistsAndRebuildsElectionSubject() {
        VotingSubject draft = proposalLifecycleService.propose(electionCmd(3));
        assertNotNull(draft.getSubjectId());

        Integer maxWinners = jdbcTemplate.queryForObject(
                "SELECT max_winners FROM t_voting_subject WHERE subject_id = ?",
                Integer.class, draft.getSubjectId());
        assertEquals(3, maxWinners, "ELECTION 立项必须落库 max_winners");

        VotingSubject reloaded = votingSubjectRepository.findById(draft.getSubjectId()).orElseThrow();
        ElectionSubject electionSubject = assertInstanceOf(ElectionSubject.class, reloaded,
                "toAggregate 应把 ELECTION 行还原为 ElectionSubject");
        assertEquals(3, electionSubject.getMaxWinners());
    }

    // ===== trigger 13 反例 =====

    @Test
    public void trigger13_electionWithNullMaxWinners_rejectedByDb() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, "
                                + "vote_start_at, vote_end_at) "
                                + "VALUES(?, 'ELECTION 缺名额', 1, 1, 1, "
                                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')",
                        TEST_TENANT_ID));
        assertTrue(rootMessage(ex).contains("[trigger 13]"),
                "应被 trigger 13 拒绝，实际：" + rootMessage(ex));
    }

    // ===== 结算路由：ELECTION 进引擎 =====

    @Test
    public void settle_electionRoutesIntoEngine_andFlipsSettled() {
        // 3 名业主，各 100 ㎡（同栋）
        Long uid1 = insertUser("77500000001");
        Long uid2 = insertUser("77500000002");
        Long uid3 = insertUser("77500000003");
        long opid1 = insertOwnership(uid1, 650001L, new BigDecimal("100.00"));
        long opid2 = insertOwnership(uid2, 650002L, new BigDecimal("100.00"));
        long opid3 = insertOwnership(uid3, 650003L, new BigDecimal("100.00"));

        // ELECTION 议题（status=VOTING 让 settle 接受），maxWinners=2
        Long subjectId = jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, scope_reference_id, "
                        + "status, party_ratio_floor, max_winners, vote_start_at, vote_end_at) "
                        + "VALUES(?, '换届选举-settle', 1, 2, ?, 3, 0.50, 2, "
                        + "CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 day') "
                        + "RETURNING subject_id",
                Long.class, TEST_TENANT_ID, TEST_BUILDING);

        // 2 名 APPROVED 候选人（1 党员 + 1 非党员）
        Long c1 = insertApprovedCandidate(subjectId, 8850001L, "党员候选人", 1);
        Long c2 = insertApprovedCandidate(subjectId, 8850002L, "非党员候选人", 0);

        // 3 户各对两名候选人投 SUPPORT（100% 参与，双 2/3 必满足）
        for (long[] owner : new long[][]{{opid1, uid1}, {opid2, uid2}, {opid3, uid3}}) {
            insertSupportVote(subjectId, owner[0], owner[1], c1, new BigDecimal("100.00"));
            insertSupportVote(subjectId, owner[0], owner[1], c2, new BigDecimal("100.00"));
        }

        VotingResultRepository.Snapshot snapshot = votingApplicationService.settle(
                new SettleSubjectCommand(subjectId, "MANUAL"));

        assertTrue(snapshot.resultPayloadJson().contains("\"subjectType\":\"ELECTION\""),
                "结算快照 payload 应标记为 ELECTION（路由进了选举引擎），实际=" + snapshot.resultPayloadJson());
        assertTrue(snapshot.quorumSatisfied(), "全员参与应满足双 2/3");
        assertNotNull(snapshot.attestationTxHash());
        assertTrue(snapshot.attestationTxHash().startsWith("STUB-"));

        Integer finalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM t_voting_subject WHERE subject_id = ?", Integer.class, subjectId);
        assertEquals(5, finalStatus, "议题应被翻转为 SETTLED(5)");
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
