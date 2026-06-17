package com.pangu.bootstrap.web;

import com.pangu.interfaces.web.controller.AppException;
import com.pangu.interfaces.web.controller.CommonErrorCode;
import com.pangu.interfaces.web.controller.ErrorType;
import com.pangu.interfaces.web.controller.GlobalExceptionHandler;
import com.pangu.interfaces.web.controller.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void keepsLegacyBusinessCodeButUsesServerHttpStatus() {
        AppException ex = new AppException(10001, "legacy error");

        assertEquals(10001, ex.getCode());
        assertEquals(500, ex.getErrorCode().getHttpStatus());
        assertEquals("BIZ", ex.getErrorCode().getErrorType());
        assertEquals(ErrorType.BIZ, ex.getErrorCode().getType());
    }

    @Test
    void exposesTypeSafeErrorCategory() {
        assertEquals(ErrorType.SYSTEM, CommonErrorCode.SYSTEM_ERROR.getType());
        assertEquals("SYSTEM", CommonErrorCode.SYSTEM_ERROR.getErrorType());
    }

    @Test
    void fallsBackWhenMessageFormatIsInvalid() {
        AppException ex = new AppException(CommonErrorCode.PARAM_ERROR, "invalid %s %s", "arg1");

        assertEquals("invalid %s %s [arg1]", ex.getMessage());
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
