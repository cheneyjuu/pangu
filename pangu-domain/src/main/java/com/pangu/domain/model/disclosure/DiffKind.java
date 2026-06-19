package com.pangu.domain.model.disclosure;

/**
 * 字段级差分的三类标记（W/R/N）。
 *
 * <p>语义（写在 {@link DisclosureDiff} JavaDoc 与 PRD 一致）：
 * <ul>
 *   <li>{@link #WRITE} —— 当期写入或修改：path 在 prev/curr 都存在但值不同，或 path 仅在 curr 存在；</li>
 *   <li>{@link #READ} —— 当期未写入但引用了历史字段：path 仅在 prev 存在（被移除）；</li>
 *   <li>{@link #NO_CHANGE} —— 两期完全一致。</li>
 * </ul>
 *
 * <p>「READ」语义在 PRD 中表达「当期沿用上一期的值，未做修改」，因此把
 * 「prev 有 / curr 无」归并到 R 域，便于审计端识别「丢字段」与「沿用」的合并风险。
 */
public enum DiffKind {
    WRITE,
    READ,
    NO_CHANGE
}
