package com.pangu.domain.model.voting;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 单个候选人的计票结果统计实体 (领域模型)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateElectionResult {

    /** 对应候选人 */
    private Candidate candidate;

    /** 所得赞成票专有面积 */
    private BigDecimal supportArea;

    /** 所得赞成票业主人数 */
    private long supportOwnerCount;

    /** 赞成票是否双过半（占参会面积及人数的半数以上） */
    private boolean passedHalf;

    /**
     * 差额选举排序得分 (Score)
     * Score = (所得赞成面积 / 参会总面积) + (所得赞成人数 / 参会总人数)
     */
    private BigDecimal score;

    /**
     * 在线抽签随机值（用于面积和综合分均平票时的仲裁逻辑，由系统自动生成）
     */
    private Integer drawValue;
}
