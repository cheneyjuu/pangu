// 关联业务：校验街道办对物业管理模式变更申请作出的退回、驳回或执行决定。
package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.command.ReviewPropertyManagementModeChangeCommand;
import com.pangu.domain.model.community.PropertyManagementModeChangeDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 街道办审核或执行物业管理模式变更申请表单。
 */
public record ReviewPropertyManagementModeChangeRequest(
        @NotNull PropertyManagementModeChangeDecision decision,
        @Size(max = 1000) String reviewComment,
        @NotNull @PositiveOrZero Integer expectedVersion
) {
    public ReviewPropertyManagementModeChangeCommand toCommand() {
        return new ReviewPropertyManagementModeChangeCommand(decision, reviewComment, expectedVersion);
    }
}
