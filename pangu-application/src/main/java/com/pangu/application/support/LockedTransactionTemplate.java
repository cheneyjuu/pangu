package com.pangu.application.support;

import com.pangu.domain.lock.DistributedLockTemplate;
import com.pangu.domain.lock.DistributedLockTemplate.DistributedLockAcquisitionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Redis 红线锁 + Spring 事务的固定编排模板。
 */
public final class LockedTransactionTemplate {

    private LockedTransactionTemplate() {
    }

    public static <T> T execute(DistributedLockTemplate lockTemplate,
                                TransactionTemplate transactionTemplate,
                                String lockKey,
                                Duration ttl,
                                Supplier<T> transactionBody,
                                Function<DistributedLockAcquisitionException, ? extends RuntimeException> lockFailureMapper) {
        Objects.requireNonNull(lockTemplate, "lockTemplate must not be null");
        Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
        Objects.requireNonNull(transactionBody, "transactionBody must not be null");
        Objects.requireNonNull(lockFailureMapper, "lockFailureMapper must not be null");
        try {
            return lockTemplate.executeWithLock(lockKey, ttl,
                    () -> transactionTemplate.execute(status -> transactionBody.get()));
        } catch (DistributedLockAcquisitionException e) {
            throw lockFailureMapper.apply(e);
        }
    }
}
