// 关联业务：接收物业提交给相关业主表决的实施方案版本和施工单位选择条件。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.FreezeRepairAuthorizationProposalCommand;
import com.pangu.domain.model.repair.RepairAcceptancePartyRole;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerPassRule;
import com.pangu.domain.model.repair.RepairProject.EvidenceRequirement;
import com.pangu.domain.model.repair.RepairProject.EvidenceStage;
import com.pangu.domain.model.repair.RepairProject.SettlementMethod;
import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRequirement;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record FreezeRepairAuthorizationProposalRequest(
        @NotNull @Min(0) Integer expectedProjectVersion,
        @NotNull RepairSupplierSelectionMethod supplierSelectionMethod,
        @NotNull SupplierSelectionEvaluationRule supplierEvaluationRule,
        @Min(1) Integer minimumInvitedSupplierCount,
        @Min(1) Integer minimumValidQuoteCount,
        String nonCompetitiveSelectionBasis,
        @NotBlank @Size(max = 2000) String constructionManagementRequirements,
        @NotEmpty @Size(max = 12) List<@Valid EvidenceRequirementRequest> evidenceRequirements,
        @NotBlank @Size(max = 1000) String safetyRequirements,
        @NotNull SettlementMethod settlementMethod,
        @NotNull LocalDate plannedStartDate,
        @NotNull LocalDate plannedCompletionDate,
        @NotNull @Positive Integer warrantyDays,
        @NotBlank @Size(max = 1000) String acceptanceMethod,
        @NotEmpty @Size(max = 12) List<@Valid AcceptanceRequirementRequest> acceptanceRequirements,
        @NotEmpty Set<@NotNull RepairAcceptancePartyRole> acceptanceFinalizerRoles,
        @NotEmpty List<@NotNull @Positive Long> acceptanceBasisAttachmentIds,
        @NotBlank @Size(max = 1000) String acceptanceBasisSummary,
        @Size(max = 1000) String affectedOwnerScopeDescription,
        @Min(1) Integer minimumAffectedOwnerAcceptors,
        AffectedOwnerPassRule affectedOwnerPassRule,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") BigDecimal affectedOwnerApprovalRatio
) {

    public FreezeRepairAuthorizationProposalCommand toCommand() {
        return new FreezeRepairAuthorizationProposalCommand(
                expectedProjectVersion, supplierSelectionMethod, supplierEvaluationRule,
                minimumInvitedSupplierCount, minimumValidQuoteCount, nonCompetitiveSelectionBasis,
                constructionManagementRequirements,
                evidenceRequirements.stream().map(EvidenceRequirementRequest::toDomain).toList(),
                safetyRequirements, settlementMethod, plannedStartDate, plannedCompletionDate, warrantyDays,
                acceptanceMethod,
                acceptanceRequirements.stream().map(AcceptanceRequirementRequest::toDomain).toList(),
                acceptanceFinalizerRoles, acceptanceBasisAttachmentIds, acceptanceBasisSummary,
                affectedOwnerScopeDescription, minimumAffectedOwnerAcceptors,
                affectedOwnerPassRule, affectedOwnerApprovalRatio);
    }

    public record EvidenceRequirementRequest(
            @NotNull EvidenceStage stage,
            @NotBlank @Size(max = 500) String description,
            boolean required
    ) {
        private EvidenceRequirement toDomain() {
            return new EvidenceRequirement(stage, description, required);
        }
    }

    public record AcceptanceRequirementRequest(
            @NotBlank @Size(max = 64) String requirementCode,
            @NotBlank @Size(max = 160) String businessName,
            @NotEmpty Set<@NotNull RepairAcceptancePartyRole> eligibleRoles,
            @Min(1) int minimumPassingCount,
            boolean evidenceRequired
    ) {
        private AcceptanceRequirement toDomain() {
            return new AcceptanceRequirement(
                    requirementCode, businessName, eligibleRoles, minimumPassingCount, evidenceRequired);
        }
    }
}
