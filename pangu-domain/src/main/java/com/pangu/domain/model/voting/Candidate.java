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

    /** 所属议题 ID（t_election_candidate.subject_id；投票归属校验用） */
    private Long subjectId;

    /** 关联业主 uid（c_user.uid；同一议题内唯一，由 UNIQUE(subject_id,uid) 兜底） */
    private Long uid;

    /** 姓名 */
    private String name;

    /** 是否为中共党员 (PRD 提及原则上党员占比不低于 50%) */
    private boolean partyMember;

    /** 资格审查状态（提名时 PENDING_PARTY_REVIEW；过党组前置审查后 PENDING_COMMITTEE_REVIEW；居委会资格审查后 APPROVED/REJECTED；仅 APPROVED 计入结算） */
    private CandidateStatus qualificationStatus;

    /** 驳回理由码：C1-C5，仅 REJECTED 时有值。 */
    private String rejectReasonCode;

    /** 驳回证据链 JSON 字符串，对应 DB JSONB。 */
    private String rejectEvidenceJson;

    /** 驳回审查人 sys_user.user_id。 */
    private Long rejectReviewerUserId;

    /** 驳回发生阶段：PARTY_REVIEW / COMMITTEE_REVIEW。 */
    private String rejectReviewStage;
}
