package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecommendRepairSupplierRequest(
        @NotNull Long quoteId,
        @Size(max = 40) String selectionMethod,
        @Size(max = 1000) String recommendationReason,
        @Size(max = 1000) String insufficientQuoteReason,
        Long frameworkRelationId,
        @Size(max = 500) String remark
) {
}
