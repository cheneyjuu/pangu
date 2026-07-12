// 关联业务：校验属地街镇审核或平台代审提交的决定和审计说明。
package com.pangu.interfaces.web.controller.dto.registration;

import com.pangu.application.registration.command.ReviewCommunityRegistrationCommand;
import com.pangu.domain.model.registration.CommunityRegistrationDecision;
import com.pangu.domain.model.registration.CommunityRegistrationReviewMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 小区注册审核请求。
 */
public record ReviewCommunityRegistrationRequest(
        @NotNull CommunityRegistrationDecision decision,
        @NotNull CommunityRegistrationReviewMode reviewMode,
        @Size(max = 1000) String reviewComment,
        @Size(max = 1000) String fallbackReason,
        @NotNull @PositiveOrZero Integer expectedVersion
) {
    public ReviewCommunityRegistrationCommand toCommand() {
        return new ReviewCommunityRegistrationCommand(
                decision, reviewMode, reviewComment, fallbackReason, expectedVersion);
    }
}
