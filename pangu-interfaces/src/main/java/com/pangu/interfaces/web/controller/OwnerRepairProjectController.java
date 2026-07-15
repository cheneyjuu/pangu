// 关联业务：向受影响业主本人展示楼栋维修验收任务并提交本人验收结论。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairProjectAcceptanceService;
import com.pangu.application.repair.OwnerRepairProjectDisclosure;
import com.pangu.application.repair.OwnerRepairProjectQueryService;
import com.pangu.application.repair.RepairProjectAttachmentService;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceParty;
import com.pangu.domain.model.repair.RepairProjectExecution.OwnerAcceptanceTask;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.OwnerAcceptanceRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairAttachmentDownloadTicketResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/repair-projects")
@RequiredArgsConstructor
public class OwnerRepairProjectController extends BaseController {

    private final RepairProjectAcceptanceService acceptanceService;
    private final OwnerRepairProjectQueryService queryService;
    private final RepairProjectAttachmentService attachmentService;

    @GetMapping("/by-work-order/{workOrderId}")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerRepairProjectDisclosure> disclosure(
            @PathVariable("workOrderId") Long workOrderId) {
        return success(queryService.findPublishedByWorkOrder(workOrderId).orElse(null));
    }

    @GetMapping("/by-work-order/{workOrderId}/attachments/{attachmentId}/download-ticket")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairAttachmentDownloadTicketResponse> attachmentTicket(
            @PathVariable("workOrderId") Long workOrderId,
            @PathVariable("attachmentId") Long attachmentId) {
        return success(RepairAttachmentDownloadTicketResponse.from(
                attachmentService.createOwnerDownloadTicket(workOrderId, attachmentId)));
    }

    @GetMapping("/acceptance-tasks")
    @PreAuthorize("isAuthenticated()")
    public Result<List<OwnerAcceptanceTask>> tasks() {
        return success(acceptanceService.listOwnerTasks());
    }

    @GetMapping("/{projectId}/acceptance")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerAcceptanceTask> task(@PathVariable("projectId") Long projectId) {
        return success(acceptanceService.ownerTask(projectId));
    }

    @PostMapping("/{projectId}/acceptance")
    @PreAuthorize("isAuthenticated()")
    public Result<AcceptanceParty> submit(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody OwnerAcceptanceRequest request) {
        return success("受影响业主验收意见已提交",
                acceptanceService.recordOwner(projectId, request.toCommand()));
    }
}
