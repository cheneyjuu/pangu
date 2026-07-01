package com.pangu.domain.model.lock;

/**
 * 通用治理锁的实体类型。
 *
 * <p>每种实体类型对应一类需要"业委会主任 + 街道办双签"才能解锁修改的业务对象：
 * <ul>
 *   <li>{@link #FINANCE_DISCLOSURE} —— 财务公示快照（M2-3 落地后启用）</li>
 *   <li>{@link #ELECTION_DISCLOSURE} —— 选举公示快照</li>
 *   <li>{@link #FUND_LEDGER_PUBLISH} —— 维修资金台账期末发布</li>
 *   <li>{@link #TRUST_FUND_PAYMENT} —— 信托制动账付款指令</li>
 * </ul>
 *
 * <p>枚举名直接作为 DB {@code entity_type} 字段（VARCHAR(32)）存储，与 V2.5 的
 * {@code chk_lock_entity_type} CHECK 约束严格对齐——新增类型时必须同步迁移文件。
 */
public enum LockEntityType {

    FINANCE_DISCLOSURE,
    ELECTION_DISCLOSURE,
    FUND_LEDGER_PUBLISH,
    TRUST_FUND_PAYMENT;

    public static LockEntityType fromDbValue(String dbValue) {
        if (dbValue == null) {
            throw new IllegalArgumentException("LockEntityType dbValue must not be null");
        }
        for (LockEntityType type : values()) {
            if (type.name().equals(dbValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LockEntityType dbValue: " + dbValue);
    }
}
