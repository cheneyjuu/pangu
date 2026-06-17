package com.pangu.interfaces.web.exception;

/**
 * 选举/表决/Waiver 业务错误码（feature-scoped 枚举，对齐 {@link CommonErrorCode} 体系）。
 *
 * <p>命名规则：
 * <ul>
 *   <li>{@code WAIVER_*} —— 党员比例放宽申请相关；</li>
 *   <li>{@code SUBJECT_*} —— 议题状态/结算相关；</li>
 *   <li>{@code DENOMINATOR_*} —— 分母快照与去重；</li>
 *   <li>{@code ATTESTATION_*} —— 司法链 stub；</li>
 *   <li>{@code DATA_SCOPE_*} —— 数据权限拦截器审计性失败。</li>
 * </ul>
 *
 * <p>错误码段位约定：
 * <pre>
 *   400xx  请求参数级（VALIDATION）
 *   409xx  业务冲突（BUSINESS）
 *   403xx  授权（AUTHZ）—— 与 CommonErrorCode.FORBIDDEN 区分子原因
 *   500xx  系统/分母解析/上链失败（SYSTEM，需要 needRetry=true）
 * </pre>
 *
 * <p>{@code errorType} 复用 {@link ErrorType}（BIZ / SYSTEM / THIRD_PARTY）；
 * "VALIDATION"/"AUTHZ" 属于 BIZ 大类下的语义子集，统一计入 BIZ 以保持响应字段稳定。
 */
public enum ElectionErrorCode implements ErrorCode {

    // ============ Waiver: 申请理由水文检测（VALIDATION） ============
    WAIVER_REASON_TOO_SHORT(40001, "申请理由实质字符不足，请补充至少 50 个有意义的中文/英文字符", 400, ErrorType.BIZ, false),
    WAIVER_REASON_TOO_REPETITIVE(40002, "申请理由内容重复度过高，请重新撰写", 400, ErrorType.BIZ, false),
    WAIVER_REASON_LOW_ENTROPY(40003, "申请理由字符多样性不足，请重新撰写", 400, ErrorType.BIZ, false),
    WAIVER_REASON_REJECTED(40004, "申请理由未通过水文检测，请重新撰写", 400, ErrorType.BIZ, false),

    // ============ Waiver: 业务冲突（BUSINESS / 409） ============
    WAIVER_ALREADY_PENDING(40901, "本议题已存在活跃的放宽申请", 409, ErrorType.BIZ, false),
    WAIVER_NOT_FOUND(40902, "Waiver 不存在或已过期", 404, ErrorType.BIZ, false),
    WAIVER_INVALID_TRANSITION(40903, "Waiver 状态流转不合法", 409, ErrorType.BIZ, false),
    WAIVER_TENANT_MISMATCH(40904, "申请人租户与议题租户不一致", 409, ErrorType.BIZ, false),
    WAIVER_SUBJECT_NOT_ELIGIBLE(40905, "议题不存在或不允许发起放宽申请", 409, ErrorType.BIZ, false),
    WAIVER_CONCURRENT_MODIFICATION(40906, "Waiver 已被其他操作并发修改，请刷新后重试", 409, ErrorType.SYSTEM, true),

    // ============ Waiver: 授权（AUTHZ / 403） ============
    WAIVER_INITIATOR_NOT_COMMITTEE(40301, "申请发起人必须是居委会用户（dept_type=2）", 403, ErrorType.BIZ, false),
    WAIVER_APPROVER_DEPT_INVALID(40302, "审批人部门类型不符", 403, ErrorType.BIZ, false),
    WAIVER_APPROVER_CONFLICT(40303, "终审与初审审批人不能为同一人", 403, ErrorType.BIZ, false),

    // ============ 议题/结算（BUSINESS / 409） ============
    SUBJECT_NOT_FOUND(40910, "议题不存在", 404, ErrorType.BIZ, false),
    SUBJECT_NOT_VOTING(40911, "议题不在投票期或已截止", 409, ErrorType.BIZ, false),
    SUBJECT_ALREADY_SETTLED(40912, "议题已结算，无法重复操作", 409, ErrorType.BIZ, false),
    SUBJECT_TYPE_NOT_SUPPORTED(40913, "议题类型尚不支持本项操作", 409, ErrorType.BIZ, false),
    SUBJECT_CONCURRENT_SETTLEMENT(40914, "议题在结算过程中被并发修改，请稍后重试", 409, ErrorType.SYSTEM, true),

    // ============ 系统/外部依赖（SYSTEM / 500） ============
    DENOMINATOR_RESOLVE_FAILED(50010, "分母快照生成失败，请稍后重试", 500, ErrorType.SYSTEM, true),
    ATTESTATION_FAILED(50011, "司法链存证失败，请稍后重试", 500, ErrorType.SYSTEM, true),
    DATA_SCOPE_PARSE_FAILED(50020, "数据权限 SQL 重写失败，已拒绝放行原 SQL", 500, ErrorType.SYSTEM, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    ElectionErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.needRetry = needRetry;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getErrorType() {
        return errorType.name();
    }

    @Override
    public ErrorType getType() {
        return errorType;
    }

    @Override
    public boolean isNeedRetry() {
        return needRetry;
    }
}
