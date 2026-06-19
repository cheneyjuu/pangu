package com.pangu.bootstrap.web;

import com.pangu.application.voting.VotingApplicationService;
import com.pangu.application.voting.command.SettleSubjectCommand;
import com.pangu.application.waiver.WaiverApplicationService;
import com.pangu.application.waiver.command.CommitteeReviewCommand;
import com.pangu.application.waiver.command.StreetReviewCommand;
import com.pangu.application.waiver.command.SubmitDraftCommand;
import com.pangu.domain.model.waiver.PartyRatioWaiver;
import com.pangu.domain.model.waiver.WaiverStatus;
import com.pangu.domain.repository.VotingResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试：
 *
 * <p>覆盖两条主链路：
 * <ol>
 *   <li><b>Waiver 工作流</b>：居委会发起 → 居委会初审通过 → 街道办终审通过 →
 *       APPROVED 落定 + payloadHash 锁定（{@link PartyRatioWaiver} 状态机全链路）。</li>
 *   <li><b>Voting 结算工作流</b>：议题发起 → 投票 → 截止 → 结算 →
 *       校验 {@link VotingResultRepository.Snapshot#attestationTxHash()} 形如 STUB-{n}（司法链 outbox stub）。</li>
 * </ol>
 *
 * <p>本期 ELECTION 议题尚未接入完整 ElectionSubject 加载链路（见
 * {@code VotingApplicationService} 注释），因此结算流程使用 GENERAL 议题；
 * Waiver 流程独立验证（业务上 Waiver 主要服务 ELECTION，但应用层不限定 subject_type）。
 *
 * <p>使用真实 PostgreSQL + Redis（{@code docker compose up -d}）。
 */
@SpringBootTest
public class ElectionWorkflowIntegrationTest {

    private static final long TEST_TENANT_ID = 99003L;
    private static final long TEST_BUILDING = 990003L;

    private static final long INITIATOR = 70010L;     // 居委会发起人
    private static final long COMMITTEE_APPROVER = 70011L;  // 居委会审批人（不同人）
    private static final long STREET_APPROVER = 80001L;     // 街道办终审人

    @Autowired
    private WaiverApplicationService waiverApplicationService;

    @Autowired
    private VotingApplicationService votingApplicationService;

    @Autowired
    private VotingResultRepository votingResultRepository;

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
        // 顺序：result/snapshots/votes → waiver/comparison → candidate → subject → owner_property → user
        jdbcTemplate.update(
                "DELETE FROM t_voting_result "
                        + "WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_voting_denominator_item_snapshot "
                        + "WHERE snapshot_id IN (SELECT snapshot_id FROM t_voting_denominator_snapshot "
                        + "                       WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?))",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_voting_denominator_snapshot "
                        + "WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_vote_item "
                        + "WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_waiver_snapshot_comparison "
                        + "WHERE waiver_id IN (SELECT waiver_id FROM t_party_ratio_waiver WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM t_party_ratio_waiver WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update(
                "DELETE FROM t_election_candidate "
                        + "WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE tenant_id = ?)",
                TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE tenant_id = ?", TEST_TENANT_ID);
        jdbcTemplate.update("DELETE FROM c_owner_property WHERE tenant_id = ?", TEST_TENANT_ID);
        // 测试用户 phone 以 770/770 开头共 11 位；和生产数据隔离开
        // M1 RBAC 重构后：phone 在 t_account；先删 c_user 再删 t_account
        jdbcTemplate.update(
                "DELETE FROM c_user WHERE account_id IN ("
                        + "SELECT account_id FROM t_account WHERE phone LIKE '770%' AND length(phone) = 11)");
        jdbcTemplate.update("DELETE FROM t_account WHERE phone LIKE '770%' AND length(phone) = 11");
        jdbcTemplate.update("DELETE FROM t_outbox_event WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    private Long insertUser(String phone) {
        Long accountId = jdbcTemplate.queryForObject(
                "INSERT INTO t_account(phone, real_name, real_name_verified, status) "
                        + "VALUES(?, '测试用户', 0, 1) RETURNING account_id",
                Long.class, phone);
        return jdbcTemplate.queryForObject(
                "INSERT INTO c_user(account_id, auth_level) VALUES(?, 1) RETURNING uid",
                Long.class, accountId);
    }

    private Long insertElectionSubject() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, party_ratio_floor, max_winners) "
                        + "VALUES(?, ?, 1, 1, 2, 0.50, 5) RETURNING subject_id",
                Long.class,
                TEST_TENANT_ID, "选举议题-e2e");
    }

    private Long insertGeneralSubject() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, party_ratio_floor) "
                        + "VALUES(?, ?, 3, 1, 3, 0.50) RETURNING subject_id",  // status=3 VOTING
                Long.class,
                TEST_TENANT_ID, "一般议题-e2e");
    }

    private void insertCandidate(Long subjectId, Long uid, String name, int isParty) {
        jdbcTemplate.update(
                "INSERT INTO t_election_candidate(subject_id, uid, name, is_party_member, qualification_status) "
                        + "VALUES(?, ?, ?, ?, 2)",
                subjectId, uid, name, isParty);
    }

    private long insertOwnership(Long uid, Long roomId, BigDecimal area) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO c_owner_property(uid, tenant_id, building_id, room_id, build_area, is_voting_delegate, account_status) "
                        + "VALUES(?, ?, ?, ?, ?, 1, 1) RETURNING opid",
                Long.class,
                uid, TEST_TENANT_ID, TEST_BUILDING, roomId, area);
    }

    private void insertVote(Long subjectId, long opid, Long uid, BigDecimal area, int choice) {
        jdbcTemplate.update(
                "INSERT INTO t_vote_item(subject_id, opid, uid, target_id, property_area, choice) "
                        + "VALUES(?, ?, ?, NULL, ?, ?)",
                subjectId, opid, uid, area, choice);
    }

    @Test
    public void waiverWorkflow_doubleSign_endsApproved_withLockedPayloadHash() {
        // 1. 创建 ELECTION 议题 + 6 名候选人（2 党员 + 4 非党员，自然比 33%）
        Long subjectId = insertElectionSubject();
        for (int i = 0; i < 6; i++) {
            insertCandidate(subjectId, 800000L + i, "候选人" + i, i < 2 ? 1 : 0);
        }

        // 2. 居委会发起 waiver（状态 → PENDING_COMMITTEE）
        SubmitDraftCommand draftCmd = new SubmitDraftCommand(
                subjectId, TEST_TENANT_ID, INITIATOR, new BigDecimal("0.30"),
                "本小区共有产权房比例较高党员人数严重不足经多次组织居民代表协商发动报名仍无法凑足候选人池所需的党员数量特申请将党员比例下限放宽至30%恳请居委会及街道办予以审议批准",
                null
        );
        PartyRatioWaiver submitted = waiverApplicationService.submitDraft(draftCmd);
        assertEquals(WaiverStatus.PENDING_COMMITTEE, submitted.getStatus());
        assertNotNull(submitted.getWaiverId());
        assertEquals(2L, submitted.getPartyPoolSize());
        assertEquals(6L, submitted.getTotalEligibleSize());

        // 3. 居委会初审通过（不同审批人，dept_type=2）→ PENDING_STREET
        PartyRatioWaiver afterCommittee = waiverApplicationService.reviewByCommittee(
                new CommitteeReviewCommand(submitted.getWaiverId(), COMMITTEE_APPROVER, true, "情况属实"));
        assertEquals(WaiverStatus.PENDING_STREET, afterCommittee.getStatus());
        assertEquals(COMMITTEE_APPROVER, afterCommittee.getCommitteeApprover());
        assertNotNull(afterCommittee.getCommitteeApprovalAt());

        // 4. 街道办终审通过（dept_type=1）→ APPROVED + payloadHash 锁定
        PartyRatioWaiver afterStreet = waiverApplicationService.reviewByStreet(
                new StreetReviewCommand(submitted.getWaiverId(), STREET_APPROVER, true, "终审通过"));
        assertEquals(WaiverStatus.APPROVED, afterStreet.getStatus());
        assertEquals(STREET_APPROVER, afterStreet.getStreetApprover());
        assertNotNull(afterStreet.getStreetApprovalAt());
        assertNotNull(afterStreet.getLocalPayloadHash(), "APPROVED 后必须锁定 local_payload_hash");
        assertEquals(64, afterStreet.getLocalPayloadHash().length(),
                "local_payload_hash 必须是 64-hex SHA256");
        assertNotNull(afterStreet.getLocalPayloadLockedAt());

        // 5. DB 视角验证：waiver 行的 status / hashes 实际落库
        Map<String, Object> waiverRow = jdbcTemplate.queryForMap(
                "SELECT status, local_payload_hash, committee_approver, street_approver "
                        + "FROM t_party_ratio_waiver WHERE waiver_id = ?",
                submitted.getWaiverId());
        assertEquals(WaiverStatus.APPROVED.getDbValue(),
                ((Number) waiverRow.get("status")).intValue());
        assertEquals(afterStreet.getLocalPayloadHash(), waiverRow.get("local_payload_hash"));
        assertEquals(COMMITTEE_APPROVER, ((Number) waiverRow.get("committee_approver")).longValue());
        assertEquals(STREET_APPROVER, ((Number) waiverRow.get("street_approver")).longValue());
    }

    @Test
    public void votingSettleWorkflow_passes_andEmitsAttestationTxHash() {
        // 1. 3 名业主 + 3 套房（每套 100 ㎡）
        Long uid1 = insertUser("77010000001");
        Long uid2 = insertUser("77010000002");
        Long uid3 = insertUser("77010000003");
        long opid1 = insertOwnership(uid1, 600001L, new BigDecimal("100.00"));
        long opid2 = insertOwnership(uid2, 600002L, new BigDecimal("100.00"));
        long opid3 = insertOwnership(uid3, 600003L, new BigDecimal("100.00"));

        // 2. 创建 GENERAL 议题（status=VOTING 让 settle 接受）
        Long subjectId = insertGeneralSubject();

        // 3. 全员投赞成（全部 100% 参与，>2/3 quorum 必满足；>1/2 通过）
        insertVote(subjectId, opid1, uid1, new BigDecimal("100.00"), 1);
        insertVote(subjectId, opid2, uid2, new BigDecimal("100.00"), 1);
        insertVote(subjectId, opid3, uid3, new BigDecimal("100.00"), 1);

        // 4. 触发结算（手工触发；调度器只负责到期 push 命令）
        VotingResultRepository.Snapshot snapshot = votingApplicationService.settle(
                new SettleSubjectCommand(subjectId, "MANUAL"));

        // 5. 引擎结算正确性
        assertTrue(snapshot.quorumSatisfied(), "全员参会必满足双 2/3");
        assertTrue(snapshot.passed(), "GENERAL 议题全员赞成必通过");
        assertEquals(0, new BigDecimal("300.00").compareTo(snapshot.participatingArea()));
        assertEquals(3L, snapshot.participatingOwnerCount());
        assertEquals(1, snapshot.statisticsVersion());
        assertNotNull(snapshot.denominatorSnapshotId());

        // 6. 司法链 stub：tx_hash 形如 "STUB-{eventId}"，且对应 outbox 行已落
        String txHash = snapshot.attestationTxHash();
        assertNotNull(txHash);
        assertTrue(txHash.startsWith("STUB-"),
                "本期司法链是 outbox stub，tx_hash 必须形如 STUB-{eventId}，实际=" + txHash);
        long eventId = Long.parseLong(txHash.substring("STUB-".length()));
        Integer outboxRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_outbox_event WHERE event_id = ? AND tenant_id = ?",
                Integer.class, eventId, TEST_TENANT_ID);
        assertEquals(1, outboxRows, "outbox 表中应有对应记录");

        // 7. 议题 status 翻转到 SETTLED
        Integer finalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM t_voting_subject WHERE subject_id = ?",
                Integer.class, subjectId);
        assertEquals(5, finalStatus, "议题应被翻转为 SETTLED(5)");

        // 8. 幂等：再次调用 settle 应返回相同 snapshot，不重复 settle
        VotingResultRepository.Snapshot second = votingApplicationService.settle(
                new SettleSubjectCommand(subjectId, "MANUAL"));
        assertEquals(snapshot.attestationTxHash(), second.attestationTxHash(),
                "已 SETTLED 的议题再次调用应返回历史 tx_hash（幂等）");
        assertEquals(snapshot.statisticsVersion(), second.statisticsVersion());
    }

    @Test
    public void votingSettle_quorumFailed_passedFalse_butStillEmitsAttestation() {
        // 极端：3 业主中只 1 人参会（33%）→ 双 2/3 不达 → 不成立 → passed=false
        Long uid1 = insertUser("77020000001");
        Long uid2 = insertUser("77020000002");
        Long uid3 = insertUser("77020000003");
        long opid1 = insertOwnership(uid1, 700001L, new BigDecimal("100.00"));
        insertOwnership(uid2, 700002L, new BigDecimal("100.00"));
        insertOwnership(uid3, 700003L, new BigDecimal("100.00"));

        Long subjectId = insertGeneralSubject();
        insertVote(subjectId, opid1, uid1, new BigDecimal("100.00"), 1);  // 仅 1 人投票

        VotingResultRepository.Snapshot snap = votingApplicationService.settle(
                new SettleSubjectCommand(subjectId, "MANUAL"));

        assertFalse(snap.quorumSatisfied(), "1/3 参与率应不达双 2/3");
        assertFalse(snap.passed(), "未成立的会议不可能 passed");
        // 即使未通过，tx_hash 仍应生成（审计需要存证）
        assertNotNull(snap.attestationTxHash());
        assertTrue(snap.attestationTxHash().startsWith("STUB-"));
    }
}
