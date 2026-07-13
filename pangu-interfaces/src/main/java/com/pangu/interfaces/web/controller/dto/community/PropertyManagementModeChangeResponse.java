// 关联业务：汇总小区生效物业管理模式、变更申请、材料与审核审计，供业委会和街道办查看。
package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.PropertyManagementModeChangeDetails;
import com.pangu.domain.model.community.PropertyManagementModeChangeAudit;
import com.pangu.domain.model.community.PropertyManagementModeChangeRequest;

import java.time.Instant;
import java.util.List;

/**
 * 物业管理模式变更申请详情响应。
 */
public record PropertyManagementModeChangeResponse(
        String effectivePropertyMode,
        Long requestId,
        Long tenantId,
        String currentPropertyMode,
        String requestedPropertyMode,
        String ownersAssemblyResolutionReference,
        String changeReason,
        String status,
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
        Instant updatedAt,
        List<PropertyManagementModeChangeMaterialResponse> materials,
        List<AuditResponse> audits
) {
    public static PropertyManagementModeChangeResponse from(PropertyManagementModeChangeDetails details) {
        PropertyManagementModeChangeRequest request = details.request();
        return new PropertyManagementModeChangeResponse(
                details.effectivePropertyMode() == null ? null : details.effectivePropertyMode().name(),
                request.requestId(), request.tenantId(),
                request.currentPropertyMode() == null ? null : request.currentPropertyMode().name(),
                request.requestedPropertyMode().name(), request.ownersAssemblyResolutionReference(),
                request.changeReason(), request.status().name(), request.applicantAccountId(), request.applicantUserId(),
                request.applicantDeptId(), request.submittedAt(), request.reviewerAccountId(), request.reviewerUserId(),
                request.reviewerDeptId(), request.reviewComment(), request.reviewedAt(), request.executedAt(),
                request.version(), request.createdAt(), request.updatedAt(),
                details.materials().stream().map(PropertyManagementModeChangeMaterialResponse::from).toList(),
                details.audits().stream().map(AuditResponse::from).toList());
    }

    /**
     * 不可变审核审计记录。
     */
    public record AuditResponse(
            Long auditId,
            Long actorAccountId,
            Long actorUserId,
            Long actorDeptId,
            String eventType,
            String fromStatus,
            String toStatus,
            String payloadJson,
            Instant createdAt
    ) {
        static AuditResponse from(PropertyManagementModeChangeAudit audit) {
            return new AuditResponse(
                    audit.auditId(), audit.actorAccountId(), audit.actorUserId(), audit.actorDeptId(),
                    audit.eventType(), audit.fromStatus() == null ? null : audit.fromStatus().name(),
                    audit.toStatus() == null ? null : audit.toStatus().name(), audit.payloadJson(), audit.createdAt());
        }
    }
}
