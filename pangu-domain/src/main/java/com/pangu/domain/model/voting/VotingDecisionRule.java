// 关联业务：将一类业主大会表决事项冻结为人数和面积双维度的参与、同意计票规则。
package com.pangu.domain.model.voting;

/**
 * 某一类表决事项的可执行计票规则。
 *
 * <p>参与门槛以总人数、总专有面积为分母；同意门槛以实际参与人数、参与面积为分母。
 */
public record VotingDecisionRule(
        VotingThreshold participationOwnerThreshold,
        VotingThreshold participationAreaThreshold,
        VotingThreshold approvalOwnerThreshold,
        VotingThreshold approvalAreaThreshold
) {

    /**
     * 参与和同意均须同时给出人数、面积及比较关系，缺一项即不得进入正式计票。
     */
    public void requireExecutable() {
        if (participationOwnerThreshold == null || participationAreaThreshold == null
                || approvalOwnerThreshold == null || approvalAreaThreshold == null) {
            throw new IllegalStateException("表决计票规则未完成规则确认");
        }
        participationOwnerThreshold.requireExecutable();
        participationAreaThreshold.requireExecutable();
        approvalOwnerThreshold.requireExecutable();
        approvalAreaThreshold.requireExecutable();
    }
}
