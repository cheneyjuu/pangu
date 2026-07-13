// 关联业务：携带物业管理模式变更申请提交动作的乐观锁版本号。
package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.command.PropertyManagementModeChangeVersionCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 物业管理模式变更申请提交版本请求。
 */
public record PropertyManagementModeChangeVersionRequest(
        @NotNull @PositiveOrZero Integer expectedVersion
) {
    public PropertyManagementModeChangeVersionCommand toCommand() {
        return new PropertyManagementModeChangeVersionCommand(expectedVersion);
    }
}
