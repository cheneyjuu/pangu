// 关联业务：按事项类型路由表决结算，并保留实际票与规则认定票的来源。
package com.pangu.infrastructure.voting;

import com.pangu.domain.model.voting.AbstractVotingEngine;
import com.pangu.domain.model.voting.Denominator;
import com.pangu.domain.model.voting.CountedVote;
import com.pangu.domain.model.voting.ElectionSubject;
import com.pangu.domain.model.voting.ElectionVotingEngine;
import com.pangu.domain.model.voting.GeneralDecisionEngine;
import com.pangu.domain.model.voting.MajorDecisionEngine;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingEngineRouter;
import com.pangu.domain.model.voting.VotingResult;
import com.pangu.domain.model.voting.VotingSettlementPolicy;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认引擎路由：根据 {@link SubjectType} 分派到三个领域引擎之一。
 *
 * <p>M3-3 起 ELECTION 链路接通：{@code VotingSubjectRepositoryImpl.toAggregate} 已把
 * ELECTION 行构造为 {@link ElectionSubject}（携带 maxWinners，候选人 list 暂空）；本路由
 * 在 settle 前通过 {@link ElectionCandidateRegistry#findApprovedCandidates} 回填 APPROVED
 * 候选人，再交 {@link ElectionVotingEngine} 计票。
 */
@Component
@RequiredArgsConstructor
public class DefaultVotingEngineRouter implements VotingEngineRouter {

    private final MajorDecisionEngine majorDecisionEngine;
    private final GeneralDecisionEngine generalDecisionEngine;
    private final ElectionVotingEngine electionVotingEngine;
    private final ElectionCandidateRegistry electionCandidateRegistry;

    @Override
    public VotingResult<? extends VotingSubject> settle(VotingSubject subject,
                                                         List<VoteItem> validVotes,
                                                         Denominator denom) {
        SubjectType type = subject.getSubjectType();
        if (type == null) {
            throw new IllegalArgumentException("subject.subjectType must not be null");
        }
        return switch (type) {
            case GENERAL -> ((AbstractVotingEngine<VotingSubject, VotingResult<VotingSubject>>)
                    (AbstractVotingEngine<?, ?>) generalDecisionEngine).settle(subject, validVotes, denom);
            case MAJOR -> ((AbstractVotingEngine<VotingSubject, VotingResult<VotingSubject>>)
                    (AbstractVotingEngine<?, ?>) majorDecisionEngine).settle(subject, validVotes, denom);
            case ELECTION -> {
                if (!(subject instanceof ElectionSubject electionSubject)) {
                    throw new IllegalStateException(
                            "ELECTION 议题未被构造为 ElectionSubject，toAggregate 链路异常 subjectId=" + subject.getSubjectId());
                }
                electionSubject.setCandidates(
                        electionCandidateRegistry.findApprovedCandidates(electionSubject.getSubjectId()));
                yield electionVotingEngine.settle(electionSubject, validVotes, denom);
            }
        };
    }

    @Override
    public VotingResult<? extends VotingSubject> settle(VotingSubject subject,
                                                         List<VoteItem> validVotes,
                                                         Denominator denom,
                                                         VotingSettlementPolicy settlementPolicy) {
        if (settlementPolicy == null) {
            return settle(subject, validVotes, denom);
        }
        settlementPolicy.requireExecutable();
        SubjectType type = subject.getSubjectType();
        if (type == null) {
            throw new IllegalArgumentException("subject.subjectType must not be null");
        }
        return switch (type) {
            case GENERAL -> ((AbstractVotingEngine<VotingSubject, VotingResult<VotingSubject>>)
                    (AbstractVotingEngine<?, ?>) generalDecisionEngine).settle(
                            subject, validVotes, denom, settlementPolicy.decisionRule());
            case MAJOR -> ((AbstractVotingEngine<VotingSubject, VotingResult<VotingSubject>>)
                    (AbstractVotingEngine<?, ?>) majorDecisionEngine).settle(
                            subject, validVotes, denom, settlementPolicy.decisionRule());
            case ELECTION -> throw new UnsupportedSubjectTypeException(
                    "业主大会议事规则快照不能用于业委会选举事项");
        };
    }

    @Override
    public VotingResult<? extends VotingSubject> settleCounted(VotingSubject subject,
                                                                List<CountedVote> countedVotes,
                                                                Denominator denom,
                                                                VotingSettlementPolicy settlementPolicy) {
        if (settlementPolicy == null) {
            throw new IllegalArgumentException("正式分源计票必须提供冻结规则");
        }
        settlementPolicy.requireExecutable();
        return switch (subject.getSubjectType()) {
            case GENERAL -> generalDecisionEngine.settleCounted(
                    subject, countedVotes, denom, settlementPolicy.decisionRule());
            case MAJOR -> majorDecisionEngine.settleCounted(
                    subject, countedVotes, denom, settlementPolicy.decisionRule());
            case ELECTION -> throw new UnsupportedSubjectTypeException(
                    "业主大会议事规则快照不能用于业委会选举事项");
        };
    }
}
