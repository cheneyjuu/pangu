// 关联业务：验证正式表决在锁定时冻结表决人名册，并以同一台账承接线上票与纸质票。
package com.pangu.bootstrap.voting;

import com.pangu.application.voting.VotingExecutionService;
import com.pangu.application.voting.VotingExecutionService.CastBallotCommand;
import com.pangu.application.voting.VotingExecutionService.CreatePackageCommand;
import com.pangu.application.voting.VotingExecutionService.RecordDeliveryCommand;
import com.pangu.application.voting.VoteSubmissionService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.command.CastVoteCommand;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.VotingSubjectActions;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class VotingExecutionFlowTest {

    private static final long TENANT_ID = 10001L;
    private static final long BUILDING_ID = 30002L;
    private static final long ACTOR_USER_ID = 800101L;
    private static final long FIRST_OPID = 2L;
    private static final long FIRST_UID = 70002L;
    private static final long OUT_OF_SCOPE_OPID = 4L;
    private static final String TITLE_PREFIX = "IT-统一表决内核-";
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);

    @Autowired private VotingExecutionService votingExecutionService;
    @Autowired private VoteSubmissionService voteSubmissionService;
    @Autowired private VotingSubjectRepository votingSubjectRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private int originalAccountStatus;
    private BigDecimal originalOwnerArea;

    @BeforeEach
    void rememberFixture() {
        originalAccountStatus = jdbcTemplate.queryForObject(
                "SELECT account_status FROM c_owner_property WHERE opid = ?", Integer.class, FIRST_OPID);
        originalOwnerArea = jdbcTemplate.queryForObject(
                "SELECT build_area FROM c_owner_property WHERE opid = ?", BigDecimal.class, FIRST_OPID);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update(
                "UPDATE c_owner_property SET account_status = ?, build_area = ? WHERE opid = ?",
                originalAccountStatus, originalOwnerArea, FIRST_OPID);
        jdbcTemplate.update("DELETE FROM t_voting_result WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE title LIKE ?)", TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_voting_ballot_record WHERE package_id IN (SELECT package_id FROM t_voting_execution_package WHERE business_type = 'OWNERS_ASSEMBLY' AND business_reference_id >= 990000000)");
        jdbcTemplate.update("DELETE FROM t_voting_delivery_record WHERE package_id IN (SELECT package_id FROM t_voting_execution_package WHERE business_type = 'OWNERS_ASSEMBLY' AND business_reference_id >= 990000000)");
        jdbcTemplate.update("UPDATE t_voting_execution_package SET status = 'DRAFT', package_hash = NULL, electorate_snapshot_id = NULL, frozen_at = NULL WHERE business_type = 'OWNERS_ASSEMBLY' AND business_reference_id >= 990000000");
        jdbcTemplate.update("DELETE FROM t_voting_electorate_snapshot WHERE package_id IN (SELECT package_id FROM t_voting_execution_package WHERE business_type = 'OWNERS_ASSEMBLY' AND business_reference_id >= 990000000)");
        jdbcTemplate.update("DELETE FROM t_voting_execution_package WHERE business_type = 'OWNERS_ASSEMBLY' AND business_reference_id >= 990000000");
        jdbcTemplate.update("DELETE FROM t_vote_item WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE title LIKE ?)", TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_voting_denominator_item_snapshot WHERE snapshot_id IN (SELECT snapshot_id FROM t_voting_denominator_snapshot WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE title LIKE ?))", TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_voting_denominator_snapshot WHERE subject_id IN (SELECT subject_id FROM t_voting_subject WHERE title LIKE ?)", TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE title LIKE ?", TITLE_PREFIX + "%");
    }

    @Test
    void frozenRosterIgnoresAccountStateAndKeepsAreaAfterLiveOwnershipChanges() {
        jdbcTemplate.update("UPDATE c_owner_property SET account_status = 3 WHERE opid = ?", FIRST_OPID);
        VotingSubject subject = createSubject();
        VotingExecutionPackage ballotPackage = createPackage(subject, 990000001L);

        votingExecutionService.freeze(ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());
        votingExecutionService.open(ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());

        jdbcTemplate.update("UPDATE c_owner_property SET build_area = 999.99 WHERE opid = ?", FIRST_OPID);
        votingExecutionService.recordDelivery(new RecordDeliveryCommand(
                ballotPackage.getPackageId(), TENANT_ID, FIRST_OPID, VoteChannel.PAPER,
                "DOOR_TO_DOOR", SHA_A, ACTOR_USER_ID, Instant.now()));
        long voteId = votingExecutionService.cast(new CastBallotCommand(
                ballotPackage.getPackageId(), subject.getSubjectId(), TENANT_ID,
                FIRST_OPID, FIRST_UID, VoteChoice.SUPPORT, VoteChannel.PAPER,
                SHA_B, null, ACTOR_USER_ID, Instant.now()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT property_area FROM t_vote_item WHERE vote_id = ?", BigDecimal.class, voteId))
                .isEqualByComparingTo("85.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_voting_electorate_item_snapshot WHERE snapshot_id = "
                        + "(SELECT electorate_snapshot_id FROM t_voting_execution_package WHERE package_id = ?)",
                Long.class, ballotPackage.getPackageId())).isEqualTo(2L);
    }

    @Test
    void frozenRosterRejectsOutOfScopePropertyAndCrossChannelDuplicate() {
        VotingSubject subject = createSubject();
        VotingExecutionPackage ballotPackage = createPackage(subject, 990000002L);
        votingExecutionService.freeze(ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());
        votingExecutionService.open(ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());

        assertThatThrownBy(() -> votingExecutionService.cast(new CastBallotCommand(
                ballotPackage.getPackageId(), subject.getSubjectId(), TENANT_ID,
                OUT_OF_SCOPE_OPID, FIRST_UID, VoteChoice.SUPPORT, VoteChannel.ONLINE,
                null, SHA_A, null, Instant.now())))
                .isInstanceOf(VotingExecutionService.VotingExecutionException.class)
                .hasMessageContaining("冻结表决人名册");

        votingExecutionService.recordDelivery(new RecordDeliveryCommand(
                ballotPackage.getPackageId(), TENANT_ID, FIRST_OPID, VoteChannel.PAPER,
                "DOOR_TO_DOOR", SHA_A, ACTOR_USER_ID, Instant.now()));
        votingExecutionService.cast(new CastBallotCommand(
                ballotPackage.getPackageId(), subject.getSubjectId(), TENANT_ID,
                FIRST_OPID, FIRST_UID, VoteChoice.SUPPORT, VoteChannel.PAPER,
                SHA_B, null, ACTOR_USER_ID, Instant.now()));

        votingExecutionService.recordDelivery(new RecordDeliveryCommand(
                ballotPackage.getPackageId(), TENANT_ID, FIRST_OPID, VoteChannel.ONLINE,
                "SYSTEM_NOTICE", SHA_B, ACTOR_USER_ID, Instant.now()));
        assertThatThrownBy(() -> votingExecutionService.cast(new CastBallotCommand(
                ballotPackage.getPackageId(), subject.getSubjectId(), TENANT_ID,
                FIRST_OPID, FIRST_UID, VoteChoice.AGAINST, VoteChannel.ONLINE,
                null, SHA_A, null, Instant.now())))
                .isInstanceOf(VotingExecutionService.VotingExecutionException.class)
                .hasMessageContaining("已有有效票");
    }

    @Test
    void settlementPersistsExecutionPackageAndFrozenRosterTrace() {
        VotingSubject subject = createSubject();
        VotingExecutionPackage ballotPackage = createPackage(subject, 990000003L);
        VotingExecutionPackage frozen = votingExecutionService.freeze(
                ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());
        votingExecutionService.open(ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());

        votingExecutionService.closeAndSettle(
                ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID,
                subject.getVoteEndAt().plus(1, ChronoUnit.SECONDS));

        Map<String, Object> trace = jdbcTemplate.queryForMap("""
                SELECT execution_package_id, electorate_snapshot_id,
                       proposal_snapshot_hash, rule_snapshot_hash, execution_package_hash
                FROM t_voting_result
                WHERE subject_id = ?
                """, subject.getSubjectId());
        assertThat(trace.get("execution_package_id")).isEqualTo(ballotPackage.getPackageId());
        assertThat(trace.get("electorate_snapshot_id")).isEqualTo(frozen.getElectorateSnapshotId());
        assertThat(trace.get("proposal_snapshot_hash")).isEqualTo(SHA_A);
        assertThat(trace.get("rule_snapshot_hash")).isEqualTo(SHA_B);
        assertThat(trace.get("execution_package_hash")).isEqualTo(frozen.getPackageHash());
    }

    @Test
    void formalPackageRejectsLegacySingleSubjectOnlineSubmission() {
        VotingSubject subject = createSubject();
        VotingExecutionPackage ballotPackage = createPackage(subject, 990000004L);
        votingExecutionService.freeze(ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());
        votingExecutionService.open(ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());

        CastVoteCommand command = new CastVoteCommand(
                subject.getSubjectId(), FIRST_UID, TENANT_ID, FIRST_OPID,
                null, VoteChoice.SUPPORT, SHA_A, VoteChannel.ONLINE);
        assertThatThrownBy(() -> voteSubmissionService.cast(command))
                .isInstanceOf(VotingApplicationException.class)
                .hasMessageContaining("业主大会表决页面")
                .hasMessageContaining("统一提交");
    }

    @Test
    void concurrentPaperAndOnlineSubmissionsKeepOnlyTheFirstValidBallot() throws Exception {
        VotingSubject subject = createSubject();
        VotingExecutionPackage ballotPackage = createPackage(subject, 990000005L);
        votingExecutionService.freeze(ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());
        votingExecutionService.open(ballotPackage.getPackageId(), TENANT_ID, ACTOR_USER_ID, Instant.now());
        votingExecutionService.recordDelivery(new RecordDeliveryCommand(
                ballotPackage.getPackageId(), TENANT_ID, FIRST_OPID, VoteChannel.PAPER,
                "DOOR_TO_DOOR", SHA_A, ACTOR_USER_ID, Instant.now()));
        votingExecutionService.recordDelivery(new RecordDeliveryCommand(
                ballotPackage.getPackageId(), TENANT_ID, FIRST_OPID, VoteChannel.ONLINE,
                "OWNER_ONLINE_ACKNOWLEDGEMENT", SHA_B, ACTOR_USER_ID, Instant.now()));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> paper = executor.submit(() -> concurrentCast(
                    ready, start, ballotPackage, subject, VoteChannel.PAPER, VoteChoice.SUPPORT));
            Future<String> online = executor.submit(() -> concurrentCast(
                    ready, start, ballotPackage, subject, VoteChannel.ONLINE, VoteChoice.AGAINST));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> outcomes = List.of(
                    paper.get(10, TimeUnit.SECONDS), online.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).contains("DUPLICATE");
            assertThat(outcomes).containsAnyOf("PAPER", "ONLINE");
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_voting_ballot_record WHERE package_id = ? AND valid_flag = 1",
                Long.class, ballotPackage.getPackageId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_vote_item WHERE subject_id = ? AND opid = ?",
                Long.class, subject.getSubjectId(), FIRST_OPID)).isEqualTo(1L);
    }

    private String concurrentCast(
            CountDownLatch ready,
            CountDownLatch start,
            VotingExecutionPackage ballotPackage,
            VotingSubject subject,
            VoteChannel channel,
            VoteChoice choice) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发收票测试未能同步启动");
        }
        try {
            votingExecutionService.castRecord(new CastBallotCommand(
                    ballotPackage.getPackageId(), subject.getSubjectId(), TENANT_ID,
                    FIRST_OPID, FIRST_UID, choice, channel,
                    channel == VoteChannel.PAPER ? SHA_A : null,
                    channel == VoteChannel.ONLINE ? SHA_B : null,
                    channel == VoteChannel.PAPER ? ACTOR_USER_ID : null,
                    Instant.now()));
            return channel.name();
        } catch (VotingExecutionService.VotingExecutionException failure) {
            assertThat(failure.getMessage()).contains("已有有效票");
            return "DUPLICATE";
        }
    }

    private VotingSubject createSubject() {
        Instant now = Instant.now();
        VotingSubject subject = VotingSubjectActions.open(
                TENANT_ID,
                SubjectType.GENERAL,
                VotingScope.BUILDING,
                BUILDING_ID,
                TITLE_PREFIX + System.nanoTime(),
                now.minus(1, ChronoUnit.MINUTES),
                now.plus(1, ChronoUnit.HOURS),
                ACTOR_USER_ID,
                null);
        return votingSubjectRepository.insert(subject);
    }

    private VotingExecutionPackage createPackage(VotingSubject subject, long businessReferenceId) {
        VotingExecutionPackage ballotPackage = votingExecutionService.create(new CreatePackageCommand(
                TENANT_ID,
                VotingExecutionPackage.BusinessType.OWNERS_ASSEMBLY,
                businessReferenceId,
                "TEST_PROPOSAL",
                businessReferenceId,
                SHA_A,
                "TEST_RULE",
                businessReferenceId,
                SHA_B,
                VotingScope.BUILDING,
                BUILDING_ID,
                VotingExecutionPackage.CollectionMode.PAPER_AND_ONLINE,
                subject.getVoteStartAt(),
                subject.getVoteEndAt(),
                ACTOR_USER_ID));
        votingExecutionService.attachSubject(
                ballotPackage.getPackageId(), TENANT_ID, subject.getSubjectId(), ACTOR_USER_ID);
        return ballotPackage;
    }
}
