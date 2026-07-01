package com.pangu.infrastructure.gateway;

import com.pangu.domain.gateway.VoteCastMonitorGateway;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Redis/Redisson 投票监控基线实现。
 *
 * <p>Key 约定：
 * <ul>
 *   <li>{@code bf:vote-cast:{subjectId}}：已成功写入票的 {@code opid:targetId} Bloom 基线；</li>
 *   <li>{@code counter:vote-cast:{subjectId}:total}：成功写入票数增量计数；</li>
 *   <li>{@code counter:vote-cast:{subjectId}:unsigned}：显式纸票/线下代录票数，沿用旧 key 名；</li>
 *   <li>{@code counter:vote-cast:{subjectId}:rapid-interval}：相邻写票间隔小于 30 秒的次数。</li>
 * </ul>
 */
@Slf4j
@Component
public class RedissonVoteCastMonitorGateway implements VoteCastMonitorGateway {

    private static final long RAPID_INTERVAL_MILLIS = 30_000L;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Override
    public void recordCast(VoteCastEvent event) {
        if (redissonClient == null || event == null || event.subjectId() == null) {
            return;
        }
        try {
            String subjectId = String.valueOf(event.subjectId());
            RBloomFilter<String> bloom = redissonClient.getBloomFilter("bf:vote-cast:" + subjectId);
            bloom.tryInit(100_000L, 0.01);
            bloom.add(event.voteKey());

            redissonClient.getAtomicLong("counter:vote-cast:" + subjectId + ":total").incrementAndGet();
            if (event.unsignedLikePaper()) {
                redissonClient.getAtomicLong("counter:vote-cast:" + subjectId + ":unsigned").incrementAndGet();
            }

            RAtomicLong lastAt = redissonClient.getAtomicLong("counter:vote-cast:" + subjectId + ":last-at");
            long currentAt = event.castAt() == null ? System.currentTimeMillis() : event.castAt().toEpochMilli();
            long previousAt = lastAt.getAndSet(currentAt);
            if (previousAt > 0 && currentAt >= previousAt && currentAt - previousAt < RAPID_INTERVAL_MILLIS) {
                redissonClient.getAtomicLong("counter:vote-cast:" + subjectId + ":rapid-interval")
                        .incrementAndGet();
            }
        } catch (RuntimeException ex) {
            log.warn("投票监控基线写入失败 subjectId={} opid={} targetId={}",
                    event.subjectId(), event.opid(), event.targetId(), ex);
        }
    }

    @Override
    public VoteCastCounters loadCounters(Long subjectId) {
        if (redissonClient == null || subjectId == null) {
            return VoteCastCounters.empty(subjectId);
        }
        String id = String.valueOf(subjectId);
        return new VoteCastCounters(
                subjectId,
                redissonClient.getAtomicLong("counter:vote-cast:" + id + ":total").get(),
                redissonClient.getAtomicLong("counter:vote-cast:" + id + ":unsigned").get(),
                redissonClient.getAtomicLong("counter:vote-cast:" + id + ":rapid-interval").get());
    }
}
