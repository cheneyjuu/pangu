package com.pangu.domain.model.voting;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 选举最终表决结果报告 (继承自 VotingResult)
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ElectionVotingResult extends VotingResult<ElectionSubject> {

    /** 候选人得票统计结果列表 */
    private List<CandidateElectionResult> candidateResults;

    /** 最终依法当选的业委会委员名单 */
    private List<Candidate> winners;
}
