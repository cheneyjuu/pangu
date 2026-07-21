// 关联业务：执行业主参选与线上表决的默认认证等级和资格策略。
package com.pangu.domain.policy.impl;

import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;

/**
 * C端业主自治 ABAC 策略评估引擎默认实现 (领域服务)
 */
public class DefaultAbacPolicyEngine implements AbacPolicyEngine {

    @Override
    public EvaluationResult evaluateCandidacy(Long uid, Long tenantId, boolean hasUnpaidFees, String schemeType) {
        if (schemeType == null || schemeType.isEmpty()) {
            schemeType = "SCHEME_C"; // 默认采用主流折中的 C 方案
        }

        if (hasUnpaidFees) {
            switch (schemeType.toUpperCase()) {
                case "SCHEME_A": // 方案 A (完全限制：剥夺两权)
                    return EvaluationResult.denied(
                            "拦截限制：检测到您当前存在未按管理规约交纳维修资金或物业服务费的记录。根据本小区《业主大会议事规则》【方案A】，完全限制您的投票权及参选资格。",
                            "SCHEME_A",
                            "LIMIT_ALL_RIGHTS",
                            false // 表决权也被剥夺
                    );
                case "SCHEME_C": // 方案 C (限制被选举权，保留投票权)
                    return EvaluationResult.denied(
                            "拦截限制：检测到您当前名下物理单元存在未交纳物业服务费记录。根据本小区当前执行的《业主大会议事规则》方案C配置，限制参选业委会委员资格。如需申诉，请前往居委会协助核销。",
                            "SCHEME_C",
                            "LIMIT_ELECTION_RIGHT",
                            true // 依法保留投票权
                    );
                case "SCHEME_B": // 方案 B (完全不限制)
                default:
                    return EvaluationResult.allowed();
            }
        }

        // 无欠费直接放行
        return EvaluationResult.allowed();
    }

    @Override
    public EvaluationResult evaluateVoting(
            Long uid,
            Long tenantId,
            VotingPurpose purpose,
            AuthenticationLevel currentLevel) {
        AuthenticationLevel requiredLevel = purpose == VotingPurpose.COMMITTEE_ELECTION
                ? AuthenticationLevel.L3
                : AuthenticationLevel.L2;
        if (currentLevel == null || currentLevel.getValue() < requiredLevel.getValue()) {
            if (requiredLevel == AuthenticationLevel.L3) {
                return EvaluationResult.denied(
                        "业委会选举投票前请先完成人脸实名认证",
                        "L3_ELECTION_REQUIRED",
                        "LIMIT_ELECTION_VOTE",
                        true
                );
            }
            return EvaluationResult.denied(
                    "在线表决前请先完成 L2 实名认证",
                    "L2_COMMON_DECISION_REQUIRED",
                    "LIMIT_COMMON_DECISION_VOTE",
                    true
            );
        }

        return EvaluationResult.allowed();
    }
}
