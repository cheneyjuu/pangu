// 关联业务：校验维修工程结构化报价覆盖范围、逐行计价和报价原件一致性确认。
package com.pangu.domain.policy;

import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLine;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLineDraft;

import java.math.BigDecimal;
import java.util.List;

@FunctionalInterface
public interface RepairProjectQuotePricingPolicy {

    Decision evaluate(Input input);

    record ScopeItem(Long projectItemId, String itemNo) {
    }

    record Input(
            List<ScopeItem> scopeItems,
            List<QuoteLineDraft> quoteLines,
            BigDecimal declaredAmount,
            BigDecimal taxRate,
            Integer constructionPeriodDays,
            Integer warrantyDays,
            boolean originalAmountConfirmed
    ) {
        public Input {
            scopeItems = scopeItems == null ? List.of() : List.copyOf(scopeItems);
            quoteLines = quoteLines == null ? List.of() : List.copyOf(quoteLines);
        }
    }

    record Decision(
            boolean allowed,
            String rejectionReason,
            BigDecimal amountExcludingTax,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal calculatedAmount,
            List<QuoteLine> normalizedLines
    ) {
        public Decision {
            normalizedLines = normalizedLines == null ? List.of() : List.copyOf(normalizedLines);
        }

        public static Decision allow(
                BigDecimal amountExcludingTax,
                BigDecimal taxRate,
                BigDecimal taxAmount,
                BigDecimal calculatedAmount,
                List<QuoteLine> normalizedLines) {
            return new Decision(
                    true, null, amountExcludingTax, taxRate, taxAmount,
                    calculatedAmount, normalizedLines);
        }

        public static Decision reject(String reason) {
            return new Decision(false, reason, null, null, null, null, List.of());
        }
    }
}
