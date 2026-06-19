package com.pangu.bootstrap.lock;

import com.pangu.application.lock.GovernanceLockApplicationException;
import com.pangu.application.lock.GovernanceLockApplicationService;
import com.pangu.application.lock.command.LockCommand;
import com.pangu.domain.model.lock.GovernanceLock;
import com.pangu.domain.model.lock.LockEntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * {@link GovernanceLockApplicationService#lock} 的三层并发防御回归测试：
 *
 * <ol>
 *   <li>① Redis 红线锁 {@code lock:gov:tenant:{T}:type:{X}:entity:{E}} —— 单进程串行化；</li>
 *   <li>② DB SELECT FOR UPDATE 行锁 —— 同 tx 内活跃锁互斥；</li>
 *   <li>③ 唯一索引 {@code uidx_lock_entity} —— 兜底保险。</li>
 * </ol>
 *
 * <p>两线程同瞬触发 lock，期望恰一条成功 + 另一条返回友好的
 * {@link GovernanceLockApplicationException.Reason#LOCK_ALREADY_EXISTS}。
 *
 * <p>需要真实 PostgreSQL + Redis（{@code docker compose up -d}）。
 */
@SpringBootTest
public class ConcurrentLockSubmissionTest {

    private static final long TEST_TENANT_ID = 99301L;
    private static final long ENTITY_ID = 6001L;
    private static final long USER_ID = 7001L;
    private static final String HASH64 = "c".repeat(64);

    @Autowired
    private GovernanceLockApplicationService lockApplicationService;

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
        jdbcTemplate.update("DELETE FROM t_governance_lock WHERE tenant_id = ?", TEST_TENANT_ID);
    }

    @Test
    public void twoConcurrentLocks_exactlyOneSucceeds_otherGetsFriendlyAlreadyExists() throws Exception {
        LockCommand cmd = new LockCommand(
                TEST_TENANT_ID, LockEntityType.FINANCE_DISCLOSURE, ENTITY_ID, USER_ID, HASH64);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyExistsCount = new AtomicInteger(0);
        List<Throwable> unexpected = new ArrayList<>();
        AtomicReference<GovernanceLock> winner = new AtomicReference<>();

        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        fire.await();
                        try {
                            GovernanceLock l = lockApplicationService.lock(cmd);
                            successCount.incrementAndGet();
                            winner.compareAndSet(null, l);
                        } catch (GovernanceLockApplicationException e) {
                            if (e.getReason() == GovernanceLockApplicationException.Reason.LOCK_ALREADY_EXISTS) {
                                alreadyExistsCount.incrementAndGet();
                            } else {
                                synchronized (unexpected) { unexpected.add(e); }
                            }
                        } catch (Throwable t) {
                            synchronized (unexpected) { unexpected.add(t); }
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "两线程未在 5s 内就绪");
            fire.countDown();
            assertTrue(done.await(15, TimeUnit.SECONDS), "并发 lock 未在 15s 内全部返回");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(unexpected.isEmpty(), "出现非预期异常：" + unexpected);
        assertEquals(1, successCount.get(), "应恰有 1 个线程成功落库");
        assertEquals(1, alreadyExistsCount.get(),
                "另一线程应被三层防御拦截并返回 LOCK_ALREADY_EXISTS");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_governance_lock WHERE tenant_id = ? AND entity_id = ?",
                Integer.class, TEST_TENANT_ID, ENTITY_ID);
        assertEquals(1, rows, "DB 中本 entity 应仅存 1 条治理锁记录");

        GovernanceLock w = winner.get();
        assertNotNull(w);
        assertNotNull(w.getLockId());
        assertEquals(LockEntityType.FINANCE_DISCLOSURE, w.getEntityType());
    }

    @Test
    public void sequentialSecondLock_alsoBlockedByActiveLock() {
        LockCommand cmd = new LockCommand(
                TEST_TENANT_ID, LockEntityType.ELECTION_DISCLOSURE, ENTITY_ID, USER_ID, HASH64);

        GovernanceLock first = lockApplicationService.lock(cmd);
        assertNotNull(first.getLockId());

        GovernanceLockApplicationException ex = assertThrows(GovernanceLockApplicationException.class,
                () -> lockApplicationService.lock(cmd));
        assertEquals(GovernanceLockApplicationException.Reason.LOCK_ALREADY_EXISTS, ex.getReason());
    }
}
