package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * t_election_candidate 行映射。
 *
 * <p>M3-3 起承载候选人提名/审查/列表所需字段。{@code isPartyMember} 列为 SMALLINT(0/1)，
 * {@code qualificationStatus} 为 SMALLINT(1-4，见 {@code CandidateStatus})。
 */
@Data
public class ElectionCandidateRow {

    private Long candidateId;
    private Long subjectId;
    private Long uid;
    private String name;

    /** 0-非党员, 1-党员。 */
    private Integer isPartyMember;

    /** 1-PENDING_PARTY_REVIEW, 2-APPROVED, 3-REJECTED, 4-WITHDRAWN, 5-PENDING_COMMITTEE_REVIEW。 */
    private Integer qualificationStatus;

    private String rejectReasonCode;
    private String rejectEvidenceJson;
    private Long rejectReviewerUserId;
    private String rejectReviewStage;
    private Instant rejectedAt;

    private Instant createTime;
}
