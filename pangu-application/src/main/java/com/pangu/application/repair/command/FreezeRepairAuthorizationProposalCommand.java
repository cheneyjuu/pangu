// 关联业务：提交共有维修授权提案时，冻结项目版本和经相关业主审议的施工单位选择条件。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairAcceptancePartyRole;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerPassRule;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRequirement;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record FreezeRepairAuthorizationProposalCommand(
        Integer expectedProjectVersion,
        RepairSupplierSelectionMethod supplierSelectionMethod,
        SupplierSelectionEvaluationRule supplierEvaluationRule,
        Integer minimumInvitedSupplierCount,
        Integer minimumValidQuoteCount,
        String nonCompetitiveSelectionBasis,
        String acceptanceMethod,
        List<AcceptanceRequirement> acceptanceRequirements,
        Set<RepairAcceptancePartyRole> acceptanceFinalizerRoles,
        List<Long> acceptanceBasisAttachmentIds,
        String acceptanceBasisSummary,
        String affectedOwnerScopeDescription,
        Integer minimumAffectedOwnerAcceptors,
        AffectedOwnerPassRule affectedOwnerPassRule,
        BigDecimal affectedOwnerApprovalRatio
) {
    public FreezeRepairAuthorizationProposalCommand {
        acceptanceRequirements = acceptanceRequirements == null ? List.of() : List.copyOf(acceptanceRequirements);
        acceptanceFinalizerRoles = acceptanceFinalizerRoles == null
                ? Set.of()
                : Set.copyOf(acceptanceFinalizerRoles);
        acceptanceBasisAttachmentIds = acceptanceBasisAttachmentIds == null
                ? List.of()
                : List.copyOf(acceptanceBasisAttachmentIds);
    }
}
