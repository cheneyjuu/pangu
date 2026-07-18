// 关联业务：接收维修工程筹备草稿的预算、维修点位和原始附件引用；不把资金、决定、验收、付款或定商写成未核验事实。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import com.pangu.domain.model.repair.RepairProject.WorkPointCauseStatus;
import com.pangu.domain.model.repair.RepairProject.WorkPointLocationType;

import java.math.BigDecimal;
import java.util.List;

public record RepairPlanDraftCommand(
        String planDescription,
        BigDecimal budgetTotal,
        List<WorkPointDraft> workPoints,
        List<AttachmentReference> attachments
) {
    public RepairPlanDraftCommand {
        workPoints = workPoints == null ? List.of() : List.copyOf(workPoints);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /** 维修点位只记录可观察事实和拟定措施，估算金额不承担报价行职责。 */
    public record WorkPointDraft(
            String businessName,
            Long buildingId,
            String unitName,
            WorkPointLocationType locationType,
            Long referenceRoomId,
            String commonAreaName,
            String spaceName,
            String orientation,
            String component,
            String specificPart,
            String symptom,
            WorkPointCauseStatus causeStatus,
            String causeBasis,
            String proposedMeasure,
            String technicalRequirements,
            BigDecimal quantity,
            String unit,
            BigDecimal preliminaryEstimatedAmount,
            String estimateSource,
            List<Long> linkedWorkOrderIds
    ) {
        public WorkPointDraft {
            linkedWorkOrderIds = linkedWorkOrderIds == null ? List.of() : List.copyOf(linkedWorkOrderIds);
        }
    }

    public record AttachmentReference(
            Long attachmentId,
            AttachmentPurpose purpose
    ) {
    }
}
