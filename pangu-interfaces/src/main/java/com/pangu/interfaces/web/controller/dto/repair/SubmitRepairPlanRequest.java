// 关联业务：承载维修实施方案及楼栋验收人数门槛，门槛不得由平台补默认值。
package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record SubmitRepairPlanRequest(
        @DecimalMin(value = "0.01") BigDecimal planBudget,
        @DecimalMin(value = "0.01") BigDecimal publicCeilingPrice,
        @Size(max = 64) String fundSource,
        @Valid AcceptancePolicy acceptancePolicy,
        @Size(max = 500) String remark
) {
    public record AcceptancePolicy(
            @NotEmpty List<@Valid AffectedOwner> affectedOwners,
            @NotNull @Min(1) Integer minimumAffectedOwnerParticipants,
            @NotNull @Min(1) Integer minimumAffectedOwnerApprovals
    ) {
    }

    public record AffectedOwner(
            @NotNull Long roomId,
            @NotNull Long ownerUid,
            @Size(max = 500) String affectedReason
    ) {
    }
}
