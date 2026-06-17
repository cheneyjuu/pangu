package com.pangu.domain.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 分布式锁端口（Hexagonal Port）。
 *
 * <p>实现：infrastructure 层 {@code RedissonDistributedLockTemplate}
 * （Redis 红线锁）。配合 DB {@code SELECT FOR UPDATE} + 唯一索引形成三层防御。
 */
public interface DistributedLockTemplate {

    /**
     * 获取锁后执行 action；自动释放。
     *
     * <p>未获取到锁时立即抛 {@link DistributedLockAcquisitionException}；
     * 上层应当转为对客户端友好的业务异常（如 WAIVER_ALREADY_PENDING）。
     *
     * @param key    锁键（包含 tenant 与业务实体 ID 的领域语义）
     * @param ttl    锁最大持有时长（防死锁）
     * @param action 临界区动作
     */
    <T> T executeWithLock(String key, Duration ttl, Supplier<T> action);

    /**
     * 锁获取失败异常（区别于业务异常，让上层决定如何映射）。
     */
    class DistributedLockAcquisitionException extends RuntimeException {
        public DistributedLockAcquisitionException(String key) {
            super("Failed to acquire distributed lock: " + key);
        }
    }
}
