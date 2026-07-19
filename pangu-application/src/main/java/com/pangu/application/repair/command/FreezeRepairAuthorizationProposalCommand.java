// 关联业务：提交共有维修授权提案时，冻结项目版本和经相关业主审议的施工单位选择条件。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;

public record FreezeRepairAuthorizationProposalCommand(
        Integer expectedProjectVersion,
        RepairSupplierSelectionMethod supplierSelectionMethod,
        SupplierSelectionEvaluationRule supplierEvaluationRule,
        Integer minimumInvitedSupplierCount,
        Integer minimumValidQuoteCount,
        String nonCompetitiveSelectionBasis
) {
}
