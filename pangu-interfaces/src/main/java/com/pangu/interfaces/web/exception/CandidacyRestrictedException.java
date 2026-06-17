package com.pangu.interfaces.web.exception;

import java.util.Map;

/**
 * 候选人资格受限业务异常。
 *
 * <p>除标准错误码 + 文案外，附带结构化的限制原因元数据
 * （例如 {@code policy_type} / {@code restriction_target} / {@code is_voting_rights_retained}），
 * 用于前端展示具体的资格约束细节。
 *
 * <p>通过覆写 {@link #getResponsePayload()} 让 {@code GlobalExceptionHandler}
 * 自动将元数据挂载到 {@code Result.data}，handler 端无需 {@code instanceof} 分支识别。
 */
public class CandidacyRestrictedException extends AppException {

    private final Map<String, Object> restrictionDetails;

    public CandidacyRestrictedException(ErrorCode errorCode, Map<String, Object> restrictionDetails, String message) {
        super(errorCode, message);
        this.restrictionDetails = restrictionDetails;
    }

    public Map<String, Object> getRestrictionDetails() {
        return restrictionDetails;
    }

    @Override
    public Object getResponsePayload() {
        return restrictionDetails;
    }
}
