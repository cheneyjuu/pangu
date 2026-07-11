package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

/** 供应商个人账号激活邀请及授权审计快照。 */
public record SupplierActivationInvitation(
        Long invitationId,
        Long tenantId,
        Long supplierDeptId,
        Long workOrderId,
        String supplierLegalName,
        String contactName,
        String contactPhone,
        String defaultRoleKey,
        String status,
        LocalDateTime expiresAt,
        Long activatedAccountId,
        Long activatedUserId,
        Long invitedByUserId,
        LocalDateTime activatedAt,
        LocalDateTime createTime
) {
}
