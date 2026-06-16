package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 计票与表决结算引擎抽象类 (使用模板方法模式与函数式编程)
 * @param <S> 投票议题类型
 * @param <R> 投票结算结果类型
 */
public abstract class AbstractVotingEngine<S extends VotingSubject, R extends VotingResult<S>> {

    /**
     * 结算模板方法：锁定外部流程，计算双参与参与率并回调业务表决逻辑
     * @param subject 表决议题
     * @param validVotes 已经过去重、共有产权及代投过滤后的有效投票列表
     * @param totalArea 小区计票总面积
     * @param totalOwnerCount 小区总业主数
     * @return 最终结算报告
     */
    public final R settle(S subject, List<VoteItem> validVotes, BigDecimal totalArea, long totalOwnerCount) {
        if (validVotes == null || totalArea == null || totalArea.compareTo(BigDecimal.ZERO) == 0 || totalOwnerCount <= 0) {
            throw new IllegalArgumentException("结算参数不合规：表决项列表、小区面积或总人数无效。");
        }

        // 1. 基于双重去重机制，精准计算实际参会业主的“总建筑面积（不同房屋累加，同房屋候选人票不累加）”与“总人数（合并一户多房/开发商存量房）”
        Set<Long> uniqueUids = new HashSet<>();
        Set<String> uniqueUidAndOpid = new HashSet<>();
        BigDecimal participatingArea = BigDecimal.ZERO;

        for (VoteItem vote : validVotes) {
            // 通过 "uid-opid" 组合去重累加参会房产的有效面积
            if (uniqueUidAndOpid.add(vote.getUid() + "-" + vote.getOpid())) {
                participatingArea = participatingArea.add(vote.getPropertyArea());
            }
            // 收集所有去重的独立自然人 ID (UID)
            uniqueUids.add(vote.getUid());
        }

        // 2. 实际参会独立业主总数（一户多套/开发商合并算作 1 人）
        long participatingOwnerCount = uniqueUids.size();

        // 3. 校验双参与门槛 (2/3)
        boolean quorumSatisfied = checkQuorum(participatingArea, totalArea, participatingOwnerCount, totalOwnerCount);

        // 4. 调用具体的子类策略算法判定该议题是否通过，并填充特定的结果字段
        return calculateResult(subject, validVotes, totalArea, totalOwnerCount, 
                participatingArea, participatingOwnerCount, quorumSatisfied);
    }

    /**
     * 校验双参与（2/3 面积及人数）开会比例
     */
    private boolean checkQuorum(BigDecimal participatingArea, BigDecimal totalArea, 
                                 long participatingOwnerCount, long totalOwnerCount) {
        // 专有面积参与率 >= 2/3，即 3 * participatingArea >= 2 * totalArea
        boolean areaQuorum = participatingArea.multiply(new BigDecimal("3"))
                .compareTo(totalArea.multiply(new BigDecimal("2"))) >= 0;

        // 人数参与率 >= 2/3，即 3 * participatingOwnerCount >= 2 * totalOwnerCount
        boolean ownerQuorum = participatingOwnerCount * 3 >= totalOwnerCount * 2;

        return areaQuorum && ownerQuorum;
    }

    /**
     * 子类策略算法接口：用于各事项（普通决定双过半、重大决定双3/4、差额选举排序）实现通过判定逻辑
     */
    protected abstract R calculateResult(S subject, List<VoteItem> validVotes, 
                                         BigDecimal totalArea, long totalOwnerCount, 
                                         BigDecimal participatingArea, long participatingOwnerCount, 
                                         boolean quorumSatisfied);
}
