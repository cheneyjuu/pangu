package com.pangu.domain.model.voting;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 业委会选举候选人实体 (领域模型)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {

    /** 候选人 ID */
    private Long candidateId;

    /** 姓名 */
    private String name;

    /** 是否为中共党员 (PRD 提及原则上党员占比不低于 50%) */
    private boolean partyMember;
}
