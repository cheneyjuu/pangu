package com.pangu.domain.model.voting;

/**
 * 议题分母范围（社区/楼栋/单元/维修方案费用承担房屋）。
 *
 * <p>对应 V2.0 t_voting_subject.scope，决定 {@link VotingDenominatorResolver}
 * 应当将多大空间内的房产计入分母：
 * <ul>
 *   <li>{@link #COMMUNITY}：整个 tenant 范围（业委会换届、公共维修资金大项等）</li>
 *   <li>{@link #BUILDING}：仅本楼栋范围（楼栋专项电梯改造）</li>
 *   <li>{@link #UNIT}：仅本单元范围（单元立管入户改造等）</li>
 *   <li>{@link #REPAIR_ALLOCATION}：维修授权提案已冻结的精确费用承担房屋集合</li>
 * </ul>
 */
public enum VotingScope {

    /** 社区/小区级（默认）。 */
    COMMUNITY(1),

    /** 楼栋级。 */
    BUILDING(2),

    /** 单元级。 */
    UNIT(3),

    /**
     * 维修方案级精确决定范围；scopeReferenceId 为 planId，分母只能由方案冻结房屋集合写入，
     * 不能按楼栋或小区实时名册推导。
     */
    REPAIR_ALLOCATION(4);

    private final int dbValue;

    VotingScope(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public static VotingScope fromDbValue(int dbValue) {
        for (VotingScope scope : values()) {
            if (scope.dbValue == dbValue) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown VotingScope dbValue: " + dbValue);
    }
}
