package com.pangu.domain.policy;

import com.pangu.domain.model.user.AuthenticationLevel;

/**
 * C端业主自治 ABAC 策略评估引擎接口
 */
public interface AbacPolicyEngine {

    /**
     * 评估自荐/提名参选业委会委员的资格限制 (方案C拦截机制)
     * @param uid 自然人唯一标识
     * @param tenantId 当前小区租户 ID
     * @param hasUnpaidFees 该业主名下房产是否有未缴纳费用记录
     * @param schemeType 小区当前生效的配置方案（A、B、C）
     * @return 评估决策结果
     */
    EvaluationResult evaluateCandidacy(Long uid, Long tenantId, boolean hasUnpaidFees, String schemeType);

    /**
     * 评估大表决投票的实名安全等级
     * @param uid 自然人唯一标识
     * @param tenantId 当前小区租户 ID
     * @param currentLevel 当前用户的实名认证等级
     * @return 评估决策结果
     */
    EvaluationResult evaluateVoting(Long uid, Long tenantId, AuthenticationLevel currentLevel);
}
