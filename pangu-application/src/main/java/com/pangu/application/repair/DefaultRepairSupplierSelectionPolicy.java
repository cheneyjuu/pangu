// 关联业务：按现行维修业务规则校验竞争性询价、框架供应商和直接定商依据。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.policy.RepairSupplierSelectionPolicy;
import org.springframework.stereotype.Component;

@Component
public class DefaultRepairSupplierSelectionPolicy implements RepairSupplierSelectionPolicy {

    @Override
    public Decision evaluate(Input input) {
        if (input == null || input.method() == null) {
            return Decision.reject("施工单位选择方式必填");
        }
        if (input.method() == RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION) {
            if (input.invitationCount() < 3) {
                return Decision.reject("竞争性比价必须先邀请至少 3 家供应商");
            }
            if (input.quoteCount() < 3 && blank(input.insufficientQuoteReason())) {
                return Decision.reject("有效报价不足 3 家时必须说明继续推荐的理由");
            }
            return Decision.allow();
        }
        if (blank(input.recommendationReason())) {
            return Decision.reject("跳过比价必须填写选择依据");
        }
        if (input.method() == RepairSupplierSelectionMethod.FRAMEWORK_SUPPLIER
                && !input.frameworkRelationValid()) {
            return Decision.reject("框架供应商必须关联当前小区有效的长期合作关系");
        }
        return Decision.allow();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
