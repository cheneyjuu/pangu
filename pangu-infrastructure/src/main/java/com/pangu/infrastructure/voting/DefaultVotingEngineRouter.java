package com.pangu.infrastructure.voting;

import com.pangu.domain.model.voting.AbstractVotingEngine;
import com.pangu.domain.model.voting.Denominator;
import com.pangu.domain.model.voting.GeneralDecisionEngine;
import com.pangu.domain.model.voting.MajorDecisionEngine;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingEngineRouter;
import com.pangu.domain.model.voting.VotingResult;
import com.pangu.domain.model.voting.VotingSubject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认引擎路由：根据 {@link SubjectType} 分派到三个领域引擎之一。
 *
 * <p>本期 ELECTION 议题需 {@code ElectionSubject} 完整加载链路（含候选人 +
 * maxWinners），尚未在本 commit 接入；遇到 ELECTION 抛
 * {@link UnsupportedSubjectTypeException}，由 application 层映射为
 * {@code SUBJECT_TYPE_NOT_SUPPORTED} 错误码。
 */
@Component
@RequiredArgsConstructor
public class DefaultVotingEngineRouter implements VotingEngineRouter {

    private final MajorDecisionEngine majorDecisionEngine;
    private final GeneralDecisionEngine generalDecisionEngine;

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
            case ELECTION -> throw new UnsupportedSubjectTypeException(
                    "ELECTION 议题结算尚未接入完整候选人加载链路（M1 part 2 限制），请等待后续迭代 subjectId=" + subject.getSubjectId());
        };
    }
}
