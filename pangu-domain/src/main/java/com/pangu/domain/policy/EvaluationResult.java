package com.pangu.domain.policy;

import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * ABAC 策略评估决策结果 (值对象)
 */
@Getter
@AllArgsConstructor
public class EvaluationResult {

    /** 是否允许执行该操作 */
    private final boolean allowed;

    /** 被拦截时的友善提示信息 */
    private final String message;

    /** 触发拦截的政策类型 (如 SCHEME_C) */
    private final String policyType;

    /** 限制的目标操作类型 (如 LIMIT_ELECTION_RIGHT) */
    private final String restrictionTarget;

    /** 是否保留核心投票表决权 (民法典要求) */
    private final boolean isVotingRightsRetained;

    public static EvaluationResult allowed() {
        return new EvaluationResult(true, "放行：符合业务执行条件", null, null, true);
    }

    public static EvaluationResult denied(String message, String policyType, String restrictionTarget, boolean isVotingRightsRetained) {
        return new EvaluationResult(false, message, policyType, restrictionTarget, isVotingRightsRetained);
    }
}
