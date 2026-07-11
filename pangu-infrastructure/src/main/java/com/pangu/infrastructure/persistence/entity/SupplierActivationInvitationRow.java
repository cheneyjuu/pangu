package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierActivationInvitationRow {
    private Long invitationId;
    private Long tenantId;
    private Long supplierDeptId;
    private Long workOrderId;
    private String supplierLegalName;
    private String contactName;
    private String contactPhone;
    private String invitationTokenHash;
    private String defaultRoleKey;
    private String status;
    private LocalDateTime expiresAt;
    private Long activatedAccountId;
    private Long activatedUserId;
    private Long invitedByUserId;
    private LocalDateTime activatedAt;
    private LocalDateTime createTime;
}
