// 关联业务：由获授权业委会印章保管人登记楼栋维修正式文件用印。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;

public record SealBuildingRepairCommand(
        Integer expectedProcessVersion,
        Long sealedAttachmentId,
        String remark,
        SupplierSelectionAuthorization supplierSelectionAuthorization
) {

    /** 用印时一并固化正式文件已通过的施工单位选择规则，不能由后续物业操作补写。 */
    public record SupplierSelectionAuthorization(
            RepairSupplierSelectionMethod selectionMethod,
            SupplierSelectionEvaluationRule evaluationRule,
            Integer minimumInvitedSupplierCount,
            Integer minimumValidQuoteCount,
            String nonCompetitiveSelectionBasis
    ) {
    }
}
