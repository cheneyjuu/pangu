// 关联业务：按已经盖章固化的授权快照校验施工单位确认，不制造固定邀价或报价数量规则。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.policy.RepairSupplierSelectionPolicy;
import org.springframework.stereotype.Component;

@Component
public class DefaultRepairSupplierSelectionPolicy implements RepairSupplierSelectionPolicy {

    @Override
    public Decision evaluate(Input input) {
        if (input == null || input.method() == null || input.evaluationRule() == null) {
            return Decision.reject("施工单位选择授权快照不完整");
        }
        if (!input.selectionEvidencePresent()) {
            return Decision.reject("必须归档评审记录、比价表或定商记录原件");
        }
        if (input.minimumInvitedSupplierCount() != null
                && input.invitationCount() < input.minimumInvitedSupplierCount()) {
            return Decision.reject("有效授权要求的最低邀请供应商数量尚未达到");
        }
        if (input.minimumValidQuoteCount() != null
                && input.validQuoteCount() < input.minimumValidQuoteCount()) {
            return Decision.reject("有效授权要求的最低有效报价数量尚未达到");
        }
        if (input.method() == RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION) {
            if (input.evaluationRule() != SupplierSelectionEvaluationRule.LOWEST_COMPLIANT_QUOTE
                    && input.evaluationRule() != SupplierSelectionEvaluationRule.COMPREHENSIVE_EVALUATION) {
                return Decision.reject("竞争性报价必须使用最低合格报价或综合评审规则");
            }
            if (!blank(input.nonCompetitiveSelectionBasis())) {
                return Decision.reject("竞争性报价授权不得携带非竞争定商依据");
            }
            if (input.evaluationRule() == SupplierSelectionEvaluationRule.COMPREHENSIVE_EVALUATION
                    && blank(input.selectionRationale())) {
                return Decision.reject("综合评审必须填写中选说明");
            }
            return Decision.allow();
        }
        if (input.evaluationRule() != SupplierSelectionEvaluationRule.AUTHORIZED_DIRECT_SELECTION) {
            return Decision.reject("非竞争定商必须使用授权直接选择规则");
        }
        if (blank(input.nonCompetitiveSelectionBasis())) {
            return Decision.reject("非竞争定商缺少授权文件中的明确依据");
        }
        if (blank(input.selectionRationale())) {
            return Decision.reject("授权直接选择必须填写具体中选说明");
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
