package com.pangu.interfaces.web.exception;

import com.pangu.application.lock.GovernanceLockApplicationException;

/**
 * {@link GovernanceLockApplicationException} → web 层 {@link LockErrorCode} 翻译器。
 *
 * <p>设计动机与 {@link ElectionExceptionTranslator} 一致：将翻译逻辑独立成类，便于单元测试
 * 覆盖 {@code Reason → ErrorCode} 的全表映射。
 */
public final class GovernanceLockExceptionTranslator {

    private GovernanceLockExceptionTranslator() {
    }

    public static LockErrorCode translate(GovernanceLockApplicationException ex) {
        return switch (ex.getReason()) {
            case LOCK_NOT_FOUND -> LockErrorCode.LOCK_NOT_FOUND;
            case LOCK_ALREADY_EXISTS -> LockErrorCode.LOCK_ALREADY_EXISTS;
            case LOCK_CONCURRENT_MODIFICATION -> LockErrorCode.LOCK_CONCURRENT_MODIFICATION;
            case LOCK_INVALID_TRANSITION -> LockErrorCode.LOCK_INVALID_TRANSITION;
            case LOCK_SIGNER_CONFLICT -> LockErrorCode.LOCK_SIGNER_CONFLICT;
            case LOCK_NOT_HELD -> LockErrorCode.LOCK_NOT_HELD;
        };
    }
}
