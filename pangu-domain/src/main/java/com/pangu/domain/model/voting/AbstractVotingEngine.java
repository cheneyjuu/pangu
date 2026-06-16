package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 计票与表决结算引擎抽象类 (使用模板方法模式与函数式编程)
 * @param <S> 投票议题类型
 * @param <R> 投票结算结果类型
 */
public abstract class AbstractVotingEngine<S extends VotingSubject, R extends VotingResult<S>> {

    /** 判定双参与通过所需要的法定基准比率 (2/3 = 0.6667) */
    private static final BigDecimal QUORUM_RATIO_THRESHOLD = new BigDecimal("2").divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);

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

        // 1. 基于 Stream 函数式累加参会专有建筑面积
        BigDecimal participatingArea = validVotes.stream()
                .map(VoteItem::getPropertyArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. 参会业主总人数 (传入的有效票已在应用层去重合并，一张有效票代表一个独立参与主体)
        long participatingOwnerCount = validVotes.size();

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
        BigDecimal areaRatio = participatingArea.divide(totalArea, 4, RoundingMode.HALF_UP);
        BigDecimal ownerRatio = BigDecimal.valueOf(participatingOwnerCount)
                .divide(BigDecimal.valueOf(totalOwnerCount), 4, RoundingMode.HALF_UP);

        // 必须同时满足：面积参与率 >= 2/3 且 人数参与率 >= 2/3
        return areaRatio.compareTo(QUORUM_RATIO_THRESHOLD) >= 0 
                && ownerRatio.compareTo(QUORUM_RATIO_THRESHOLD) >= 0;
    }

    /**
     * 子类策略算法接口：用于各事项（普通决定双过半、重大决定双3/4、差额选举排序）实现通过判定逻辑
     */
    protected abstract R calculateResult(S subject, List<VoteItem> validVotes, 
                                         BigDecimal totalArea, long totalOwnerCount, 
                                         BigDecimal participatingArea, long participatingOwnerCount, 
                                         boolean quorumSatisfied);
}
