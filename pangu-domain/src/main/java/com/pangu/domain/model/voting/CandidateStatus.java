package com.pangu.domain.model.voting;

/**
 * 候选人资格状态枚举（对应 t_election_candidate.qualification_status）。
 *
 * <p>状态流转：
 * <pre>
 *   PENDING_REVIEW --(approve)--&gt; APPROVED
 *   PENDING_REVIEW --(reject)--&gt;  REJECTED
 *   PENDING_REVIEW --(withdraw)--&gt; WITHDRAWN
 * </pre>
 *
 * <p>仅 {@link #APPROVED} 候选人计入候选人池快照与选举结算。审查/撤回均为终态。
 */
public enum CandidateStatus {

    PENDING_REVIEW(1),
    APPROVED(2),
    REJECTED(3),
    WITHDRAWN(4);

    private final int dbValue;

    CandidateStatus(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public static CandidateStatus fromDbValue(int dbValue) {
        for (CandidateStatus status : values()) {
            if (status.dbValue == dbValue) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown CandidateStatus dbValue: " + dbValue);
    }
}
