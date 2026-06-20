package com.pangu.domain.model.voting;

/**
 * 候选人资格状态枚举（对应 t_election_candidate.qualification_status）。
 *
 * <p>状态流转（M3-3 引入单段审查；本期插入党组书记前置审查后变为两段）：
 * <pre>
 *   PENDING_PARTY_REVIEW --(partyApprove)--&gt; PENDING_COMMITTEE_REVIEW   党组书记前置审查通过
 *   PENDING_PARTY_REVIEW --(reject)--&gt;       REJECTED                   党组书记前置审查驳回
 *   PENDING_COMMITTEE_REVIEW --(approve)--&gt;  APPROVED                   居委会资格审查通过
 *   PENDING_COMMITTEE_REVIEW --(reject)--&gt;   REJECTED                   居委会资格审查驳回
 * </pre>
 *
 * <p>提名落点为 {@link #PENDING_PARTY_REVIEW}；必须先过党组书记前置审查（政治/资格初筛）
 * 才进入 {@link #PENDING_COMMITTEE_REVIEW}（居委会资格审查）。仅 {@link #APPROVED} 候选人
 * 计入候选人池快照与选举结算。{@link #APPROVED} / {@link #REJECTED} / {@link #WITHDRAWN} 均为终态。
 *
 * <p>dbValue 约定：党组前置审查待办沿用历史值 {@code 1}（原 PENDING_REVIEW），
 * 故老数据零迁移；居委会待办为新增值 {@code 5}。
 */
public enum CandidateStatus {

    PENDING_PARTY_REVIEW(1),
    APPROVED(2),
    REJECTED(3),
    WITHDRAWN(4),
    PENDING_COMMITTEE_REVIEW(5);

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
