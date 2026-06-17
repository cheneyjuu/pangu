package com.pangu.bootstrap.web;

import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import com.pangu.interfaces.web.exception.ErrorType;
import com.pangu.interfaces.web.exception.GlobalExceptionHandler;
import com.pangu.interfaces.web.controller.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppExceptionBehaviorTest {

    @Test
    void rejectsNullErrorCodeWithClearMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new AppException(null));

        assertTrue(ex.getMessage().contains("errorCode"));
    }

    @Test
    void exposesTypeSafeErrorCategory() {
        assertEquals(ErrorType.SYSTEM, CommonErrorCode.SYSTEM_ERROR.getType());
        assertEquals("SYSTEM", CommonErrorCode.SYSTEM_ERROR.getErrorType());
    }

    @Test
    void preservesCallerFormattedMessage() {
        // 由调用方自行格式化文案后传入，AppException 不再持有 String.format 兜底逻辑。
        AppException ex = new AppException(CommonErrorCode.PARAM_ERROR,
                String.format("invalid %s and %s", "arg1", "arg2"));

        assertEquals("invalid arg1 and arg2", ex.getMessage());
        assertEquals(CommonErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    void delegatesNeedRetryToErrorCodeWithoutCaching() {
        AppException ex = new AppException(CommonErrorCode.SYSTEM_ERROR);

        assertEquals(CommonErrorCode.SYSTEM_ERROR.isNeedRetry(), ex.isNeedRetry());
    }

    @Test
    void responsePayloadDefaultsToNullForBaseException() {
        AppException ex = new AppException(CommonErrorCode.PARAM_ERROR);

        assertNull(ex.getResponsePayload());
    }

    @Test
    void handlesUnexpectedExceptionWithUnifiedEnvelope() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<Object> result = handler.handleUnexpectedException(new IllegalStateException("boom"), response);

        assertEquals(500, response.getStatus());
        assertEquals(500, result.getCode());
        assertEquals(CommonErrorCode.SYSTEM_ERROR.getMessage(), result.getMsg());
        assertEquals("SYSTEM", result.getErrorType());
        assertEquals(Boolean.TRUE, result.getNeedRetry());
    }
}
