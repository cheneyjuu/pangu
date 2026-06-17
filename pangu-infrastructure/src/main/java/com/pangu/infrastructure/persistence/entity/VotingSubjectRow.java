package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * t_voting_subject 行映射。
 *
 * <p>本期仅承载 {@code VotingSubject} 聚合所需的字段，不映射 {@code subject_type / publish_at /
 * vote_start_at / max_winners} 等仅由后续 Phase 7 集成测试用到的列；待用到时再补。
 */
@Data
public class VotingSubjectRow {

    private Long subjectId;
    private Long tenantId;
    private String title;

    /** 1-ELECTION, 2-MAJOR, 3-GENERAL；当前 application 流程不消费但保留映射。 */
    private Integer subjectType;

    /** 1-COMMUNITY, 2-BUILDING（UNIT 暂未实现）。 */
    private Integer scope;
    private Long scopeReferenceId;

    /** 1-DRAFT, 2-PUBLISHED, 3-VOTING, 4-CLOSED, 5-SETTLED。 */
    private Integer status;

    private Instant publishAt;
    private Instant voteStartAt;
    private Instant voteEndAt;
    private Instant settledAt;

    private BigDecimal partyRatioFloor;
    private Integer maxWinners;

    private long version;
    private Instant createTime;
}
