// 关联业务：集中评估业主参选与线上表决所需的身份、资格和认证等级。
package com.pangu.domain.policy;

import com.pangu.domain.model.user.AuthenticationLevel;

/**
 * C端业主自治 ABAC 策略评估引擎接口
 */
public interface AbacPolicyEngine {

    /** 线上投票认证目的；共同决定与业委会选举采用不同实名等级。 */
    enum VotingPurpose {
        COMMON_DECISION,
        COMMITTEE_ELECTION
    }

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
     * 评估线上投票的实名安全等级。
     * @param uid 自然人唯一标识
     * @param tenantId 当前小区租户 ID
     * @param purpose 本次线上投票属于共同决定还是业委会选举
     * @param currentLevel 当前用户的实名认证等级
     * @return 评估决策结果
     */
    EvaluationResult evaluateVoting(
            Long uid,
            Long tenantId,
            VotingPurpose purpose,
            AuthenticationLevel currentLevel);
}
