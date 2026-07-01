package com.pangu.domain.model.handover;

/**
 * 租户任期状态，对应换届闭环中的 HANDOVER_LOCK。
 */
public enum TenantTermStatus {

    /** 常态：敏感治理动作按原权限和业务规则执行。 */
    NORMAL(1),

    /** 换届锁定：选举已结算但街道办尚未完成换届备案。 */
    HANDOVER_LOCK(2);

    private final int dbValue;

    TenantTermStatus(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public static TenantTermStatus fromDbValue(int dbValue) {
        for (TenantTermStatus status : values()) {
            if (status.dbValue == dbValue) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TenantTermStatus dbValue: " + dbValue);
    }
}
