// 关联业务：结构化记录每轮小区注册审核决定与代审依据。
package com.pangu.domain.model.registration;

import java.time.Instant;

/**
 * 小区注册审核记录。
 */
public record CommunityRegistrationReview(
        Long reviewId,
        Long applicationId,
        CommunityRegistrationDecision decision,
        CommunityRegistrationReviewMode reviewMode,
        Long reviewerAccountId,
        Long reviewerUserId,
        Long reviewerDeptId,
        String reviewComment,
        String fallbackReason,
        Instant createdAt
) {
}
