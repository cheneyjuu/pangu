package com.pangu.bootstrap.handover;

import com.pangu.application.disclosure.FinanceDisclosureApplicationException;
import com.pangu.interfaces.web.exception.DisclosureErrorCode;
import com.pangu.interfaces.web.exception.DisclosureExceptionTranslator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link DisclosureExceptionTranslator} 对换届熔断 Reason 的映射单测。
 *
 * <p>断言 {@code HANDOVER_IN_PROGRESS} → {@link DisclosureErrorCode#HANDOVER_IN_PROGRESS}（41109 / 409 / 不可重试）。
 */
public class DisclosureExceptionTranslatorTest {

    @Test
    public void handoverInProgress_mapsTo41109() {
        FinanceDisclosureApplicationException ex = new FinanceDisclosureApplicationException(
                FinanceDisclosureApplicationException.Reason.HANDOVER_IN_PROGRESS,
                "换届选举进行中，财务公示发布已熔断 electionSubjectId=888");

        DisclosureErrorCode code = DisclosureExceptionTranslator.translate(ex);

        assertEquals(DisclosureErrorCode.HANDOVER_IN_PROGRESS, code);
        assertEquals(41109, code.getCode());
        assertEquals(409, code.getHttpStatus());
        assertEquals(false, code.isNeedRetry());
    }
}
