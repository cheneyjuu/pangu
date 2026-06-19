package com.pangu.application.dispute.command;

/** 启动审查（行政机关受理）：RAISED → UNDER_REVIEW_LEVEL_<currentLevel>。 */
public record StartReviewCommand(Long disputeId) {
}
