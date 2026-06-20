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

    // ============ M3-2 议题生命周期（BUSINESS / 409） ============
    SUBJECT_NOT_DRAFT(40920, "议题不在草稿状态，无法公示", 409, ErrorType.BIZ, false),
    SUBJECT_NOT_PUBLISHED(40921, "议题不在公示状态，无法强撤", 409, ErrorType.BIZ, false),
    SUBJECT_NOT_VOTING_CASTABLE(40922, "议题不在投票中，暂无法投票", 409, ErrorType.BIZ, false),
    PROPOSE_FORBIDDEN_FOR_TYPE(40923, "当前角色不允许发起该类型议题", 403, ErrorType.BIZ, false),
    CANCEL_FORBIDDEN(40924, "当前角色或议题状态不允许撤回", 403, ErrorType.BIZ, false),
    LIFECYCLE_CONCURRENT_MODIFICATION(40925, "议题状态在并发写入下被推翻，请刷新后重试", 409, ErrorType.SYSTEM, true),

    // ============ M3-2 投票提交（AUTHZ / 403 + BUSINESS / 409） ============
    AUTH_LEVEL_INSUFFICIENT(40330, "当前认证等级不足，请先完成人脸实名认证", 403, ErrorType.BIZ, false),
    OPID_NOT_OWNED(40331, "该房产身份不属于当前用户", 403, ErrorType.BIZ, false),
    OPID_OUT_OF_SCOPE(40332, "该房产不在本议题表决范围内", 403, ErrorType.BIZ, false),
    VOTE_ALREADY_CAST(40930, "您已对该议题投过票，无法重复投票", 409, ErrorType.BIZ, false),

    // ============ M3-3 ELECTION 选举全流程（VALIDATION/BUSINESS） ============
    ELECTION_MAX_WINNERS_REQUIRED(40940, "选举立项必须指定应选名额（不少于 1）", 409, ErrorType.BIZ, false),
    ELECTION_TARGET_REQUIRED(40941, "选举投票必须指定候选人", 409, ErrorType.BIZ, false),
    CANDIDATE_NOT_FOUND(40942, "候选人不存在", 404, ErrorType.BIZ, false),
    CANDIDATE_NOT_VOTABLE(40943, "候选人不可被投票（不属于本议题或资格未通过）", 409, ErrorType.BIZ, false),
    CANDIDATE_ALREADY_NOMINATED(40944, "该业主已被提名为本议题候选人", 409, ErrorType.BIZ, false),
    CANDIDATE_REVIEW_CONFLICT(40945, "候选人资格已被审查或状态不允许该操作", 409, ErrorType.BIZ, false),
    VOTE_LIMIT_EXCEEDED(40946, "已投满本次选举的应选名额", 409, ErrorType.BIZ, false),
    SUBJECT_NOT_NOMINATABLE(40947, "议题当前状态不允许增改候选人名单", 409, ErrorType.BIZ, false),

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
