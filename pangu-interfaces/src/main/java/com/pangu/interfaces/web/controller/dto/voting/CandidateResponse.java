package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidateStatus;

/**
 * 候选人视图（M3-3，管理端 + C 端共用）。
 */
public record CandidateResponse(
        Long candidateId,
        Long subjectId,
        Long uid,
        String name,
        boolean partyMember,
        CandidateStatus qualificationStatus
) {
    public static CandidateResponse from(Candidate c) {
        return new CandidateResponse(
                c.getCandidateId(),
                c.getSubjectId(),
                c.getUid(),
                c.getName(),
                c.isPartyMember(),
                c.getQualificationStatus());
    }
}
