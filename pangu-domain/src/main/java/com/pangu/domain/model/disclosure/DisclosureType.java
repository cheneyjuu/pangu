package com.pangu.domain.model.disclosure;

/**
 * 财务公示类型。
 *
 * <p>本期 M2-3 仅 {@link #MAINTENANCE_FUND} 真正可用：
 * <ul>
 *   <li>{@link #COMMON_FUND} —— 共有资金 / 公共收益（占位枚举，application 层调用走该值直接抛
 *       {@code DISCLOSURE_TYPE_NOT_SUPPORTED}），需等共有资金账户落地后启用；</li>
 *   <li>{@link #MAINTENANCE_FUND} —— 专项维修资金，复用 V2.2 的 t_maintenance_fund_account
 *       + t_fund_ledger_entry 做只读聚合。</li>
 * </ul>
 *
 * <p>枚举名直接作为 DB {@code disclosure_type} 字段（VARCHAR(32)）存储，
 * 与 V2.7 的 {@code chk_disc_type} CHECK 约束严格对齐——新增类型时必须同步迁移文件。
 */
public enum DisclosureType {

    COMMON_FUND,
    MAINTENANCE_FUND;

    public static DisclosureType fromDbValue(String dbValue) {
        if (dbValue == null) {
            throw new IllegalArgumentException("DisclosureType dbValue must not be null");
        }
        for (DisclosureType type : values()) {
            if (type.name().equals(dbValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DisclosureType dbValue: " + dbValue);
    }
}
