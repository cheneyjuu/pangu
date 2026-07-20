// 关联业务：将业主大会冻结的议事规则计票口径传递给结算引擎，并写入结果存证。
package com.pangu.domain.model.voting;

import java.util.Set;

/**
 * 单次结算的规则来源。
 *
 * <p>普通投票仍可使用既有领域默认引擎；业主大会必须传入其冻结规则快照，避免后来规则变更
 * 影响正在办理或已经结算的会议。
 */
public record VotingSettlementPolicy(
        VotingDecisionRule decisionRule,
        Long ruleSnapshotId,
        String ruleConfigurationSha256,
        VotingNonResponsePolicy nonResponsePolicy,
        Set<String> validDeliveryMethods
) {

    public VotingSettlementPolicy {
        validDeliveryMethods = validDeliveryMethods == null ? Set.of() : Set.copyOf(validDeliveryMethods);
    }

    public void requireExecutable() {
        if (decisionRule == null || ruleSnapshotId == null || ruleConfigurationSha256 == null
                || ruleConfigurationSha256.isBlank()) {
            throw new IllegalArgumentException("结算规则快照不完整");
        }
        if (nonResponsePolicy == null) {
            throw new IllegalArgumentException("结算规则缺少未反馈认定方式");
        }
        if (validDeliveryMethods.isEmpty()) {
            throw new IllegalArgumentException("结算规则缺少有效送达方式");
        }
        decisionRule.requireExecutable();
    }
}
