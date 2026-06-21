package com.pangu.bootstrap.voting;

import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingProgressQueryService;
import com.pangu.domain.common.Page;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VotingProgress;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.repository.VoteDetailQueryRepository;
import com.pangu.domain.repository.VotingDenominatorReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VotingProgressQueryService} 进度 / 逐户明细查询集成测试。
 *
 * <p>用独立测试租户 {@code 96101 / 96102} + {@code MP-} 前缀种子隔离。覆盖：
 * <ul>
 *   <li>进度 totals 与去重分母口径一致（总面积 / 总人数）；</li>
 *   <li>参与 / 赞成的面积与人数计算正确，双 2/3 门槛判定；</li>
 *   <li>逐户明细分页：voted / 未投、choice、authLevel 正确，total 跨页稳定；</li>
 *   <li>跨租户议题 → SUBJECT_NOT_FOUND；</li>
 *   <li>UNIT scope → 异常。</li>
 * </ul>
 */
@SpringBootTest
public class VotingProgressQueryTest {

    private static final long TENANT_A = 96101L;
    private static final long TENANT_B = 96102L;
    private static final long BUILDING = 96101001L;
    private static final String PHONE_PREFIX = "MP-";
    private static final String TITLE_PREFIX = "MP-PROG-";

    @Autowired
    private VotingProgressQueryService queryService;

    @Autowired
    private VotingDenominatorReader denominatorReader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long opid1;
    private long opid2;
    private long opid3;
    private long subjectId;

    @BeforeEach
    public void setUp() {
        cleanUp();
        // 3 套房，每套 100 面积、各 1 业主、account_status=1 → 总 300 / 3 人
        long uid1 = seedOwner("1", 3);   // L3
        long uid2 = seedOwner("2", 2);   // L2
        long uid3 = seedOwner("3", 1);   // L1
        opid1 = seedProperty(uid1, 96101101L, new BigDecimal("100.00"));
        opid2 = seedProperty(uid2, 96101102L, new BigDecimal("100.00"));
        opid3 = seedProperty(uid3, 96101103L, new BigDecimal("100.00"));

        subjectId = seedSubject(TENANT_A, "MAIN", 3, 1, 3); // GENERAL / COMMUNITY / VOTING

        // 2 户投票：opid1 赞成、opid2 反对；opid3 未投 → 参与 200/2人，赞成 100/1人
        seedVote(subjectId, opid1, uid1, new BigDecimal("100.00"), VoteChoice.SUPPORT);
        seedVote(subjectId, opid2, uid2, new BigDecimal("100.00"), VoteChoice.AGAINST);
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_vote_item WHERE subject_id IN "
                + "(SELECT subject_id FROM t_voting_subject WHERE tenant_id IN (?, ?) AND title LIKE ?)",
                TENANT_A, TENANT_B, TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_voting_subject WHERE tenant_id IN (?, ?) AND title LIKE ?",
                TENANT_A, TENANT_B, TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM c_owner_property WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbcTemplate.update("DELETE FROM c_user WHERE account_id IN "
                + "(SELECT account_id FROM t_account WHERE phone LIKE ?)", PHONE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM t_account WHERE phone LIKE ?", PHONE_PREFIX + "%");
    }

    private long seedOwner(String suffix, int authLevel) {
        Long accountId = jdbcTemplate.queryForObject(
                "INSERT INTO t_account(phone, real_name) VALUES(?, ?) RETURNING account_id",
                Long.class, PHONE_PREFIX + suffix, "MOCK-OWNER-" + suffix);
        return jdbcTemplate.queryForObject(
                "INSERT INTO c_user(account_id, auth_level) VALUES(?, ?) RETURNING uid",
                Long.class, accountId, authLevel);
    }

    private long seedProperty(long uid, long roomId, BigDecimal area) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO c_owner_property(uid, tenant_id, building_id, room_id, build_area, account_status) "
                        + "VALUES(?, ?, ?, ?, ?, 1) RETURNING opid",
                Long.class, uid, TENANT_A, BUILDING, roomId, area);
    }

    /** scope: 1=COMMUNITY/3=UNIT; status: 3=VOTING. */
    private long seedSubject(long tenantId, String suffix, int subjectType, int scope, int status) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status) "
                        + "VALUES(?, ?, ?, ?, ?) RETURNING subject_id",
                Long.class, tenantId, TITLE_PREFIX + suffix, subjectType, scope, status);
    }

    private void seedVote(long subjectId, long opid, long uid, BigDecimal area, VoteChoice choice) {
        jdbcTemplate.update(
                "INSERT INTO t_vote_item(subject_id, opid, uid, property_area, choice) VALUES(?, ?, ?, ?, ?)",
                subjectId, opid, uid, area, choice.getDbValue());
    }

    @Test
    public void progress_totalsMatchDenominator_andParticipationCorrect() {
        VotingProgress p = queryService.queryProgress(subjectId, TENANT_A);

        // 分母口径：总面积 300 / 总人数 3
        assertEquals(0, new BigDecimal("300.00").compareTo(p.totalArea()));
        assertEquals(3, p.totalOwnerCount());

        // 参与：200 面积 / 2 人（opid3 未投）
        assertEquals(0, new BigDecimal("200.00").compareTo(p.participatingArea()));
        assertEquals(2, p.participatingOwnerCount());

        // 赞成：100 面积 / 1 人
        assertEquals(0, new BigDecimal("100.00").compareTo(p.supportArea()));
        assertEquals(1L, p.supportOwnerCount());

        // 双 2/3：面积 200/300 与人数 2/3 恰好达标
        assertTrue(p.quorumSatisfied());
        assertFalse(p.settled());
    }

    @Test
    public void voteDetails_paginationAndFlagsCorrect() {
        Page<VoteDetailQueryRepository.VoteDetailRow> p1 =
                queryService.pageVoteDetails(subjectId, TENANT_A, 1, 2);
        Page<VoteDetailQueryRepository.VoteDetailRow> p2 =
                queryService.pageVoteDetails(subjectId, TENANT_A, 2, 2);

        // 应投房产 3 户，total 跨页稳定
        assertEquals(3, p1.total());
        assertEquals(3, p2.total());
        assertEquals(2, p1.items().size());
        assertEquals(1, p2.items().size());

        // opid1 已投 SUPPORT、L3
        VoteDetailQueryRepository.VoteDetailRow r1 = p1.items().stream()
                .filter(r -> r.opid().equals(opid1)).findFirst().orElseThrow();
        assertTrue(r1.voted());
        assertEquals(VoteChoice.SUPPORT, r1.choice());
        assertEquals(3, r1.authLevel());

        // opid3 未投：choice/votedAt 为空
        VoteDetailQueryRepository.VoteDetailRow r3 = p2.items().stream()
                .filter(r -> r.opid().equals(opid3)).findFirst()
                .orElseGet(() -> p1.items().stream()
                        .filter(r -> r.opid().equals(opid3)).findFirst().orElseThrow());
        assertFalse(r3.voted());
    }

    @Test
    public void crossTenantSubject_throwsSubjectNotFound() {
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> queryService.queryProgress(subjectId, TENANT_B));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_FOUND, ex.getReason());
    }

    @Test
    public void unitScope_rejectedByDenominatorReader() {
        // UNIT scope 在 DB 层即被 chk_subject_scope 禁止入库，故无法经议题落库触达；
        // 此处直接验证只读分母读取器对 UNIT 的防御性拒绝。
        assertThrows(IllegalStateException.class,
                () -> denominatorReader.previewTotals(TENANT_A, VotingScope.UNIT, null));
    }
}
