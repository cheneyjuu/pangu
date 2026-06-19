package com.pangu.domain.model.dispute;

/**
 * 行政机关层级决议结论 ENUM。
 *
 * <p>与 V2.8 {@code chk_decision_kind} CHECK 严格对齐；同时决定主表 status 流转：
 * <ul>
 *   <li>{@link #UPHELD} —— 业主诉求被支持，主表流转 DECIDED_LEVEL_N_UPHELD（终态等业主 conclude）；</li>
 *   <li>{@link #REJECTED} —— 业主诉求被驳回，主表流转 DECIDED_LEVEL_N_REJECTED（业主可继续 escalate）；</li>
 *   <li>{@link #PARTIAL_UPHELD} —— 部分支持，主表流转 DECIDED_LEVEL_N_PARTIAL（终态等业主 conclude）。</li>
 * </ul>
 */
public enum DecisionKind {

    UPHELD,
    REJECTED,
    PARTIAL_UPHELD
}
