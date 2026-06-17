package com.pangu.domain.model.voting;

/**
 * 议题状态枚举（对应 t_voting_subject.status）。
 *
 * <p>当前期允许的状态流转（单向不可逆）：
 * <pre>
 *   DRAFT --(publish)--&gt; PUBLISHED --(open vote)--&gt; VOTING --(deadline)--&gt; CLOSED --(settle)--&gt; SETTLED
 * </pre>
 */
public enum SubjectStatus {

    DRAFT(1),
    PUBLISHED(2),
    VOTING(3),
    CLOSED(4),
    SETTLED(5);

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
