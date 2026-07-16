// 关联业务：表达维修工程项目方案级邀价、报价版本和中选供应商快照。
package com.pangu.domain.model.repair;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RepairProjectSourcing {

    private RepairProjectSourcing() {
    }

    public enum InvitationStatus {
        PENDING,
        SUBMITTED,
        DECLINED,
        EXPIRED,
        CANCELLED
    }

    public enum InvitationType {
        INITIAL,
        REVISION
    }

    public record Invitation(
            Long invitationId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long supplierDeptId,
            String supplierName,
            Long invitedByUserId,
            LocalDateTime deadline,
            InvitationStatus status,
            Integer invitationRound,
            InvitationType invitationType,
            String revisionReason,
            LocalDateTime sentAt,
            LocalDateTime respondedAt
    ) {
    }

    public record Quote(
            Long quoteId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long supplierDeptId,
            String supplierName,
            BigDecimal quoteAmount,
            String quoteSummary,
            Long attachmentId,
            String attachmentHash,
            Long submittedByUserId,
            String submittedByRoleKey,
            RepairQuoteSubmissionSource submissionSource,
            RepairQuoteConfirmationStatus confirmationStatus,
            String originalSource,
            RepairSupplierQuoteStatus quoteStatus,
            Integer revisionNo,
            Long supersededByQuoteId,
            LocalDateTime createTime
    ) {
    }

    public record Selection(
            Long selectionId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long quoteId,
            Long supplierDeptId,
            String supplierName,
            BigDecimal quoteAmount,
            RepairSupplierSelectionMethod selectionMethod,
            String recommendationReason,
            String insufficientQuoteReason,
            Long frameworkRelationId,
            Long recommendedByUserId,
            LocalDateTime createTime
    ) {
    }

    public record Details(
            Long projectId,
            Long planId,
            RepairSupplierSelectionMethod selectionMethod,
            List<Invitation> invitations,
            List<Quote> quotes,
            Selection selection
    ) {
        public Details {
            invitations = invitations == null ? List.of() : List.copyOf(invitations);
            quotes = quotes == null ? List.of() : List.copyOf(quotes);
        }
    }
}
