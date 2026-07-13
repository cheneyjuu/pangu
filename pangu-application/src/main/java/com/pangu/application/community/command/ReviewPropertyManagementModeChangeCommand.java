// 关联业务：承载街道办对物业管理模式变更申请的退回、驳回或执行输入。
package com.pangu.application.community.command;

import com.pangu.domain.model.community.PropertyManagementModeChangeDecision;

/**
 * 审核或执行物业管理模式变更申请命令。
 */
public record ReviewPropertyManagementModeChangeCommand(
        PropertyManagementModeChangeDecision decision,
        String reviewComment,
        Integer expectedVersion
) {
}
