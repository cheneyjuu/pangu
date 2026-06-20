package com.pangu.domain.model.voting;

/**
 * 议题状态枚举（对应 t_voting_subject.status）。
 *
 * <p>当前期允许的状态流转（单向不可逆）：
 * <pre>
 *   DRAFT --(publish)--&gt; PUBLISHED --(open vote)--&gt; VOTING --(deadline)--&gt; CLOSED --(settle)--&gt; SETTLED
 *   DRAFT --(cancel by proposer)--&gt; CANCELLED
 *   PUBLISHED --(force cancel by GOV)--&gt; CANCELLED
 * </pre>
 *
 * <p>{@link #CANCELLED} 在 M3-2 引入，是与 SETTLED 并列的终态。一旦进入 VOTING/CLOSED/SETTLED 即不可撤回。
 */
public enum SubjectStatus {

    DRAFT(1),
    PUBLISHED(2),
    VOTING(3),
    CLOSED(4),
    SETTLED(5),
    CANCELLED(6);

    private final int dbValue;

    SubjectStatus(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public static SubjectStatus fromDbValue(int dbValue) {
        for (SubjectStatus status : values()) {
            if (status.dbValue == dbValue) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown SubjectStatus dbValue: " + dbValue);
    }
}
