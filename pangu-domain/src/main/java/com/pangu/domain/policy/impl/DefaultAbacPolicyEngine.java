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
    public EvaluationResult evaluateVoting(Long uid, Long tenantId, AuthenticationLevel currentLevel) {
        // 电子大表决强制要求 L3 级（刷脸实名）极其以上
        if (currentLevel == null || currentLevel.getValue() < AuthenticationLevel.L3.getValue()) {
            return EvaluationResult.denied(
                    "拦截限制：重大事项电子表决需要确保为产权人本人操作。根据法效力合规要求，请先完成 L3 级实名活体认证（人脸识别），或转由线下兜底渠道核销。",
                    "L3_REQUIREMENT",
                    "LIMIT_VOTE_SUBMIT",
                    true
            );
        }

        return EvaluationResult.allowed();
    }
}
