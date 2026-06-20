package com.pangu.bootstrap.handover;

import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 换届熔断「检测 seam」集成测试：直接打靶
 * {@link VotingSubjectRepository#findActiveElectionSubjectId(Long)} 的状态语义。
 *
 * <p>「在途换届」≙ {@code subject_type = ELECTION(1)} 且 {@code status ∈ {PUBLISHED(2), VOTING(3), CLOSED(4)}}。
 * <ul>
 *   <li>ELECTION 的 PUBLISHED / VOTING / CLOSED → 命中；</li>
 *   <li>ELECTION 的 DRAFT(1) / SETTLED(5) / CANCELLED(6) → 空（结算/撤销后自动解除熔断）；</li>
 *   <li>非 ELECTION（GENERAL(3) / MAJOR(2)）即便 VOTING 也不计入；</li>
 *   <li>跨租户隔离：A 租户的在途选举不影响 B 租户。</li>
 * </ul>
 *
 * <p>用独立测试租户 {@code 95001 / 95002} + {@code HANDOVER-DETECT-} 前缀种子隔离，
 * 纯查询语义不涉及真实业主/seed 用户。
 */
@SpringBootTest
public class HandoverDetectionTest {

    private static final long TENANT_A = 95001L;
    private static final long TENANT_B = 95002L;
    private static final String TITLE_PREFIX = "HANDOVER-DETECT-";

    // 议题类型
    private static final int TYPE_ELECTION = 1;
    private static final int TYPE_MAJOR = 2;
    private static final int TYPE_GENERAL = 3;
    // 议题状态
    private static final int ST_DRAFT = 1;
    private static final int ST_PUBLISHED = 2;
    private static final int ST_VOTING = 3;
    private static final int ST_CLOSED = 4;
    private static final int ST_SETTLED = 5;
    private static final int ST_CANCELLED = 6;

    @Autowired
    private VotingSubjectRepository subjectRepository;

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
                "DELETE FROM t_voting_subject WHERE tenant_id IN (?, ?) AND title LIKE ?",
                TENANT_A, TENANT_B, TITLE_PREFIX + "%");
    }

    /** 种一条议题；ELECTION 必须带 max_winners>=1（trigger 11）；CANCELLED 必须带 cancel_* 字段（trigger 12）。 */
    private long seedSubject(long tenantId, String suffix, int subjectType, int status) {
        Integer maxWinners = subjectType == TYPE_ELECTION ? 2 : null;
        if (status == ST_CANCELLED) {
            return jdbcTemplate.queryForObject(
                    "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, "
                            + "max_winners, cancelled_at, cancelled_by_user_id, cancel_reason) "
                            + "VALUES(?, ?, ?, 1, 6, ?, CURRENT_TIMESTAMP, ?, ?) RETURNING subject_id",
                    Long.class, tenantId, TITLE_PREFIX + suffix, subjectType, maxWinners,
                    1L, "测试撤回");
        }
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, max_winners) "
                        + "VALUES(?, ?, ?, 1, ?, ?) RETURNING subject_id",
                Long.class, tenantId, TITLE_PREFIX + suffix, subjectType, status, maxWinners);
    }

    // ===== ELECTION 在途三态：命中 =====

    @Test
    public void electionPublished_detected() {
        long id = seedSubject(TENANT_A, "PUB", TYPE_ELECTION, ST_PUBLISHED);
        assertEquals(Optional.of(id), subjectRepository.findActiveElectionSubjectId(TENANT_A));
    }

    @Test
    public void electionVoting_detected() {
        long id = seedSubject(TENANT_A, "VOTE", TYPE_ELECTION, ST_VOTING);
        assertEquals(Optional.of(id), subjectRepository.findActiveElectionSubjectId(TENANT_A));
    }

    @Test
    public void electionClosed_detected() {
        long id = seedSubject(TENANT_A, "CLOSED", TYPE_ELECTION, ST_CLOSED);
        assertEquals(Optional.of(id), subjectRepository.findActiveElectionSubjectId(TENANT_A));
    }

    // ===== ELECTION 非在途三态：空（自动解除）=====

    @Test
    public void electionDraft_notDetected() {
        seedSubject(TENANT_A, "DRAFT", TYPE_ELECTION, ST_DRAFT);
        assertTrue(subjectRepository.findActiveElectionSubjectId(TENANT_A).isEmpty(),
                "DRAFT 未进入公示期，不应触发熔断");
    }

    @Test
    public void electionSettled_notDetected() {
        seedSubject(TENANT_A, "SETTLED", TYPE_ELECTION, ST_SETTLED);
        assertTrue(subjectRepository.findActiveElectionSubjectId(TENANT_A).isEmpty(),
                "SETTLED 后换届完成，熔断应自动解除");
    }

    @Test
    public void electionCancelled_notDetected() {
        seedSubject(TENANT_A, "CANCELLED", TYPE_ELECTION, ST_CANCELLED);
        assertTrue(subjectRepository.findActiveElectionSubjectId(TENANT_A).isEmpty(),
                "CANCELLED 后换届撤销，熔断应自动解除");
    }

    // ===== 非 ELECTION：即便 VOTING 也不计入 =====

    @Test
    public void generalVoting_notDetected() {
        seedSubject(TENANT_A, "GENERAL", TYPE_GENERAL, ST_VOTING);
        assertTrue(subjectRepository.findActiveElectionSubjectId(TENANT_A).isEmpty(),
                "一般决议投票中不属于换届，不应触发熔断");
    }

    @Test
    public void majorVoting_notDetected() {
        seedSubject(TENANT_A, "MAJOR", TYPE_MAJOR, ST_VOTING);
        assertTrue(subjectRepository.findActiveElectionSubjectId(TENANT_A).isEmpty(),
                "重大决议投票中不属于换届，不应触发熔断");
    }

    // ===== 跨租户隔离 =====

    @Test
    public void crossTenant_isolated() {
        seedSubject(TENANT_B, "PUB", TYPE_ELECTION, ST_PUBLISHED);
        assertTrue(subjectRepository.findActiveElectionSubjectId(TENANT_A).isEmpty(),
                "B 租户的在途换届不应影响 A 租户");
        assertTrue(subjectRepository.findActiveElectionSubjectId(TENANT_B).isPresent(),
                "B 租户自身应命中");
    }
}
