package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairQuoteInvitationRow {
    private Long quoteInvitationId;
    private Long workOrderId;
    private Long supplierDeptId;
    private String supplierName;
    private String status;
    private Integer invitationRound;
    private String invitationType;
    private String revisionReason;
    private LocalDateTime deadline;
    private LocalDateTime sentAt;
}
