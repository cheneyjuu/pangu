// 关联业务：验证维修工程结构化报价必须覆盖锁定工程项、按明细计价并与报价原件总额一致。
package com.pangu.bootstrap.repair;

import com.pangu.application.repair.DefaultRepairProjectQuotePricingPolicy;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLineDraft;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLineType;
import com.pangu.domain.policy.RepairProjectQuotePricingPolicy.Input;
import com.pangu.domain.policy.RepairProjectQuotePricingPolicy.ScopeItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRepairProjectQuotePricingPolicyTest {

    private final DefaultRepairProjectQuotePricingPolicy policy =
            new DefaultRepairProjectQuotePricingPolicy();

    @Test
    void calculatesMultipleCostLinesAndPreservesTheirPlanItemBinding() {
        var decision = policy.evaluate(new Input(
                List.of(new ScopeItem(101L, "ITEM-1"), new ScopeItem(102L, "ITEM-2")),
                List.of(
                        line(101L, "排水泵", QuoteLineType.MATERIAL_EQUIPMENT, "2", "台", "1320"),
                        line(101L, "人工", QuoteLineType.LABOR_SERVICE, "2", "工", "450"),
                        line(102L, "控制柜", QuoteLineType.MATERIAL_EQUIPMENT, "1", "台", "990")),
                new BigDecimal("4937.70"), new BigDecimal("9"), 15, 365, true));

        assertTrue(decision.allowed());
        assertEquals(new BigDecimal("4530.00"), decision.amountExcludingTax());
        assertEquals(new BigDecimal("9"), decision.taxRate());
        assertEquals(new BigDecimal("407.70"), decision.taxAmount());
        assertEquals(new BigDecimal("4937.70"), decision.calculatedAmount());
        assertEquals(3, decision.normalizedLines().size());
        assertEquals("ITEM-1", decision.normalizedLines().get(0).projectItemNo());
        assertEquals(2, decision.normalizedLines().get(1).lineNo());
        assertEquals(new BigDecimal("990.00"), decision.normalizedLines().get(2).amountExcludingTax());
    }

    @Test
    void rejectsMissingScopeItemsAndHeaderAmountMismatch() {
        var missing = policy.evaluate(new Input(
                List.of(new ScopeItem(101L, "ITEM-1"), new ScopeItem(102L, "ITEM-2")),
                List.of(line(101L, "人工", QuoteLineType.LABOR_SERVICE, "1", "项", "900")),
                new BigDecimal("981"), new BigDecimal("9"), 10, 365, true));
        var mismatch = policy.evaluate(new Input(
                List.of(new ScopeItem(101L, "ITEM-1")),
                List.of(line(101L, "人工", QuoteLineType.LABOR_SERVICE, "1", "项", "900")),
                new BigDecimal("982"), new BigDecimal("9"), 10, 365, true));

        assertFalse(missing.allowed());
        assertEquals("报价明细必须覆盖全部实施方案工程项，缺少：ITEM-2", missing.rejectionReason());
        assertFalse(mismatch.allowed());
        assertEquals("含税报价总额必须与报价明细合计一致", mismatch.rejectionReason());
    }

    @Test
    void rejectsQuoteWithoutOriginalAmountConfirmation() {
        var decision = policy.evaluate(new Input(
                List.of(new ScopeItem(101L, "ITEM-1")),
                List.of(line(101L, "人工", QuoteLineType.LABOR_SERVICE, "1", "项", "900")),
                new BigDecimal("981"), new BigDecimal("9"), 10, 365, false));

        assertFalse(decision.allowed());
        assertEquals("必须确认线上报价明细总额与报价原件一致", decision.rejectionReason());
    }

    private QuoteLineDraft line(
            Long projectItemId,
            String itemName,
            QuoteLineType lineType,
            String quantity,
            String unit,
            String unitPrice) {
        return new QuoteLineDraft(
                projectItemId, itemName, lineType, "按报价原件", null, null, null,
                new BigDecimal(quantity), unit, new BigDecimal(unitPrice), "不含税单价");
    }
}
