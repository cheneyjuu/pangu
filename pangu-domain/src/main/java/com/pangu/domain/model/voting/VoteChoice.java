package com.pangu.domain.model.voting;

/**
 * 投票表决选项枚举。
 *
 * <p>对应 {@code t_vote_item.choice}（SMALLINT，CHECK IN (1,2,3)）。
 * dbValue 映射在此固化为枚举一等公民，消除散落在
 * {@code VoteItemRepositoryImpl} / 只读投影查询里的 magic number。
 */
public enum VoteChoice {
    /** 赞成 */
    SUPPORT(1),
    /** 反对 */
    AGAINST(2),
    /** 弃权 */
    ABSTAIN(3);

    private final int dbValue;

    VoteChoice(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public static VoteChoice fromDbValue(int dbValue) {
        for (VoteChoice choice : values()) {
            if (choice.dbValue == dbValue) {
                return choice;
            }
        }
        throw new IllegalArgumentException("Unknown VoteChoice dbValue: " + dbValue);
    }
}
