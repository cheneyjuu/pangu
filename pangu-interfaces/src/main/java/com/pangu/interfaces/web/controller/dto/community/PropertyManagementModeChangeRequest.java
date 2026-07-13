// 关联业务：校验业委会主任创建或补正物业管理模式变更申请的管理端表单。
package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.command.UpsertPropertyManagementModeChangeCommand;
import com.pangu.domain.model.community.PropertyManagementMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 物业管理模式变更申请表单。
 */
public record PropertyManagementModeChangeRequest(
        @NotNull PropertyManagementMode requestedPropertyMode,
        @NotBlank @Size(min = 2, max = 128) String ownersAssemblyResolutionReference,
        @NotBlank @Size(min = 5, max = 1000) String changeReason,
        @PositiveOrZero Integer expectedVersion
) {
    public UpsertPropertyManagementModeChangeCommand toCommand() {
        return new UpsertPropertyManagementModeChangeCommand(
                requestedPropertyMode, ownersAssemblyResolutionReference, changeReason, expectedVersion);
    }
}
