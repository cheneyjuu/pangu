package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 计票与表决结算引擎抽象类（模板方法模式）。
 *
 * <p>本类破坏式变更点（M1 阶段）：
 * <ul>
 *   <li>{@code settle} 不再接受裸 {@code totalArea/totalOwnerCount}，而是接受经
 *       {@link VotingDenominatorResolver} 双重去重落定的 {@link Denominator} 不可变值对象。
 *       这是为了从根上消除「应过未过」的群体性诉讼级事故（一户多房 / 共有产权
 *       被 SQL 误算成 N 倍）。</li>
 *   <li>党员比例下限不再硬编码 50%，而是从 {@link VotingSubject#getEffectivePartyRatioFloor()}
 *       读取——application 层已根据放宽 waiver 的断路器结果写入正确值。</li>
 * </ul>
 *
 * @param <S> 投票议题类型
 * @param <R> 投票结算结果类型
 */
public abstract class AbstractVotingEngine<S extends VotingSubject, R extends VotingResult<S>> {

    /**
     * 结算模板方法：双重去重 + 引擎默认门槛 + 委派子类策略。
     *
     * @param subject     议题（含 scope / partyRatioFloor 等已被 application 写入的有效值）
     * @param validVotes  已过滤的有效投票列表
     * @param denom       不可变分母值对象（强校验后的快照）
     * @return 子类策略返回的最终结算报告
     */
    public final R settle(S subject, List<VoteItem> validVotes, Denominator denom) {
        return settle(subject, validVotes, denom, defaultDecisionRule());
    }

    /**
     * 以冻结的会议规则结算。
     *
     * <p>该重载仅由已锁定议事规则快照的正式业主大会调用；其他投票仍走上面的既有默认规则。
     */
    public final R settle(S subject,
                          List<VoteItem> validVotes,
                          Denominator denom,
                          VotingDecisionRule decisionRule) {
        if (subject == null) {
            throw new IllegalArgumentException("subject must not be null");
        }
        if (validVotes == null) {
            throw new IllegalArgumentException("validVotes must not be null");
        }
        if (denom == null) {
            throw new IllegalArgumentException("denominator must not be null");
        }
        if (decisionRule == null) {
            throw new IllegalArgumentException("decisionRule must not be null");
        }

        BigDecimal totalArea = denom.totalArea();
        long totalOwnerCount = denom.totalOwnerCount();

        // 1. 双重去重计算实际参会面积与人头
        Set<Long> uniqueUids = new HashSet<>();
        Set<String> uniqueUidAndOpid = new HashSet<>();
        BigDecimal participatingArea = BigDecimal.ZERO;

        for (VoteItem vote : validVotes) {
            if (uniqueUidAndOpid.add(vote.getUid() + "-" + vote.getOpid())) {
                participatingArea = participatingArea.add(vote.getPropertyArea());
            }
            uniqueUids.add(vote.getUid());
        }

        long participatingOwnerCount = uniqueUids.size();

        // 2. 已冻结规则的双维度参与门槛
        boolean quorumSatisfied = checkQuorum(
                participatingArea, totalArea, participatingOwnerCount, totalOwnerCount, decisionRule);

        // 3. 委派子类策略
        return calculateResult(subject, validVotes, totalArea, totalOwnerCount,
                participatingArea, participatingOwnerCount, quorumSatisfied, decisionRule);
    }

    private boolean checkQuorum(BigDecimal participatingArea, BigDecimal totalArea,
                                 long participatingOwnerCount,
                                 long totalOwnerCount,
                                 VotingDecisionRule decisionRule) {
        boolean areaQuorum = decisionRule.participationAreaThreshold()
                .isSatisfied(participatingArea, totalArea);
        boolean ownerQuorum = decisionRule.participationOwnerThreshold()
                .isSatisfied(participatingOwnerCount, totalOwnerCount);
        return areaQuorum && ownerQuorum;
    }

    /** 既有非业主大会投票维持原有规则，不由平台配置替代小区已备案规则。 */
    protected abstract VotingDecisionRule defaultDecisionRule();

    /**
     * 子类策略：双过半 / 双 3/4 / 差额选举多阶排序。
     */
    protected abstract R calculateResult(S subject, List<VoteItem> validVotes,
                                         BigDecimal totalArea, long totalOwnerCount,
                                         BigDecimal participatingArea, long participatingOwnerCount,
                                         boolean quorumSatisfied,
                                         VotingDecisionRule decisionRule);
}
