package com.pangu.domain.model.voting;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 选举议题实体 (继承自 VotingSubject)
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ElectionSubject extends VotingSubject {

    /** 本次选举的所有候选人列表 */
    private List<Candidate> candidates;

    /** 本届业委会委员拟选人数（席位数） */
    private int maxWinners;
}
