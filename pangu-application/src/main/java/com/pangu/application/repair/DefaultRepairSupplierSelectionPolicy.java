// 关联业务：校验授权提案及其生效快照中的施工单位选择条件，不制造固定邀价或报价数量规则。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.policy.RepairSupplierSelectionPolicy;
import org.springframework.stereotype.Component;

@Component
public class DefaultRepairSupplierSelectionPolicy implements RepairSupplierSelectionPolicy {

    @Override
    public Decision validateTerms(Terms terms) {
        if (terms == null || terms.method() == null || terms.evaluationRule() == null) {
            return Decision.reject("请选择施工单位确定方式和报价比较方式");
        }
        Integer invited = terms.minimumInvitedSupplierCount();
        Integer quotes = terms.minimumValidQuoteCount();
        if ((invited != null && invited <= 0) || (quotes != null && quotes <= 0)) {
            return Decision.reject("最低邀请单位数和最低有效报价数必须大于 0");
        }
        if (invited != null && quotes != null && quotes > invited) {
            return Decision.reject("最低有效报价数不能大于最低邀请单位数");
        }
        if (terms.method() == RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION) {
            if (terms.evaluationRule() != SupplierSelectionEvaluationRule.LOWEST_COMPLIANT_QUOTE
                    && terms.evaluationRule() != SupplierSelectionEvaluationRule.COMPREHENSIVE_EVALUATION) {
                return Decision.reject("多家询价比选必须选择最低合格报价或综合比较");
            }
            if (!blank(terms.nonCompetitiveSelectionBasis())) {
                return Decision.reject("采用多家询价比选时不应填写直接委托依据");
            }
            return Decision.allow();
        }
        if (terms.evaluationRule() != SupplierSelectionEvaluationRule.AUTHORIZED_DIRECT_SELECTION) {
            return Decision.reject("非询价方式应按已经批准的书面依据选择施工单位");
        }
        if (blank(terms.nonCompetitiveSelectionBasis())) {
            return Decision.reject("采用非询价方式时必须填写适用依据");
        }
        if (invited != null || quotes != null) {
            return Decision.reject("采用非询价方式时不应填写询价数量要求");
        }
        return Decision.allow();
    }

    @Override
    public Decision evaluate(Input input) {
        if (input == null) {
            return Decision.reject("施工单位选择条件不完整");
        }
        Decision termsDecision = validateTerms(new Terms(
                input.method(), input.evaluationRule(), input.minimumInvitedSupplierCount(),
                input.minimumValidQuoteCount(), input.nonCompetitiveSelectionBasis()));
        if (!termsDecision.allowed()) {
            return termsDecision;
        }
        if (!input.selectionEvidencePresent()) {
            return Decision.reject("请上传比价表、评审记录或施工单位选择记录");
        }
        if (input.minimumInvitedSupplierCount() != null
                && input.invitationCount() < input.minimumInvitedSupplierCount()) {
            return Decision.reject("实施方案要求邀请的施工单位数量尚未达到");
        }
        if (input.minimumValidQuoteCount() != null
                && input.validQuoteCount() < input.minimumValidQuoteCount()) {
            return Decision.reject("实施方案要求取得的报价数量尚未达到");
        }
        if (input.method() == RepairSupplierSelectionMethod.COMPETITIVE_QUOTATION) {
            if (input.evaluationRule() == SupplierSelectionEvaluationRule.COMPREHENSIVE_EVALUATION
                    && blank(input.selectionRationale())) {
                return Decision.reject("综合评审必须填写施工单位选择说明");
            }
            return Decision.allow();
        }
        if (blank(input.selectionRationale())) {
            return Decision.reject("直接选择施工单位时必须填写选择说明");
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
