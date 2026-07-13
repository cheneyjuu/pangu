// 关联业务：承载业委会主任创建或补正物业管理模式变更申请的输入。
package com.pangu.application.community.command;

import com.pangu.domain.model.community.PropertyManagementMode;

/**
 * 创建或修改物业管理模式变更申请命令。
 */
public record UpsertPropertyManagementModeChangeCommand(
        PropertyManagementMode requestedPropertyMode,
        String ownersAssemblyResolutionReference,
        String changeReason,
        Integer expectedVersion
) {
}
