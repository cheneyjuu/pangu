package com.pangu.domain.model.dispute;

import java.time.Instant;

/**
 * 行政机关层级决议值对象（一行 t_dispute_review_decision 的语义快照）。
 *
 * <p>决议是不可变的：聚合根 {@link Dispute#decide(DecisionKind, Long, String, String)}
 * 返回新构造的 {@code Decision}，由 application 层调用 repository 落库。
 */
public record Decision(
        Long decisionId,
        Long disputeId,
        int reviewLevel,
        Long decidedByUserId,
        DecisionKind kind,
        String content,
        String docUrl,
        Instant decidedAt
) {
}
