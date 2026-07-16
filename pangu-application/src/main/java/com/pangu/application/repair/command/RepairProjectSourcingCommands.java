// 关联业务：集中表达维修工程项目邀价、报价修订、报价提交和中选供应商命令。
package com.pangu.application.repair.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RepairProjectSourcingCommands {

    private RepairProjectSourcingCommands() {
    }

    public record InviteSuppliers(List<Long> supplierDeptIds, LocalDateTime deadline) {
        public InviteSuppliers {
            supplierDeptIds = supplierDeptIds == null ? List.of() : List.copyOf(supplierDeptIds);
        }
    }

    public record RequestQuoteRevisions(
            List<Long> supplierDeptIds,
            LocalDateTime deadline,
            String revisionReason
    ) {
        public RequestQuoteRevisions {
            supplierDeptIds = supplierDeptIds == null ? List.of() : List.copyOf(supplierDeptIds);
        }
    }

    public record SubmitQuote(
            Long supplierDeptId,
            Long invitationId,
            BigDecimal quoteAmount,
            String quoteSummary,
            Long attachmentId,
            String confirmationStatus,
            String originalSource
    ) {
    }

    public record SelectQuote(
            Long quoteId,
            String recommendationReason,
            String insufficientQuoteReason,
            Long frameworkRelationId
    ) {
    }
}
