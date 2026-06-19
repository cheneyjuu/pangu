package com.pangu.interfaces.web.exception;

import com.pangu.application.disclosure.FinanceDisclosureApplicationException;

/**
 * {@link FinanceDisclosureApplicationException} → {@link DisclosureErrorCode} 翻译器。
 *
 * <p>设计动机与 {@link GovernanceLockExceptionTranslator} / {@link ElectionExceptionTranslator}
 * 一致：将 Reason → ErrorCode 的全表映射独立成静态工具，便于单测覆盖。
 */
public final class DisclosureExceptionTranslator {

    private DisclosureExceptionTranslator() {
    }

    public static DisclosureErrorCode translate(FinanceDisclosureApplicationException ex) {
        return switch (ex.getReason()) {
            case SNAPSHOT_NOT_FOUND -> DisclosureErrorCode.SNAPSHOT_NOT_FOUND;
            case SNAPSHOT_DUPLICATE -> DisclosureErrorCode.SNAPSHOT_DUPLICATE;
            case SNAPSHOT_INVALID_TRANSITION -> DisclosureErrorCode.SNAPSHOT_INVALID_TRANSITION;
            case DISCLOSURE_TYPE_NOT_SUPPORTED -> DisclosureErrorCode.DISCLOSURE_TYPE_NOT_SUPPORTED;
            case DISCLOSURE_NOT_PUBLISHED -> DisclosureErrorCode.DISCLOSURE_NOT_PUBLISHED;
            case COMPARE_INVALID_PAIR -> DisclosureErrorCode.COMPARE_INVALID_PAIR;
            case LEDGER_QUERY_EMPTY -> DisclosureErrorCode.LEDGER_QUERY_EMPTY;
            case SNAPSHOT_CONCURRENT_MODIFICATION -> DisclosureErrorCode.SNAPSHOT_CONCURRENT_MODIFICATION;
        };
    }
}
