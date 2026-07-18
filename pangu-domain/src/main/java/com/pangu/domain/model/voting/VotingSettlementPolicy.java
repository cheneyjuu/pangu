// 关联业务：将业主大会冻结的议事规则计票口径传递给结算引擎，并写入结果存证。
package com.pangu.domain.model.voting;

/**
 * 单次结算的规则来源。
 *
 * <p>普通投票仍可使用既有领域默认引擎；业主大会必须传入其冻结规则快照，避免后来规则变更
 * 影响正在办理或已经结算的会议。
 */
public record VotingSettlementPolicy(
        VotingDecisionRule decisionRule,
        Long ruleSnapshotId,
        String ruleConfigurationSha256
) {

    public void requireExecutable() {
        if (decisionRule == null || ruleSnapshotId == null || ruleConfigurationSha256 == null
                || ruleConfigurationSha256.isBlank()) {
            throw new IllegalArgumentException("结算规则快照不完整");
        }
        decisionRule.requireExecutable();
    }
}
