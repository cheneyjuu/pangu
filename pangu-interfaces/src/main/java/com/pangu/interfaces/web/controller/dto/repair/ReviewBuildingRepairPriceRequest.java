// 关联业务：校验业委会对楼栋维修方案审价结论和审价附件的输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.ReviewBuildingRepairPriceCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReviewBuildingRepairPriceRequest(
        @NotNull @Min(0) Integer expectedProcessVersion,
        @NotBlank @Size(max = 32) String reviewMode,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal reviewedAmount,
        Long reportAttachmentId,
        @NotBlank @Size(max = 24) String conclusion,
        @Size(max = 1000) String opinion
) {
    public ReviewBuildingRepairPriceCommand toCommand() {
        return new ReviewBuildingRepairPriceCommand(
                expectedProcessVersion, reviewMode, reviewedAmount,
                reportAttachmentId, conclusion, opinion);
    }
}
