// 关联业务：向受影响业主本人展示楼栋维修验收任务并提交本人验收结论。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairProjectAcceptanceService;
import com.pangu.application.repair.OwnerRepairProjectDisclosure;
import com.pangu.application.repair.OwnerRepairProjectQueryService;
import com.pangu.application.repair.RepairProjectAttachmentService;
import com.pangu.application.repair.OwnerBuildingRepairDecisionService;
import com.pangu.application.repair.RepairWorkOrderApplicationException;
import com.pangu.application.repair.command.UploadRepairProjectAttachmentCommand;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceParty;
import com.pangu.domain.model.repair.RepairProjectExecution.OwnerAcceptanceTask;
import com.pangu.domain.model.repair.RepairProjectGovernance.OwnerDecisionTask;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectExecutionRequests.OwnerAcceptanceRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairAttachmentDownloadTicketResponse;
import com.pangu.interfaces.web.controller.dto.repair.SubmitOwnerRepairProjectDecisionVoteRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/me/repair-projects")
@RequiredArgsConstructor
public class OwnerRepairProjectController extends BaseController {

    private final RepairProjectAcceptanceService acceptanceService;
    private final OwnerRepairProjectQueryService queryService;
    private final RepairProjectAttachmentService attachmentService;
    private final OwnerBuildingRepairDecisionService decisionService;

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

    @GetMapping("/decisions")
    @PreAuthorize("isAuthenticated()")
    public Result<List<OwnerDecisionTask>> decisionTasks() {
        return success(decisionService.listTasks());
    }

    @GetMapping("/decisions/{decisionId}")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerDecisionTask> decisionTask(@PathVariable("decisionId") Long decisionId) {
        return success(decisionService.task(decisionId));
    }

    @PostMapping("/decisions/{decisionId}/votes")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerDecisionTask> submitDecisionVote(
            @PathVariable("decisionId") Long decisionId,
            @Valid @RequestBody SubmitOwnerRepairProjectDecisionVoteRequest request) {
        return success("楼栋维修在线表决已提交", decisionService.submit(decisionId, request.toCommand()));
    }

    @GetMapping("/decisions/{decisionId}/disclosure")
    @PreAuthorize("isAuthenticated()")
    public Result<OwnerRepairProjectDisclosure> decisionDisclosure(
            @PathVariable("decisionId") Long decisionId) {
        return success(queryService.findPublishedByDecision(decisionId).orElse(null));
    }

    @GetMapping("/decisions/{decisionId}/attachments/{attachmentId}/download-ticket")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairAttachmentDownloadTicketResponse> decisionAttachmentTicket(
            @PathVariable("decisionId") Long decisionId,
            @PathVariable("attachmentId") Long attachmentId) {
        return success(RepairAttachmentDownloadTicketResponse.from(
                attachmentService.createOwnerDecisionDownloadTicket(decisionId, attachmentId)));
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

    @PostMapping(value = "/{projectId}/acceptance/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Result<Attachment> uploadAcceptanceEvidence(
            @PathVariable("projectId") Long projectId,
            @RequestPart("file") MultipartFile file) {
        try {
            return success("验收证据已上传", attachmentService.uploadOwnerAcceptanceEvidence(
                    projectId, new UploadRepairProjectAttachmentCommand(
                            file.getOriginalFilename(), file.getContentType(), file.getBytes())));
        } catch (IOException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID, "读取验收证据失败", ex);
        }
    }
}
