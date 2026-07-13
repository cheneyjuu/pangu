// 关联业务：保存小区物业管理模式变更的申请事实、决议依据、审核状态和执行留痕。
package com.pangu.domain.model.community;

import java.time.Instant;

/**
 * 小区物业管理模式变更申请。
 *
 * <p>同一小区的生效模式是单值事实；本申请只表达由当前模式向目标模式的受控变更，
 * 不承担前端偏好设置或临时展示开关的职责。
 */
public record PropertyManagementModeChangeRequest(
        Long requestId,
        Long tenantId,
        PropertyManagementMode currentPropertyMode,
        PropertyManagementMode requestedPropertyMode,
        String ownersAssemblyResolutionReference,
        String changeReason,
        PropertyManagementModeChangeStatus status,
        Long applicantAccountId,
        Long applicantUserId,
        Long applicantDeptId,
        Instant submittedAt,
        Long reviewerAccountId,
        Long reviewerUserId,
        Long reviewerDeptId,
        String reviewComment,
        Instant reviewedAt,
        Instant executedAt,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
