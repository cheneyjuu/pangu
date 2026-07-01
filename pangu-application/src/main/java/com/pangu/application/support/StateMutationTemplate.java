package com.pangu.application.support;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 聚合根状态推进模板：加载 / 领域动作 / 持久化保持在同一个编排形状里。
 */
public final class StateMutationTemplate {

    private StateMutationTemplate() {
    }

    public static <T> T execute(Supplier<T> loadForUpdate,
                                StateMutation<T> mutation,
                                StatePersistence<T> persistence,
                                Function<IllegalStateException, ? extends RuntimeException> invalidStateMapper) {
        Objects.requireNonNull(loadForUpdate, "loadForUpdate must not be null");
        return advance(loadForUpdate.get(), mutation, persistence, invalidStateMapper);
    }

    public static <T> T advance(T aggregate,
                                StateMutation<T> mutation,
                                StatePersistence<T> persistence,
                                Function<IllegalStateException, ? extends RuntimeException> invalidStateMapper) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        Objects.requireNonNull(mutation, "mutation must not be null");
        Objects.requireNonNull(persistence, "persistence must not be null");
        Objects.requireNonNull(invalidStateMapper, "invalidStateMapper must not be null");
        try {
            mutation.apply(aggregate);
        } catch (IllegalStateException e) {
            throw invalidStateMapper.apply(e);
        }
        persistence.persist(aggregate);
        return aggregate;
    }

    @FunctionalInterface
    public interface StateMutation<T> {
        void apply(T aggregate);
    }

    @FunctionalInterface
    public interface StatePersistence<T> {
        void persist(T aggregate);
    }
}
