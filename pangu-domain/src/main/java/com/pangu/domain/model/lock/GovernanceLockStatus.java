package com.pangu.domain.model.lock;

/**
 * 通用治理锁的状态枚举。
 *
 * <p>状态流转规则（不可逆，由 {@code GovernanceLock.transitionTo} + DB trigger 8 双重把守）：
 * <pre>
 *   LOCKED → COMMITTEE_SIGNED → FULLY_UNLOCKED
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>初签 / 终签 同步推进 status，避免 status 与 unlock_*_user_id 字段出现不一致；</li>
 *   <li>{@link #FULLY_UNLOCKED} 是终止态，业务可以基于此判定原始 entity 是否可被修改；</li>
 *   <li>逆向流转一律禁止：业务侧若需要"重新锁定"必须新建另一行记录。</li>
 * </ul>
 */
public enum GovernanceLockStatus {

    LOCKED(1),
    COMMITTEE_SIGNED(2),
    FULLY_UNLOCKED(3);

    private final int dbValue;

    GovernanceLockStatus(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public static GovernanceLockStatus fromDbValue(int dbValue) {
        for (GovernanceLockStatus status : values()) {
            if (status.dbValue == dbValue) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown GovernanceLockStatus dbValue: " + dbValue);
    }

    /**
     * @return 是否终止态（不再允许任何流转）
     */
    public boolean isTerminal() {
        return this == FULLY_UNLOCKED;
    }
}
