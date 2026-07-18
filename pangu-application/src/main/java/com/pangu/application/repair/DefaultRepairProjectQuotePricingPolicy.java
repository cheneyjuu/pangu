// 关联业务：按维修点位校验报价引用并计算报价头税额，允许运输、清运等不直接对应点位的报价行。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLine;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLineDraft;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLineType;
import com.pangu.domain.policy.RepairProjectQuotePricingPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultRepairProjectQuotePricingPolicy implements RepairProjectQuotePricingPolicy {

    private static final int MAX_QUOTE_LINES = 200;
    private static final int MAX_PERIOD_DAYS = 3650;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999.999");
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");

    @Override
    public Decision evaluate(Input input) {
        try {
            return evaluateRequired(input);
        } catch (Rejected rejected) {
            return Decision.reject(rejected.getMessage());
        }
    }

    private Decision evaluateRequired(Input input) {
        if (input == null || input.scopeWorkPoints().isEmpty()) {
            reject("当前实施方案没有可报价的维修点位");
        }
        if (input.quoteLines().isEmpty()) {
            reject("请填写报价明细");
        }
        if (input.quoteLines().size() > MAX_QUOTE_LINES) {
            reject("单个报价版本最多包含 200 条报价明细");
        }
        if (input.constructionPeriodDays() == null || input.constructionPeriodDays() <= 0
                || input.constructionPeriodDays() > MAX_PERIOD_DAYS) {
            reject("施工工期必须为 1 至 3650 天");
        }
        if (input.warrantyDays() == null || input.warrantyDays() < 0
                || input.warrantyDays() > MAX_PERIOD_DAYS) {
            reject("质保期必须为 0 至 3650 天");
        }
        if (!input.originalAmountConfirmed()) {
            reject("必须确认线上报价明细总额与报价原件一致");
        }
        BigDecimal taxRate = range(
                input.taxRate(), BigDecimal.ZERO, new BigDecimal("100"), 3, "报价税率");

        Map<Long, ScopeWorkPoint> scopeById = new LinkedHashMap<>();
        for (ScopeWorkPoint scopeWorkPoint : input.scopeWorkPoints()) {
            if (scopeWorkPoint == null || scopeWorkPoint.workPointId() == null) {
                reject("实施方案维修点位无效");
            }
            scopeById.put(scopeWorkPoint.workPointId(), scopeWorkPoint);
        }

        List<QuoteLine> normalized = new ArrayList<>();
        BigDecimal amountExcludingTax = BigDecimal.ZERO.setScale(2);
        for (QuoteLineDraft draft : input.quoteLines()) {
            if (draft == null) {
                reject("报价明细不能为空");
            }
            ScopeWorkPoint scopeWorkPoint = draft.workPointId() == null
                    ? null
                    : scopeById.get(draft.workPointId());
            if (draft.workPointId() != null && scopeWorkPoint == null) {
                reject("报价明细包含当前实施方案以外的维修点位");
            }
            String itemName = requiredText(draft.itemName(), 200, "报价项目名称");
            QuoteLineType lineType = draft.lineType();
            if (lineType == null) {
                reject("明细类别必填");
            }
            String workDescription = optionalText(draft.workDescription(), 1000, "项目特征或工作内容");
            String specificationModel = optionalText(draft.specificationModel(), 200, "规格型号");
            String brand = optionalText(draft.brand(), 120, "品牌");
            String procurementMethod = optionalText(draft.procurementMethod(), 120, "采购方式");
            String unit = requiredText(draft.unit(), 40, "单位");
            String remark = optionalText(draft.remark(), 500, "报价备注");
            BigDecimal quantity = positive(draft.quantity(), 3, "数量");
            BigDecimal unitPrice = nonNegative(draft.unitPriceExcludingTax(), 2, "不含税单价")
                    .setScale(2, RoundingMode.UNNECESSARY);
            if (quantity.compareTo(MAX_QUANTITY) > 0 || unitPrice.compareTo(MAX_MONEY) > 0) {
                reject("报价明细数量或不含税单价超出系统可记录范围");
            }
            BigDecimal lineAmount = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            if (lineAmount.compareTo(MAX_MONEY) > 0) {
                reject("单条报价明细金额超出系统可记录范围");
            }
            int lineNo = normalized.size() + 1;
            normalized.add(new QuoteLine(
                    null, null, draft.workPointId(),
                    scopeWorkPoint == null ? null : scopeWorkPoint.businessName(), lineNo,
                    itemName, lineType, workDescription, specificationModel, brand,
                    procurementMethod, quantity, unit, unitPrice, lineAmount, remark));
            amountExcludingTax = amountExcludingTax.add(lineAmount);
            if (amountExcludingTax.compareTo(MAX_MONEY) > 0) {
                reject("报价明细不含税合计超出系统可记录范围");
            }
        }

        if (amountExcludingTax.signum() <= 0) {
            reject("报价明细不含税总额必须大于 0");
        }
        BigDecimal taxAmount = amountExcludingTax.multiply(taxRate)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal calculatedAmount = amountExcludingTax.add(taxAmount);
        if (calculatedAmount.compareTo(MAX_MONEY) > 0) {
            reject("报价含税总额超出系统可记录范围");
        }
        if (input.declaredAmount() == null) {
            reject("含税报价总额必填");
        }
        BigDecimal declaredAmount = input.declaredAmount().setScale(2, RoundingMode.HALF_UP);
        if (declaredAmount.compareTo(calculatedAmount) != 0) {
            reject("含税报价总额必须与报价明细合计一致");
        }
        return Decision.allow(amountExcludingTax, taxRate, taxAmount, calculatedAmount, normalized);
    }

    private String requiredText(String value, int maxLength, String field) {
        String normalized = optionalText(value, maxLength, field);
        if (normalized == null) {
            reject(field + "必填");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            reject(field + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private BigDecimal positive(BigDecimal value, int maximumScale, String field) {
        BigDecimal normalized = scaled(value, maximumScale, field);
        if (normalized.signum() <= 0) {
            reject(field + "必须大于 0");
        }
        return normalized;
    }

    private BigDecimal nonNegative(BigDecimal value, int maximumScale, String field) {
        BigDecimal normalized = scaled(value, maximumScale, field);
        if (normalized.signum() < 0) {
            reject(field + "不能小于 0");
        }
        return normalized;
    }

    private BigDecimal range(
            BigDecimal value,
            BigDecimal minimum,
            BigDecimal maximum,
            int maximumScale,
            String field) {
        BigDecimal normalized = scaled(value, maximumScale, field);
        if (normalized.compareTo(minimum) < 0 || normalized.compareTo(maximum) > 0) {
            reject(field + "必须在 " + minimum + " 至 " + maximum + " 之间");
        }
        return normalized;
    }

    private BigDecimal scaled(BigDecimal value, int maximumScale, String field) {
        if (value == null) {
            reject(field + "必填");
        }
        int effectiveScale = Math.max(0, value.stripTrailingZeros().scale());
        if (effectiveScale > maximumScale) {
            reject(field + "最多保留 " + maximumScale + " 位小数");
        }
        return value.setScale(effectiveScale, RoundingMode.UNNECESSARY);
    }

    private void reject(String reason) {
        throw new Rejected(reason);
    }

    private static final class Rejected extends RuntimeException {
        private Rejected(String message) {
            super(message);
        }
    }
}
