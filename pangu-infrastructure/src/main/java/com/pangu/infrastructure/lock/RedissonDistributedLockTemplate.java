package com.pangu.infrastructure.lock;

import com.pangu.domain.lock.DistributedLockTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 分布式锁实现。
 *
 * <p>三层并发防御之顶层：阻挡跨进程的同时提交。
 * 锁键命名规范：{@code lock:<feature>:tenant:<T>:<resource>:<id>}。
 *
 * <p>本期不允许「等待获取」语义——拿不到立即抛
 * {@link DistributedLockAcquisitionException}，由上层映射为业务异常。
 */
@Component
public class RedissonDistributedLockTemplate implements DistributedLockTemplate {

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Override
    public <T> T executeWithLock(String key, Duration ttl, Supplier<T> action) {
        if (redissonClient == null) {
            // 兜底：未配置 Redisson 时降级为本地锁（仅用于单测/dev profile）。
            // 生产环境必须保证 Redisson 客户端就位。
            synchronized (RedissonDistributedLockTemplate.class) {
                return action.get();
            }
        }
        RLock lock = redissonClient.getLock(key);
        boolean acquired;
        try {
            acquired = lock.tryLock(0, ttl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockAcquisitionException(key);
        }
        if (!acquired) {
            throw new DistributedLockAcquisitionException(key);
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
