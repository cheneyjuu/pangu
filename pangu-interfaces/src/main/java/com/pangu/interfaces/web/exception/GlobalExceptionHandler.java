package com.pangu.interfaces.web.exception;

import com.pangu.application.admin.BuildingAssignmentApplicationException;
import com.pangu.application.admin.RoleAdminApplicationException;
import com.pangu.application.admin.WorkIdentityApplicationException;
import com.pangu.application.assembly.OwnersAssemblyApplicationException;
import com.pangu.application.community.CommunitySettingsApplicationException;
import com.pangu.application.disclosure.FinanceDisclosureApplicationException;
import com.pangu.application.dispute.DisputeApplicationException;
import com.pangu.application.lock.GovernanceLockApplicationException;
import com.pangu.application.owner.PropertyBindingApplicationException;
import com.pangu.application.repair.RepairWorkOrderApplicationException;
import com.pangu.application.repair.SupplierActivationApplicationException;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.waiver.WaiverApplicationException;
import com.pangu.interfaces.web.controller.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，统一处理 {@link AppException} 及未捕获异常。
 *
 * <p>职责边界：
 * <ul>
 *   <li>将 {@link ErrorCode} 中的 httpStatus / errorType / needRetry 翻译到 HTTP 响应；</li>
 *   <li>调用 {@link AppException#getResponsePayload()} 获取子类附加的结构化数据，无需 {@code instanceof} 分支；</li>
 *   <li>将 {@link WaiverApplicationException} / {@link VotingApplicationException} 通过
 *       {@link ElectionExceptionTranslator} 映射到对应的 {@link ElectionErrorCode}；</li>
 *   <li>将 Spring Security 的 {@link AccessDeniedException} / {@link AuthenticationException}
 *       兜底为 FORBIDDEN / UNAUTHORIZED；</li>
 *   <li>未知异常一律降级为 {@link CommonErrorCode#SYSTEM_ERROR}，避免堆栈泄漏到客户端。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public Result<Object> handleAppException(AppException ex, HttpServletResponse response) {
        ErrorCode errorCode = ex.getErrorCode();
        response.setStatus(errorCode.getHttpStatus());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage(),
                ex.getResponsePayload(),
                errorCode.getErrorType(),
                ex.isNeedRetry());
    }

    /**
     * Waiver 应用层业务异常 → ElectionErrorCode。message 沿用 application 层抛出的具体描述
     * （便于前端定位问题），httpStatus / errorType / needRetry 由 ErrorCode 决定。
     */
    @ExceptionHandler(WaiverApplicationException.class)
    public Result<Object> handleWaiverApplicationException(WaiverApplicationException ex,
                                                            HttpServletResponse response) {
        ElectionErrorCode errorCode = ElectionExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("Waiver business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /**
     * 投票结算应用层业务异常 → ElectionErrorCode。
     */
    @ExceptionHandler(VotingApplicationException.class)
    public Result<Object> handleVotingApplicationException(VotingApplicationException ex,
                                                            HttpServletResponse response) {
        ElectionErrorCode errorCode = ElectionExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("Voting business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /**
     * 治理锁应用层业务异常 → LockErrorCode。
     */
    @ExceptionHandler(GovernanceLockApplicationException.class)
    public Result<Object> handleGovernanceLockApplicationException(GovernanceLockApplicationException ex,
                                                                    HttpServletResponse response) {
        LockErrorCode errorCode = GovernanceLockExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("GovernanceLock business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /**
     * 财务公示应用层业务异常 → DisclosureErrorCode。
     */
    @ExceptionHandler(FinanceDisclosureApplicationException.class)
    public Result<Object> handleFinanceDisclosureApplicationException(FinanceDisclosureApplicationException ex,
                                                                       HttpServletResponse response) {
        DisclosureErrorCode errorCode = DisclosureExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("FinanceDisclosure business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /**
     * 业主异议应用层业务异常 → DisputeErrorCode。
     */
    @ExceptionHandler(DisputeApplicationException.class)
    public Result<Object> handleDisputeApplicationException(DisputeApplicationException ex,
                                                              HttpServletResponse response) {
        DisputeErrorCode errorCode = DisputeExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("Dispute business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /**
     * SaaS 角色管理应用层业务异常 → RoleAdminErrorCode（M2-4）。
     */
    @ExceptionHandler(RoleAdminApplicationException.class)
    public Result<Object> handleRoleAdminApplicationException(RoleAdminApplicationException ex,
                                                               HttpServletResponse response) {
        RoleAdminErrorCode errorCode = RoleAdminExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("RoleAdmin business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /**
     * 楼栋责任田分配应用层业务异常 → BuildingAssignmentErrorCode（M4）。
     */
    @ExceptionHandler(BuildingAssignmentApplicationException.class)
    public Result<Object> handleBuildingAssignmentApplicationException(
            BuildingAssignmentApplicationException ex, HttpServletResponse response) {
        BuildingAssignmentErrorCode errorCode = BuildingAssignmentExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("BuildingAssignment business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /**
     * 工作身份授权应用层业务异常 → WorkIdentityErrorCode。
     */
    @ExceptionHandler(WorkIdentityApplicationException.class)
    public Result<Object> handleWorkIdentityApplicationException(
            WorkIdentityApplicationException ex, HttpServletResponse response) {
        WorkIdentityErrorCode errorCode = WorkIdentityExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("WorkIdentity business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    @ExceptionHandler(CommunitySettingsApplicationException.class)
    public Result<Object> handleCommunitySettingsApplicationException(
            CommunitySettingsApplicationException ex, HttpServletResponse response) {
        CommunitySettingsErrorCode errorCode = CommunitySettingsExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("CommunitySettings business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /**
     * 维修报修工单应用层业务异常 → RepairWorkOrderErrorCode。
     */
    @ExceptionHandler(RepairWorkOrderApplicationException.class)
    public Result<Object> handleRepairWorkOrderApplicationException(
            RepairWorkOrderApplicationException ex, HttpServletResponse response) {
        RepairWorkOrderErrorCode errorCode = RepairWorkOrderExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("RepairWorkOrder business exception reason={} code={} msg={}",
                ex.reason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    @ExceptionHandler(SupplierActivationApplicationException.class)
    public Result<Object> handleSupplierActivationApplicationException(
            SupplierActivationApplicationException ex, HttpServletResponse response) {
        SupplierActivationErrorCode errorCode = SupplierActivationExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("SupplierActivation business exception reason={} code={} msg={}",
                ex.reason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    @ExceptionHandler(OwnersAssemblyApplicationException.class)
    public Result<Object> handleOwnersAssemblyApplicationException(
            OwnersAssemblyApplicationException ex, HttpServletResponse response) {
        OwnersAssemblyErrorCode errorCode = OwnersAssemblyExceptionTranslator.translate(ex);
        response.setStatus(errorCode.getHttpStatus());
        log.info("OwnersAssembly business exception reason={} code={} msg={}",
                ex.reason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    @ExceptionHandler(PropertyBindingApplicationException.class)
    public Result<Object> handlePropertyBindingApplicationException(
            PropertyBindingApplicationException ex, HttpServletResponse response) {
        CommonErrorCode errorCode = switch (ex.getReason()) {
            case PARAM_INVALID -> CommonErrorCode.PARAM_ERROR;
            case FORBIDDEN -> CommonErrorCode.FORBIDDEN;
            case UNAUTHORIZED -> CommonErrorCode.UNAUTHORIZED;
            case NOT_FOUND -> CommonErrorCode.NOT_FOUND;
            case BAD_REQUEST -> CommonErrorCode.BAD_REQUEST;
            case SYSTEM_ERROR -> CommonErrorCode.SYSTEM_ERROR;
        };
        response.setStatus(errorCode.getHttpStatus());
        log.info("PropertyBinding business exception reason={} code={} msg={}",
                ex.getReason(), errorCode.getCode(), ex.getMessage());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /** Bean Validation 失败 → 400 PARAM_ERROR，message 拼接所有字段错误。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Object> handleValidationException(MethodArgumentNotValidException ex,
                                                     HttpServletResponse response) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ":" + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        CommonErrorCode errorCode = CommonErrorCode.PARAM_ERROR;
        response.setStatus(errorCode.getHttpStatus());
        return Result.fail(
                errorCode.getCode(),
                detail.isEmpty() ? errorCode.getMessage() : detail,
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /** Spring Security 鉴权失败：未登录。 */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Object> handleAuthenticationException(AuthenticationException ex,
                                                         HttpServletResponse response) {
        CommonErrorCode errorCode = CommonErrorCode.UNAUTHORIZED;
        response.setStatus(errorCode.getHttpStatus());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /** Spring Security 授权失败：拒绝访问。 */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Object> handleAccessDeniedException(AccessDeniedException ex,
                                                       HttpServletResponse response) {
        CommonErrorCode errorCode = CommonErrorCode.FORBIDDEN;
        response.setStatus(errorCode.getHttpStatus());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage() != null ? ex.getMessage() : errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }

    /**
     * 数据权限拦截器抛出的 IllegalStateException → DATA_SCOPE_PARSE_FAILED。
     * 仅当异常 message 含数据权限关键字时映射，避免误吞其他 IllegalStateException。
     */
    @ExceptionHandler(IllegalStateException.class)
    public Result<Object> handleIllegalStateException(IllegalStateException ex,
                                                       HttpServletResponse response) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        if (msg.contains("数据权限")) {
            ElectionErrorCode errorCode = ElectionErrorCode.DATA_SCOPE_PARSE_FAILED;
            response.setStatus(errorCode.getHttpStatus());
            log.error("DataScope parse failed: {}", msg, ex);
            return Result.fail(
                    errorCode.getCode(),
                    msg,
                    null,
                    errorCode.getErrorType(),
                    errorCode.isNeedRetry());
        }
        return handleUnexpectedException(ex, response);
    }

    @ExceptionHandler(Exception.class)
    public Result<Object> handleUnexpectedException(Exception ex, HttpServletResponse response) {
        CommonErrorCode errorCode = CommonErrorCode.SYSTEM_ERROR;
        response.setStatus(errorCode.getHttpStatus());
        log.error("Unhandled exception", ex);
        return Result.fail(
                errorCode.getCode(),
                errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }
}
