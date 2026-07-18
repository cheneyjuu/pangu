package com.pangu.domain.model.voting;

import java.util.List;

/**
 * 结算引擎路由器端口（领域定义；实现由 application 自身或 infrastructure 提供）。
 *
 * <p>application 服务层需要根据议题类型派发到不同的 {@link AbstractVotingEngine}
 * 子类，但应用层不关心具体引擎的实例化与注入；本端口屏蔽该装配细节。
 */
public interface VotingEngineRouter {

    /**
     * 根据议题类型派发并执行结算。
     *
     * @param subject    议题（已被 application 写入 effectivePartyRatioFloor）
     * @param validVotes 有效投票
     * @param denom      不可变分母
     * @return 引擎产出的结算结果
     * @throws UnsupportedSubjectTypeException 当前期暂未支持该议题类型
     */
    VotingResult<? extends VotingSubject> settle(VotingSubject subject,
                                                  List<VoteItem> validVotes,
                                                  Denominator denom);

    /**
     * 以冻结规则快照结算；仅适用于已明确计票规则的一般、重大决议。
     */
    VotingResult<? extends VotingSubject> settle(VotingSubject subject,
                                                  List<VoteItem> validVotes,
                                                  Denominator denom,
                                                  VotingSettlementPolicy settlementPolicy);

    /**
     * 议题类型未注册引擎时抛出（如 ELECTION 引擎需 ElectionSubject 加载链路尚未接入）。
     */
    class UnsupportedSubjectTypeException extends RuntimeException {
        public UnsupportedSubjectTypeException(String message) {
            super(message);
        }
    }
}
