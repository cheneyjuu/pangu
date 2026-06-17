package com.pangu.domain.model.voting;

/**
 * 议题类型枚举（对应 t_voting_subject.subject_type）。
 *
 * <p>三类议题共享 {@link VotingSubject} 主表，但适配不同的结算引擎：
 * <ul>
 *   <li>{@link #ELECTION}：业委会差额选举，使用 {@code ElectionVotingEngine}（依赖
 *       {@link ElectionSubject} 携带候选人 + 应选席位数）；</li>
 *   <li>{@link #MAJOR}：双 3/4 重大决议（如专项维修资金筹集），使用
 *       {@code MajorDecisionEngine}；</li>
 *   <li>{@link #GENERAL}：双过半数普通决议，使用 {@code GeneralDecisionEngine}。</li>
 * </ul>
 */
public enum SubjectType {

    /** 业委会差额选举。 */
    ELECTION(1),

    /** 双 3/4 重大决议。 */
    MAJOR(2),

    /** 双过半数一般决议。 */
    GENERAL(3);

    private final int dbValue;

    SubjectType(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public static SubjectType fromDbValue(int dbValue) {
        for (SubjectType type : values()) {
            if (type.dbValue == dbValue) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown SubjectType dbValue: " + dbValue);
    }
}
