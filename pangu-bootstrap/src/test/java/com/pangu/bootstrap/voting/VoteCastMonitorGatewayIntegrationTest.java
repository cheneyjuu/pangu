package com.pangu.bootstrap.voting;

import com.pangu.domain.gateway.VoteCastMonitorGateway.VoteCastEvent;
import com.pangu.domain.gateway.VoteCastMonitorGateway.VoteCastCounters;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.infrastructure.gateway.RedissonVoteCastMonitorGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2 监控基线 Redis 适配验证：Bloom + 增量计数 + 快速连续投票信号。
 */
@SpringBootTest
public class VoteCastMonitorGatewayIntegrationTest {

    @Autowired
    private RedissonVoteCastMonitorGateway gateway;

    @Autowired
    private RedissonClient redissonClient;

    private long subjectId;

    @BeforeEach
    public void setUp() {
        subjectId = 98_000_000L + System.nanoTime() % 1_000_000L;
        cleanKeys();
    }

    @AfterEach
    public void tearDown() {
        cleanKeys();
    }

    @Test
    public void recordCast_writesBloomCountersAndRapidIntervalSignal() {
        Instant base = Instant.parse("2026-06-28T08:00:00Z");
        gateway.recordCast(new VoteCastEvent(
                subjectId, 10001L, 70001L, 910001L, null,
                SubjectType.GENERAL, VoteChoice.SUPPORT, null, VoteChannel.PAPER, base));
        gateway.recordCast(new VoteCastEvent(
                subjectId, 10001L, 70002L, 910002L, 555L,
                SubjectType.ELECTION, VoteChoice.SUPPORT, null, VoteChannel.ONLINE, base.plusSeconds(10)));

        RBloomFilter<String> bloom = redissonClient.getBloomFilter("bf:vote-cast:" + subjectId);
        assertTrue(bloom.contains("910001:0"));
        assertTrue(bloom.contains("910002:555"));
        assertEquals(2L, redissonClient.getAtomicLong("counter:vote-cast:" + subjectId + ":total").get());
        assertEquals(1L, redissonClient.getAtomicLong("counter:vote-cast:" + subjectId + ":unsigned").get());
        assertEquals(1L, redissonClient.getAtomicLong("counter:vote-cast:" + subjectId + ":rapid-interval").get());
        VoteCastCounters counters = gateway.loadCounters(subjectId);
        assertEquals(2L, counters.totalCount());
        assertEquals(1L, counters.unsignedCount());
        assertEquals(1L, counters.rapidIntervalCount());
    }

    private void cleanKeys() {
        if (redissonClient == null || subjectId == 0L) {
            return;
        }
        String id = String.valueOf(subjectId);
        redissonClient.getKeys().delete(
                "bf:vote-cast:" + id,
                "counter:vote-cast:" + id + ":total",
                "counter:vote-cast:" + id + ":unsigned",
                "counter:vote-cast:" + id + ":last-at",
                "counter:vote-cast:" + id + ":rapid-interval");
    }
}
