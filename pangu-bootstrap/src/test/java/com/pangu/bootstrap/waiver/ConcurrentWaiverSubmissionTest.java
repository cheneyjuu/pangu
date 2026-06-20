package com.pangu.bootstrap.waiver;

import com.pangu.application.waiver.WaiverApplicationException;
import com.pangu.application.waiver.WaiverApplicationService;
import com.pangu.application.waiver.command.SubmitDraftCommand;
import com.pangu.domain.model.waiver.PartyRatioWaiver;
import com.pangu.domain.model.waiver.WaiverStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WaiverApplicationService#submitDraft} 三层防御并发回归测试：
 *
 * <ol>
 *   <li>① Redis 红线锁（{@code lock:waiver:tenant:{T}:subject:{S}}）—— 单进程串行化；</li>
 *   <li>② DB SELECT FOR UPDATE 行锁 —— 同一 tx 内活跃 waiver 互斥；</li>
 *   <li>③ 部分唯一索引 {@code uidx_waiver_active_per_subject} —— 兜底保险。</li>
 * </ol>
 *
 * <p>本测试在 {@link CountDownLatch} 协同下让两线程几乎同瞬触发 submitDraft，
 * 期望：恰一条成功落表 + 另一条返回友好的
 * {@link WaiverApplicationException.Reason#WAIVER_ALREADY_PENDING}。
 *
 * <p>本测试需要真实 PostgreSQL + Redis（{@code docker compose up -d} 起容器后运行）。
 */
@SpringBootTest
public class ConcurrentWaiverSubmissionTest {

    /** 测试隔离用的 tenant_id，远离生产 mock 数据避免污染。 */
    private static final long TEST_TENANT_ID = 99001L;

    @Autowired
    private WaiverApplicationService waiverApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long subjectId;

    @BeforeEach
    public void setUp() {
        // 1. 清理本测试租户上次留下的痕迹（顺序：先 child，后 parent）
        cleanUp();

        // 2. 创建一个 ELECTION 议题（subject_type=1, status=2 PUBLISHED, scope=1 COMMUNITY）
        //    max_winners=5：V3.1 trigger 13 要求 ELECTION 议题必须携带 max_winners >= 1
        subjectId = jdbcTemplate.queryForObject(
                "INSERT INTO t_voting_subject(tenant_id, title, subject_type, scope, status, party_ratio_floor, max_winners) "
                        + "VALUES(?, ?, 1, 1, 2, 0.50, 5) RETURNING subject_id",
                Long.class,
                TEST_TENANT_ID, "并发测试议题-" + System.currentTimeMillis());

        // 3. 候选人池：6 名 APPROVED 候选人（2 党员 + 4 非党员）→ 自然比例 33%（< 50%）
        for (int i = 0; i < 6; i++) {
            jdbcTemplate.update(
                    "INSERT INTO t_election_candidate(subject_id, uid, name, is_party_member, qualification_status) "
                            + "VALUES(?, ?, ?, ?, 2)",
                    subjectId, 800000L + i, "候选人" + i, i < 2 ? 1 : 0);
        }
    }

    @AfterEach
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        // 子表清理：snapshot_comparison（FK 引用 waiver） → waiver → candidate → subject
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
    }

    @Test
    public void twoConcurrentSubmissions_exactlyOneSucceeds_otherGetsFriendlyAlreadyPending() throws Exception {
        // 两个线程同时调用 submitDraft，仅 subject_id 相同 → 触发同一把 Redis 锁 + 同议题活跃 waiver 唯一约束
        SubmitDraftCommand cmd = new SubmitDraftCommand(
                subjectId,
                TEST_TENANT_ID,
                7001L,           // initiatorUserId
                new BigDecimal("0.30"),
                "本小区共有产权房比例较高党员人数严重不足经多次组织居民代表协商发动报名仍无法凑足候选人池所需的党员数量特申请将党员比例下限放宽至30%恳请居委会及街道办予以审议批准",
                null
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);     // 两线程已就绪
        CountDownLatch fire = new CountDownLatch(1);      // 主线程发令枪
        CountDownLatch done = new CountDownLatch(2);      // 两线程都跑完

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyPendingCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new ArrayList<>();
        AtomicReference<PartyRatioWaiver> winner = new AtomicReference<>();

        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        fire.await();    // 等主线程开闸
                        try {
                            PartyRatioWaiver w = waiverApplicationService.submitDraft(cmd);
                            successCount.incrementAndGet();
                            winner.compareAndSet(null, w);
                        } catch (WaiverApplicationException e) {
                            if (e.getReason() == WaiverApplicationException.Reason.WAIVER_ALREADY_PENDING) {
                                alreadyPendingCount.incrementAndGet();
                            } else {
                                synchronized (unexpectedErrors) { unexpectedErrors.add(e); }
                            }
                        } catch (Throwable t) {
                            synchronized (unexpectedErrors) { unexpectedErrors.add(t); }
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "两线程未在 5s 内就绪");
            fire.countDown();   // 同瞬开闸
            assertTrue(done.await(15, TimeUnit.SECONDS), "并发提交未在 15s 内全部返回");
        } finally {
            pool.shutdownNow();
        }

        // 不应出现意外异常
        assertTrue(unexpectedErrors.isEmpty(),
                "出现非预期异常：" + unexpectedErrors);

        // 强语义断言：恰一条成功 + 一条 ALREADY_PENDING
        assertEquals(1, successCount.get(), "应恰有 1 个线程成功落库");
        assertEquals(1, alreadyPendingCount.get(),
                "另一线程应被三层防御拦截并返回 WAIVER_ALREADY_PENDING");

        // 数据库视角验证：本议题应只有 1 行活跃 waiver
        Integer activeRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_party_ratio_waiver "
                        + "WHERE subject_id = ? AND status IN (1,2,3,4)",
                Integer.class, subjectId);
        assertEquals(1, activeRows, "DB 中本议题应仅存在 1 条活跃 waiver");

        // 成功的 waiver 应已进入 PENDING_COMMITTEE（DRAFT → 转 PENDING_COMMITTEE 由 application 完成）
        PartyRatioWaiver w = winner.get();
        assertNotNull(w);
        assertEquals(WaiverStatus.PENDING_COMMITTEE, w.getStatus());
        assertEquals(0, new BigDecimal("0.30").compareTo(w.getRequestedRatio()));
        assertEquals(2L, w.getPartyPoolSize(), "申请瞬间党员池快照应为 2");
        assertEquals(6L, w.getTotalEligibleSize(), "合格候选人总数快照应为 6");
    }

    @Test
    public void sequentialSecondSubmission_alsoBlockedByActiveWaiver() {
        // 顺序场景作对照：第一次成功后，第二次（即使锁/事务都已释放）仍应被同议题活跃 waiver 拒绝
        SubmitDraftCommand cmd = new SubmitDraftCommand(
                subjectId, TEST_TENANT_ID, 7001L, new BigDecimal("0.30"),
                "本小区共有产权房比例较高党员人数严重不足经多次组织居民代表协商发动报名仍无法凑足候选人池所需的党员数量特申请将党员比例下限放宽至30%恳请居委会及街道办予以审议批准",
                null
        );

        PartyRatioWaiver first = waiverApplicationService.submitDraft(cmd);
        assertNotNull(first.getWaiverId());

        WaiverApplicationException ex = assertThrows(WaiverApplicationException.class,
                () -> waiverApplicationService.submitDraft(cmd));
        assertEquals(WaiverApplicationException.Reason.WAIVER_ALREADY_PENDING, ex.getReason());
    }
}
