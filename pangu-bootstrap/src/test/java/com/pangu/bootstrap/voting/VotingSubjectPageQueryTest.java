package com.pangu.bootstrap.voting;

import com.pangu.domain.common.Page;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VotingSubjectRepository#pageForWorkbench} 管理端议题工作台分页查询集成测试。
 *
 * <p>覆盖 M4-1 读侧分页范式的关键正确性：
 * <ul>
 *   <li>租户隔离：只返本租户议题，不串其他租户；</li>
 *   <li>status / type 可选筛选；</li>
 *   <li>不限筛选时覆盖全部状态（含 DRAFT/CANCELLED，区别于业主可见列表）；</li>
 *   <li>翻页：total 不随页码变化，offset/limit 生效。</li>
 * </ul>
 *
 * <p>用独立测试租户 {@code 96001 / 96002} + {@code ADMIN-PAGE-} 前缀种子隔离。
 */
@SpringBootTest
public class VotingSubjectPageQueryTest {

    private static final long TENANT_A = 96001L;
    private static final long TENANT_B = 96002L;
    private static final String TITLE_PREFIX = "ADMIN-PAGE-";

    private static final int TYPE_ELECTION = 1;
    private static final int TYPE_MAJOR = 2;
    private static final int TYPE_GENERAL = 3;

    private static final int ST_DRAFT = 1;
    private static final int ST_PUBLISHED = 2;
    private static final int ST_VOTING = 3;
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

    /** 种一条议题；ELECTION 需带 max_winners>=1（trigger 11）；CANCELLED 需带 cancel_* 字段（trigger 12）。 */
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

    @Test
    public void unfiltered_returnsAllStatusesForTenantOnly() {
        seedSubject(TENANT_A, "DRAFT", TYPE_GENERAL, ST_DRAFT);
        seedSubject(TENANT_A, "VOTING", TYPE_MAJOR, ST_VOTING);
        seedSubject(TENANT_A, "CANCELLED", TYPE_GENERAL, ST_CANCELLED);
        seedSubject(TENANT_B, "OTHER", TYPE_GENERAL, ST_VOTING);

        Page<VotingSubject> page = subjectRepository.pageForWorkbench(
                TENANT_A, null, null, null, 1, 20);

        assertEquals(3, page.total(), "应覆盖本租户全部状态（含 DRAFT/CANCELLED）");
        assertEquals(3, page.items().size());
        assertTrue(page.items().stream().allMatch(s -> TENANT_A == s.getTenantId()),
                "不应串入其他租户议题");
    }

    @Test
    public void statusFilter_appliesPrecisely() {
        seedSubject(TENANT_A, "DRAFT1", TYPE_GENERAL, ST_DRAFT);
        seedSubject(TENANT_A, "DRAFT2", TYPE_MAJOR, ST_DRAFT);
        seedSubject(TENANT_A, "VOTING", TYPE_GENERAL, ST_VOTING);

        Page<VotingSubject> page = subjectRepository.pageForWorkbench(
                TENANT_A, null, SubjectStatus.DRAFT, null, 1, 20);

        assertEquals(2, page.total());
        assertTrue(page.items().stream().allMatch(s -> s.getStatus() == SubjectStatus.DRAFT));
    }

    @Test
    public void typeFilter_appliesPrecisely() {
        seedSubject(TENANT_A, "GEN", TYPE_GENERAL, ST_PUBLISHED);
        seedSubject(TENANT_A, "MAJ", TYPE_MAJOR, ST_PUBLISHED);
        seedSubject(TENANT_A, "ELE", TYPE_ELECTION, ST_PUBLISHED);

        Page<VotingSubject> page = subjectRepository.pageForWorkbench(
                TENANT_A, null, null, SubjectType.GENERAL, 1, 20);

        assertEquals(1, page.total());
        assertEquals(SubjectType.GENERAL, page.items().get(0).getSubjectType());
    }

    @Test
    public void statusAndTypeFilter_combined() {
        seedSubject(TENANT_A, "GEN-DRAFT", TYPE_GENERAL, ST_DRAFT);
        seedSubject(TENANT_A, "GEN-VOTING", TYPE_GENERAL, ST_VOTING);
        seedSubject(TENANT_A, "MAJ-DRAFT", TYPE_MAJOR, ST_DRAFT);

        Page<VotingSubject> page = subjectRepository.pageForWorkbench(
                TENANT_A, null, SubjectStatus.DRAFT, SubjectType.GENERAL, 1, 20);

        assertEquals(1, page.total());
        assertEquals(SubjectStatus.DRAFT, page.items().get(0).getStatus());
        assertEquals(SubjectType.GENERAL, page.items().get(0).getSubjectType());
    }

    @Test
    public void pagination_totalStableAcrossPages() {
        for (int i = 0; i < 5; i++) {
            seedSubject(TENANT_A, "P" + i, TYPE_GENERAL, ST_PUBLISHED);
        }

        Page<VotingSubject> p1 = subjectRepository.pageForWorkbench(
                TENANT_A, null, null, null, 1, 2);
        Page<VotingSubject> p2 = subjectRepository.pageForWorkbench(
                TENANT_A, null, null, null, 2, 2);
        Page<VotingSubject> p3 = subjectRepository.pageForWorkbench(
                TENANT_A, null, null, null, 3, 2);

        assertEquals(5, p1.total());
        assertEquals(5, p2.total());
        assertEquals(5, p3.total());
        assertEquals(2, p1.items().size());
        assertEquals(2, p2.items().size());
        assertEquals(1, p3.items().size(), "第三页应只剩 1 条");

        // 三页主键不重叠，合计覆盖全部 5 条
        List<Long> ids = List.of(
                p1.items().get(0).getSubjectId(), p1.items().get(1).getSubjectId(),
                p2.items().get(0).getSubjectId(), p2.items().get(1).getSubjectId(),
                p3.items().get(0).getSubjectId());
        assertEquals(5, ids.stream().distinct().count(), "翻页主键不应重叠");
    }

    @Test
    public void emptyResult_returnsZeroTotal() {
        Page<VotingSubject> page = subjectRepository.pageForWorkbench(
                TENANT_A, null, null, null, 1, 20);
        assertEquals(0, page.total());
        assertFalse(page.items().iterator().hasNext());
    }
}
