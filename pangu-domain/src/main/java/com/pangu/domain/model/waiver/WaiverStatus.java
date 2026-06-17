package com.pangu.domain.model.waiver;

/**
 * 党员比例放宽申请状态。
 *
 * <p>状态流转规则（不可逆，由 {@code PartyRatioWaiver.transitionTo} 强校验）：
 * <pre>
 *   DRAFT → PENDING_COMMITTEE → PENDING_STREET → APPROVED → APPLIED
 *                ↓                    ↓              ↓
 *             REJECTED            REJECTED        REVOKED / REVOKED_BY_SYSTEM
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>下级初审 → 上级终审：符合中国治理体系「下级初审/背书 → 上级终审/盖章」</li>
 *   <li>禁止任何逆向流转（已 APPROVED 不可降回 PENDING_*）</li>
 *   <li>{@link #REVOKED_BY_SYSTEM}：断路器自动处置（非人工撤销）</li>
 *   <li>{@link #APPLIED}：议题结算时实际生效快照（结算引擎读取的 ratio 来源）</li>
 * </ul>
 */
public enum WaiverStatus {

    DRAFT(1),
    PENDING_COMMITTEE(2),
    PENDING_STREET(3),
    APPROVED(4),
    REJECTED(5),
    REVOKED(6),
    REVOKED_BY_SYSTEM(7),
    APPLIED(8);

    private final int dbValue;

    WaiverStatus(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public static WaiverStatus fromDbValue(int dbValue) {
        for (WaiverStatus status : values()) {
            if (status.dbValue == dbValue) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown WaiverStatus dbValue: " + dbValue);
    }

    /**
     * @return 是否终止态（不再允许任何流转）
     */
    public boolean isTerminal() {
        return this == REJECTED || this == REVOKED || this == REVOKED_BY_SYSTEM || this == APPLIED;
    }
}
