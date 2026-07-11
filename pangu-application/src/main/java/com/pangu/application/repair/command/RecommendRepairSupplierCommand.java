package com.pangu.application.repair.command;

public record RecommendRepairSupplierCommand(
        Long quoteId,
        String selectionMethod,
        String recommendationReason,
        String insufficientQuoteReason,
        Long frameworkRelationId,
        String remark
) {
}
