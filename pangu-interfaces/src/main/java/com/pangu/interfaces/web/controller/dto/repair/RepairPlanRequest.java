// 关联业务：校验维修工程筹备草稿的预算、维修点位与原始附件引用；资金、决定、验收、付款和定商不能由建项表单声明。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.RepairPlanDraftCommand;
import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import com.pangu.domain.model.repair.RepairProject.WorkPointCauseStatus;
import com.pangu.domain.model.repair.RepairProject.WorkPointLocationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RepairPlanRequest(
        @NotBlank @Size(max = 16000) String planDescription,
        @NotNull @DecimalMin(value = "0.01") BigDecimal budgetTotal,
        @NotEmpty List<@Valid WorkPointRequest> workPoints,
        List<@Valid AttachmentReferenceRequest> attachments
) {

    public RepairPlanDraftCommand toCommand() {
        return new RepairPlanDraftCommand(
                planDescription, budgetTotal,
                workPoints.stream().map(WorkPointRequest::toCommand).toList(),
                attachments == null
                        ? List.of()
                        : attachments.stream().map(AttachmentReferenceRequest::toCommand).toList());
    }

    /** 录入对象是可定位的维修点位，而非报价数量和单价的承载行。 */
    public record WorkPointRequest(
            @NotBlank @Size(max = 160) String businessName,
            Long buildingId,
            @Size(max = 64) String unitName,
            @NotNull WorkPointLocationType locationType,
            Long referenceRoomId,
            @Size(max = 160) String commonAreaName,
            @Size(max = 160) String spaceName,
            @Size(max = 80) String orientation,
            @Size(max = 160) String component,
            @Size(max = 240) String specificPart,
            @NotBlank @Size(max = 4000) String symptom,
            @NotNull WorkPointCauseStatus causeStatus,
            @Size(max = 4000) String causeBasis,
            @NotBlank @Size(max = 4000) String proposedMeasure,
            @Size(max = 4000) String technicalRequirements,
            @DecimalMin(value = "0.001") BigDecimal quantity,
            @Size(max = 32) String unit,
            @DecimalMin("0.00") BigDecimal preliminaryEstimatedAmount,
            @Size(max = 500) String estimateSource,
            List<@NotNull Long> linkedWorkOrderIds
    ) {
        RepairPlanDraftCommand.WorkPointDraft toCommand() {
            return new RepairPlanDraftCommand.WorkPointDraft(
                    businessName, buildingId, unitName, locationType, referenceRoomId, commonAreaName,
                    spaceName, orientation, component, specificPart, symptom, causeStatus, causeBasis,
                    proposedMeasure, technicalRequirements, quantity, unit, preliminaryEstimatedAmount, estimateSource,
                    linkedWorkOrderIds == null ? List.of() : linkedWorkOrderIds);
        }
    }

    public record AttachmentReferenceRequest(
            @NotNull Long attachmentId,
            @NotNull AttachmentPurpose purpose
    ) {
        RepairPlanDraftCommand.AttachmentReference toCommand() {
            return new RepairPlanDraftCommand.AttachmentReference(attachmentId, purpose);
        }
    }
}
