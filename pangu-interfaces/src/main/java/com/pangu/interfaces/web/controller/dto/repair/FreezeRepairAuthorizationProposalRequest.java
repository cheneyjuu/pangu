// 关联业务：接收物业提交给相关业主表决的实施方案版本和施工单位选择条件。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.FreezeRepairAuthorizationProposalCommand;
import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FreezeRepairAuthorizationProposalRequest(
        @NotNull @Min(0) Integer expectedProjectVersion,
        @NotNull RepairSupplierSelectionMethod supplierSelectionMethod,
        @NotNull SupplierSelectionEvaluationRule supplierEvaluationRule,
        @Min(1) Integer minimumInvitedSupplierCount,
        @Min(1) Integer minimumValidQuoteCount,
        String nonCompetitiveSelectionBasis
) {

    public FreezeRepairAuthorizationProposalCommand toCommand() {
        return new FreezeRepairAuthorizationProposalCommand(
                expectedProjectVersion, supplierSelectionMethod, supplierEvaluationRule,
                minimumInvitedSupplierCount, minimumValidQuoteCount, nonCompetitiveSelectionBasis);
    }
}
