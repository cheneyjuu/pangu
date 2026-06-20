package com.pangu.domain.model.voting;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 选举议题实体 (继承自 VotingSubject)。
 *
 * <p>{@code maxWinners}（应选名额）由基类 {@link VotingSubject#getMaxWinners()} 承载——
 * M3-3 起基类持有该字段以支持持久化透传，ELECTION 必非空（V3.1 trigger 13 兜底），
 * 引擎读取时自动拆箱。本类仅扩展候选人列表。
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ElectionSubject extends VotingSubject {

    /** 本次选举的所有候选人列表 */
    private List<Candidate> candidates;
}
