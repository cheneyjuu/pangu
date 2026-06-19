package com.pangu.domain.model.disclosure;

/**
 * 财务公示快照的生命周期状态。
 *
 * <p>状态流转规则（由 {@link FinanceDisclosureSnapshot#transitionTo} + DB trigger 9 双重把守）：
 * <pre>
 *   DRAFT(1) → LOCKED(2) → PUBLISHED(3) → REVISING(4) → DRAFT(1)（开始下一版本周期）
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>跨级前进禁止（如 DRAFT → PUBLISHED 必须经 LOCKED）；</li>
 *   <li>同级回退禁止（如 PUBLISHED → LOCKED）；</li>
 *   <li>{@link #REVISING} 不是终止态，回到 DRAFT 后会作为下一个 statisticsVersion 起步；</li>
 *   <li>{@link #PUBLISHED} 是业主可读边界——状态低于 PUBLISHED 时业主端 GET 抛
 *       {@code DISCLOSURE_NOT_PUBLISHED}。</li>
 * </ul>
 */
public enum DisclosureStatus {

    DRAFT(1),
    LOCKED(2),
    PUBLISHED(3),
    REVISING(4);

    private final int dbValue;

    DisclosureStatus(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }

    public static DisclosureStatus fromDbValue(int dbValue) {
        for (DisclosureStatus status : values()) {
            if (status.dbValue == dbValue) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown DisclosureStatus dbValue: " + dbValue);
    }

    /**
     * @return 是否「业主可读」状态。仅 {@link #PUBLISHED} 视为可对外公开。
     */
    public boolean isReadableByOwner() {
        return this == PUBLISHED;
    }
}
