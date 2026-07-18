// 关联业务：校验获授权业委会成员登记楼栋维修正式盖章文件的输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.SealBuildingRepairCommand;
import com.pangu.application.repair.command.SealBuildingRepairCommand.SupplierSelectionAuthorization;
import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SealBuildingRepairRequest(
        @NotNull @Min(0) Integer expectedProcessVersion,
        @NotNull Long sealedAttachmentId,
        @Size(max = 500) String remark,
        @NotNull @Valid SupplierSelectionAuthorizationRequest supplierSelectionAuthorization
) {
    public SealBuildingRepairCommand toCommand() {
        return new SealBuildingRepairCommand(
                expectedProcessVersion, sealedAttachmentId, remark,
                supplierSelectionAuthorization.toCommand());
    }

    /** 与盖章件一致的选择规则快照；空值只表示原件未记载数量门槛，不能由前端补默认值。 */
    public record SupplierSelectionAuthorizationRequest(
            @NotNull RepairSupplierSelectionMethod selectionMethod,
            @NotNull SupplierSelectionEvaluationRule evaluationRule,
            @Min(1) @Max(1000) Integer minimumInvitedSupplierCount,
            @Min(1) @Max(1000) Integer minimumValidQuoteCount,
            @Size(max = 1000) String nonCompetitiveSelectionBasis
    ) {
        @AssertTrue(message = "最低有效报价数不得大于最低邀价数")
        public boolean hasConsistentMinimumCounts() {
            return minimumInvitedSupplierCount == null || minimumValidQuoteCount == null
                    || minimumValidQuoteCount <= minimumInvitedSupplierCount;
        }

        public SupplierSelectionAuthorization toCommand() {
            return new SupplierSelectionAuthorization(
                    selectionMethod, evaluationRule, minimumInvitedSupplierCount,
                    minimumValidQuoteCount, nonCompetitiveSelectionBasis);
        }
    }
}
