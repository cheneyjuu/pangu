// 关联业务：承载属地街镇审核或平台受控代审决定。
package com.pangu.application.registration.command;

import com.pangu.domain.model.registration.CommunityRegistrationDecision;
import com.pangu.domain.model.registration.CommunityRegistrationReviewMode;

/**
 * 小区注册审核命令。
 */
public record ReviewCommunityRegistrationCommand(
        CommunityRegistrationDecision decision,
        CommunityRegistrationReviewMode reviewMode,
        String reviewComment,
        String fallbackReason,
        Integer expectedVersion
) {
}
