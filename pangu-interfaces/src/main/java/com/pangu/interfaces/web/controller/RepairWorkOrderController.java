// 关联业务：暴露维修工单从登记、勘验、表决、报审、盖章到验收的后台接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairWorkOrderApplicationException;
import com.pangu.application.repair.RepairAttachmentService;
import com.pangu.application.repair.RepairWorkOrderService;
import com.pangu.application.repair.command.AssignRepairCommand;
import com.pangu.application.repair.command.UploadRepairAttachmentCommand;
import com.pangu.application.repair.command.CompleteRepairContractCommand;
import com.pangu.application.repair.command.CompleteRepairLocalDecisionCommand;
import com.pangu.application.repair.command.CorrectRepairLocationCommand;
import com.pangu.application.repair.command.CreateRepairContractCommand;
import com.pangu.application.repair.command.CreateRepairPaymentRequestCommand;
import com.pangu.application.repair.command.CreatePrivateRepairCommand;
import com.pangu.application.repair.command.CreatePublicRepairCommand;
import com.pangu.application.repair.command.EvaluateRepairCommand;
import com.pangu.application.repair.command.RecommendRepairSupplierCommand;
import com.pangu.application.repair.command.RegisterSupplierOrganizationCommand;
import com.pangu.application.repair.command.InviteRepairSuppliersCommand;
import com.pangu.application.repair.command.RecordRepairAcceptanceCommand;
import com.pangu.application.repair.command.RepairActionCommand;
import com.pangu.application.repair.command.SealRepairGovernanceCommand;
import com.pangu.application.repair.command.SealRepairAcceptanceCommand;
import com.pangu.application.repair.command.StartRepairAssemblyDecisionCommand;
import com.pangu.application.repair.command.StartRepairLocalDecisionCommand;
import com.pangu.application.repair.command.SubmitRepairApprovalPackageCommand;
import com.pangu.application.repair.command.SubmitRepairSupplierQuoteCommand;
import com.pangu.application.repair.command.SubmitRepairOnlineVoteCommand;
import com.pangu.application.repair.command.SubmitRepairInspectionCommand;
import com.pangu.application.repair.command.SubmitRepairPlanCommand;
import com.pangu.application.repair.command.SubmitRepairSurveyCommand;
import com.pangu.application.repair.command.ReviewRepairPriceCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.model.repair.RepairAttachmentKind;
import com.pangu.domain.model.repair.RepairSpaceScope;
import com.pangu.domain.model.repair.RepairContractSignature;
import com.pangu.domain.model.repair.RepairWorkOrder;
import com.pangu.domain.model.repair.RepairWorkOrderStatus;
import com.pangu.interfaces.web.controller.dto.PageResponse;
import com.pangu.interfaces.web.controller.dto.repair.AssignRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.CompleteRepairContractRequest;
import com.pangu.interfaces.web.controller.dto.repair.CompleteRepairLocalDecisionRequest;
import com.pangu.interfaces.web.controller.dto.repair.CorrectRepairLocationRequest;
import com.pangu.interfaces.web.controller.dto.repair.CreateRepairContractRequest;
import com.pangu.interfaces.web.controller.dto.repair.CreateRepairPaymentRequest;
import com.pangu.interfaces.web.controller.dto.repair.CreatePrivateRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.CreatePublicRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.EvaluateRepairRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairQuoteInvitationResponse;
import com.pangu.interfaces.web.controller.dto.repair.RecommendRepairSupplierRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairDecisionRoomResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairAttachmentDownloadTicketResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairAttachmentPreviewTicketResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairAttachmentResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairFrameworkRelationResponse;
import com.pangu.interfaces.web.controller.dto.repair.RegisterSupplierOrganizationRequest;
import com.pangu.interfaces.web.controller.dto.repair.InviteRepairSuppliersRequest;
import com.pangu.interfaces.web.controller.dto.repair.RecordRepairAcceptanceRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairActionRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairLocationOptionsResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairPlanningPolicyResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairSupplierOrganizationResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairSupplierQuoteResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairContractSupplierCandidateResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairOwnerLocalDecisionResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairLocalDecisionResponse;
import com.pangu.interfaces.web.controller.dto.repair.SubmitRepairOnlineVoteRequest;
import com.pangu.interfaces.web.controller.dto.repair.SubmitRepairInspectionRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairSupplierWorkOrderResponse;
import com.pangu.interfaces.web.controller.dto.repair.SealRepairGovernanceRequest;
import com.pangu.interfaces.web.controller.dto.repair.SealRepairAcceptanceRequest;
import com.pangu.interfaces.web.controller.dto.repair.StartRepairAssemblyDecisionRequest;
import com.pangu.interfaces.web.controller.dto.repair.StartRepairLocalDecisionRequest;
import com.pangu.interfaces.web.controller.dto.repair.SubmitRepairApprovalPackageRequest;
import com.pangu.interfaces.web.controller.dto.repair.SubmitRepairSupplierQuoteRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairWorkOrderEventResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairWorkOrderResponse;
import com.pangu.interfaces.web.controller.dto.repair.SubmitRepairPlanRequest;
import com.pangu.interfaces.web.controller.dto.repair.SubmitRepairSurveyRequest;
import com.pangu.interfaces.web.controller.dto.repair.ReviewRepairPriceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RepairWorkOrderController extends BaseController {

    private final RepairWorkOrderService service;
    private final RepairAttachmentService attachmentService;

    @PostMapping(
            value = "/admin/repair-work-orders/{workOrderId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:manage','repair:workorder:governance')")
    public Result<RepairAttachmentResponse> uploadAttachment(
            @PathVariable("workOrderId") Long workOrderId,
            @RequestParam("attachmentKind") String attachmentKind,
            @RequestParam("contentType") String contentType,
            @RequestPart("file") MultipartFile file) {
        try {
            return success(RepairAttachmentResponse.from(attachmentService.upload(
                    workOrderId, new UploadRepairAttachmentCommand(
                            attachmentKind, file.getOriginalFilename(), contentType, file.getBytes()))));
        } catch (IOException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID, "读取上传附件失败", ex);
        }
    }

    @PostMapping(
            value = "/admin/repair-work-orders/{workOrderId}/intake-attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('repair:workorder:intake')")
    public Result<RepairAttachmentResponse> uploadIntakeAttachment(
            @PathVariable("workOrderId") Long workOrderId,
            @RequestParam("contentType") String contentType,
            @RequestPart("file") MultipartFile file) {
        try {
            return success(RepairAttachmentResponse.from(attachmentService.uploadIntakeAttachment(
                    workOrderId, new UploadRepairAttachmentCommand(
                            RepairAttachmentKind.INTAKE_ATTACHMENT.name(), file.getOriginalFilename(),
                            contentType, file.getBytes()))));
        } catch (IOException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID, "读取登记附件失败", ex);
        }
    }

    @PostMapping(
            value = "/supplier/repair-work-orders/{workOrderId}/quote-attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('repair:workorder:supplier')")
    public Result<RepairAttachmentResponse> uploadSupplierQuoteAttachment(
            @PathVariable("workOrderId") Long workOrderId,
            @RequestPart("file") MultipartFile file) {
        try {
            return success(RepairAttachmentResponse.from(attachmentService.upload(
                    workOrderId, new UploadRepairAttachmentCommand(
                            "QUOTE_DOCUMENT", file.getOriginalFilename(), file.getContentType(), file.getBytes()))));
        } catch (IOException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID, "读取上传附件失败", ex);
        }
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/attachments/{attachmentId}/download-url")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:governance')")
    public Result<RepairAttachmentDownloadTicketResponse> createAttachmentDownloadTicket(
            @PathVariable("workOrderId") Long workOrderId,
            @PathVariable("attachmentId") Long attachmentId) {
        return success(RepairAttachmentDownloadTicketResponse.from(
                attachmentService.createDownloadTicket(workOrderId, attachmentId)));
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/attachments/{attachmentId}/preview-url")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:governance')")
    public Result<RepairAttachmentPreviewTicketResponse> createAttachmentPreviewTicket(
            @PathVariable("workOrderId") Long workOrderId,
            @PathVariable("attachmentId") Long attachmentId) {
        return success(RepairAttachmentPreviewTicketResponse.from(
                attachmentService.createPreviewTicket(workOrderId, attachmentId)));
    }

    @DeleteMapping("/admin/repair-work-orders/{workOrderId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:governance')")
    public Result<Void> deleteAttachment(
            @PathVariable("workOrderId") Long workOrderId,
            @PathVariable("attachmentId") Long attachmentId) {
        attachmentService.deleteUnbound(workOrderId, attachmentId);
        return success(null);
    }

    @DeleteMapping("/supplier/repair-work-orders/{workOrderId}/quote-attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('repair:workorder:supplier')")
    public Result<Void> deleteSupplierQuoteAttachment(
            @PathVariable("workOrderId") Long workOrderId,
            @PathVariable("attachmentId") Long attachmentId) {
        attachmentService.deleteUnbound(workOrderId, attachmentId);
        return success(null);
    }

    @GetMapping("/me/repairs")
    @PreAuthorize("isAuthenticated()")
    public Result<List<RepairWorkOrderResponse>> listMine() {
        return success(service.listMine().stream().map(RepairWorkOrderResponse::from).toList());
    }

    @GetMapping("/me/repair-local-decisions")
    @PreAuthorize("isAuthenticated()")
    public Result<List<RepairOwnerLocalDecisionResponse>> listMyLocalDecisions() {
        return success(service.listMyLocalDecisions().stream()
                .map(RepairOwnerLocalDecisionResponse::from)
                .toList());
    }

    @PostMapping("/me/repair-local-decisions/{decisionId}/votes")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairOwnerLocalDecisionResponse> submitMyLocalDecisionVote(
            @PathVariable("decisionId") Long decisionId,
            @Valid @RequestBody SubmitRepairOnlineVoteRequest request) {
        return success(RepairOwnerLocalDecisionResponse.from(service.submitMyOnlineVote(
                decisionId, new SubmitRepairOnlineVoteCommand(request.opid(), request.choice()))));
    }

    @GetMapping("/me/repair-local-decisions/{decisionId}/quote-preview")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairAttachmentPreviewTicketResponse> createMyDecisionQuotePreviewTicket(
            @PathVariable("decisionId") Long decisionId,
            @RequestParam("opid") Long opid) {
        return success(RepairAttachmentPreviewTicketResponse.from(
                attachmentService.createOwnerDecisionQuotePreviewTicket(decisionId, opid)));
    }

    @GetMapping("/me/repairs/{workOrderId}")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairWorkOrderResponse> findMine(@PathVariable("workOrderId") Long workOrderId) {
        return success(RepairWorkOrderResponse.from(service.findMine(workOrderId)));
    }

    @GetMapping("/me/repairs/{workOrderId}/acceptance-rooms")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Long>> listMyAcceptanceRooms(@PathVariable("workOrderId") Long workOrderId) {
        return success(service.listMyAcceptanceRooms(workOrderId));
    }

    @PostMapping("/me/repairs/private")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<RepairWorkOrderResponse>> createPrivate(
            @Valid @RequestBody CreatePrivateRepairRequest request) {
        RepairWorkOrder order = service.createPrivate(new CreatePrivateRepairCommand(
                request.opid(), request.title(), request.description(), request.category(), request.evidenceText()));
        return ResponseEntity.status(HttpStatus.CREATED).body(success(RepairWorkOrderResponse.from(order)));
    }

    @PostMapping("/me/repairs/public")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Result<RepairWorkOrderResponse>> createPublic(
            @Valid @RequestBody CreatePublicRepairRequest request) {
        RepairWorkOrder order = service.createPublic(toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(success(RepairWorkOrderResponse.from(order)));
    }

    /** 关联业务：业主提交报修后补传现场照片，并由服务端按工单归属立即固化。 */
    @PostMapping(
            value = "/me/repairs/{workOrderId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Result<RepairAttachmentResponse> uploadMyRepairAttachment(
            @PathVariable("workOrderId") Long workOrderId,
            @RequestParam("contentType") String contentType,
            @RequestPart("file") MultipartFile file) {
        try {
            return success(RepairAttachmentResponse.from(attachmentService.uploadOwnerReportImage(
                    workOrderId, new UploadRepairAttachmentCommand(
                            RepairAttachmentKind.OWNER_REPORT_IMAGE.name(), file.getOriginalFilename(),
                            contentType, file.getBytes()))));
        } catch (IOException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID, "读取业主报修照片失败", ex);
        }
    }

    @PostMapping("/me/repairs/{workOrderId}/evaluation")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairWorkOrderResponse> evaluate(@PathVariable("workOrderId") Long workOrderId,
                                                    @Valid @RequestBody EvaluateRepairRequest request) {
        RepairWorkOrder order = service.evaluate(workOrderId,
                new EvaluateRepairCommand(request.satisfactionScore(), request.comment()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/me/repairs/{workOrderId}/acceptance-records")
    @PreAuthorize("isAuthenticated()")
    public Result<RepairWorkOrderResponse> recordMyAcceptance(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody RecordRepairAcceptanceRequest request) {
        RepairWorkOrder order = service.recordMyAcceptance(workOrderId, new RecordRepairAcceptanceCommand(
                request.roomId(), null, "AFFECTED_OWNER", request.participantName(), null,
                request.conclusion(), request.opinion(), request.signatureHash(),
                request.evidenceHash(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @GetMapping("/admin/repair-work-orders")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<PageResponse<RepairWorkOrderResponse>> pageAdmin(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<RepairWorkOrder> result = service.pageAdmin(parseStatus(status), parseScope(scope), keyword, page, size);
        return success(PageResponse.from(result, RepairWorkOrderResponse::from));
    }

    @GetMapping("/admin/repair-work-orders/planning-policy")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairPlanningPolicyResponse> getPlanningPolicy() {
        return success(RepairPlanningPolicyResponse.from(service.getPlanningPolicy()));
    }

    @GetMapping("/supplier/repair-work-orders")
    @PreAuthorize("hasAuthority('repair:workorder:supplier')")
    public Result<List<RepairSupplierWorkOrderResponse>> listSupplierWorkOrders() {
        return success(service.listSupplierWorkOrders().stream()
                .map(RepairSupplierWorkOrderResponse::from)
                .toList());
    }

    @PostMapping("/admin/supplier-organizations")
    @PreAuthorize("hasAuthority('repair:supplier:manage')")
    public ResponseEntity<Result<Long>> registerSupplierOrganization(
            @Valid @RequestBody RegisterSupplierOrganizationRequest request) {
        Long supplierDeptId = service.registerSupplierOrganization(new RegisterSupplierOrganizationCommand(
                request.legalName(), request.unifiedSocialCreditCode(),
                request.contactName(), request.contactPhone()));
        return ResponseEntity.status(HttpStatus.CREATED).body(success(supplierDeptId));
    }

    @GetMapping("/admin/supplier-organizations")
    @PreAuthorize("hasAnyAuthority('repair:supplier:manage','repair:supplier:verify','repair:workorder:manage','repair:workorder:field')")
    public Result<List<RepairSupplierOrganizationResponse>> listSupplierOrganizations() {
        return success(service.listSupplierOrganizations().stream()
                .map(RepairSupplierOrganizationResponse::from)
                .toList());
    }

    @GetMapping("/admin/supplier-framework-relations")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<List<RepairFrameworkRelationResponse>> listActiveFrameworkRelations(
            @RequestParam(name = "serviceCategory", required = false) String serviceCategory) {
        return success(service.listActiveFrameworkRelations(serviceCategory).stream()
                .map(RepairFrameworkRelationResponse::from)
                .toList());
    }

    @PostMapping("/admin/repair-work-orders")
    @PreAuthorize("hasAuthority('repair:workorder:intake')")
    public ResponseEntity<Result<RepairWorkOrderResponse>> createAdminPublic(
            @Valid @RequestBody CreatePublicRepairRequest request) {
        RepairWorkOrder order = service.createAdminPublic(toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(success(RepairWorkOrderResponse.from(order)));
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<RepairWorkOrderResponse> findAdmin(@PathVariable("workOrderId") Long workOrderId) {
        return success(RepairWorkOrderResponse.from(service.findAdmin(workOrderId)));
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/events")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<List<RepairWorkOrderEventResponse>> events(@PathVariable("workOrderId") Long workOrderId) {
        return success(service.listEvents(workOrderId).stream().map(RepairWorkOrderEventResponse::from).toList());
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/supplier-quotes")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<List<RepairSupplierQuoteResponse>> listSupplierQuotes(
            @PathVariable("workOrderId") Long workOrderId) {
        return success(service.listSupplierQuotes(workOrderId).stream()
                .map(RepairSupplierQuoteResponse::from)
                .toList());
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/contract-supplier-candidate")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairContractSupplierCandidateResponse> getContractSupplierCandidate(
            @PathVariable("workOrderId") Long workOrderId) {
        return success(RepairContractSupplierCandidateResponse.from(
                service.getContractSupplierCandidate(workOrderId)));
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/supplier-quotes/{supplierDeptId}/history")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<List<RepairSupplierQuoteResponse>> listSupplierQuoteHistory(
            @PathVariable("workOrderId") Long workOrderId,
            @PathVariable("supplierDeptId") Long supplierDeptId) {
        return success(service.listSupplierQuoteHistory(workOrderId, supplierDeptId).stream()
                .map(RepairSupplierQuoteResponse::from)
                .toList());
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/quote-invitations")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<List<RepairQuoteInvitationResponse>> listQuoteInvitations(
            @PathVariable("workOrderId") Long workOrderId) {
        return success(service.listQuoteInvitations(workOrderId).stream()
                .map(RepairQuoteInvitationResponse::from)
                .toList());
    }

    @GetMapping("/admin/repair-work-orders/location-options")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairLocationOptionsResponse> locationOptions() {
        return success(RepairLocationOptionsResponse.from(service.listLocationOptions()));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/accept")
    @PreAuthorize("hasAuthority('repair:workorder:intake')")
    public Result<RepairWorkOrderResponse> accept(@PathVariable("workOrderId") Long workOrderId,
                                                  @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.accept(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/correct-location")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> correctLocation(@PathVariable("workOrderId") Long workOrderId,
                                                           @Valid @RequestBody CorrectRepairLocationRequest request) {
        RepairWorkOrder order = service.correctLocation(workOrderId, new CorrectRepairLocationCommand(
                request.publicAreaScope(), request.buildingId(), request.roomId(), request.locationText(), request.reason(),
                request.fieldSupplement(), request.evidenceImageAttachmentIds()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/verify-location")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> verifyLocation(@PathVariable("workOrderId") Long workOrderId,
                                                          @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.verifyLocation(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/assign")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> assign(@PathVariable("workOrderId") Long workOrderId,
                                                  @Valid @RequestBody AssignRepairRequest request) {
        RepairWorkOrder order = service.assign(workOrderId, new AssignRepairCommand(
                request.assignedUserId(), request.assigneeRoleKey(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/start-survey")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> startSurvey(@PathVariable("workOrderId") Long workOrderId,
                                                       @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.startSurvey(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/submit-survey")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> submitSurvey(@PathVariable("workOrderId") Long workOrderId,
                                                        @Valid @RequestBody SubmitRepairSurveyRequest request) {
        RepairWorkOrder order = service.submitSurvey(workOrderId, new SubmitRepairSurveyCommand(
                request.surveySummary(), request.riskLevel(), request.evidenceImageAttachmentIds(),
                request.evidenceVideoAttachmentId(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/submit-inspection")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> submitInspection(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody SubmitRepairInspectionRequest request) {
        RepairWorkOrder order = service.submitInspection(workOrderId, new SubmitRepairInspectionCommand(
                request.publicAreaScope(), request.buildingId(), request.roomId(), request.locationText(),
                request.fieldSupplement(), request.surveySummary(), request.riskLevel(),
                request.evidenceImageAttachmentIds(), request.evidenceVideoAttachmentId(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/submit-plan")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> submitPlan(@PathVariable("workOrderId") Long workOrderId,
                                                      @Valid @RequestBody SubmitRepairPlanRequest request) {
        SubmitRepairPlanCommand.AcceptancePolicy acceptancePolicy = request.acceptancePolicy() == null
                ? null
                : new SubmitRepairPlanCommand.AcceptancePolicy(
                request.acceptancePolicy().affectedOwners().stream()
                        .map(item -> new SubmitRepairPlanCommand.AffectedOwner(
                                item.roomId(), item.ownerUid(), item.affectedReason()))
                        .toList(),
                request.acceptancePolicy().minimumAffectedOwnerParticipants(),
                request.acceptancePolicy().minimumAffectedOwnerApprovals());
        RepairWorkOrder order = service.submitPlan(workOrderId, new SubmitRepairPlanCommand(
                request.planBudget(), request.publicCeilingPrice(), request.fundSource(),
                acceptancePolicy, request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/start-quote-collection")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> startQuoteCollection(@PathVariable("workOrderId") Long workOrderId,
                                                                @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.startQuoteCollection(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/quote-invitations")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> inviteSuppliers(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody InviteRepairSuppliersRequest request) {
        RepairWorkOrder order = service.inviteSuppliers(workOrderId, new InviteRepairSuppliersCommand(
                request.supplierDeptIds(), request.deadline(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/revision-quote-invitations")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> requestSupplierQuoteRevisions(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody InviteRepairSuppliersRequest request) {
        RepairWorkOrder order = service.requestSupplierQuoteRevisions(workOrderId,
                new InviteRepairSuppliersCommand(
                        request.supplierDeptIds(), request.deadline(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/reuse-supplier-quote")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> reusePreviousSupplierQuote(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(
                service.reusePreviousSupplierQuote(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/supplier-quotes")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> submitSupplierQuote(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody SubmitRepairSupplierQuoteRequest request) {
        RepairWorkOrder order = service.submitSupplierQuote(workOrderId, new SubmitRepairSupplierQuoteCommand(
                request.supplierDeptId(), request.quoteInvitationId(), request.supplierName(), request.quoteAmount(),
                request.quoteSummary(), request.attachmentId(), request.originalSource(),
                request.confirmationStatus(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/supplier/repair-work-orders/{workOrderId}/quote")
    @PreAuthorize("hasAuthority('repair:workorder:supplier')")
    public Result<RepairSupplierWorkOrderResponse> submitSupplierWorkbenchQuote(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody SubmitRepairSupplierQuoteRequest request) {
        RepairWorkOrder order = service.submitSupplierQuote(workOrderId, new SubmitRepairSupplierQuoteCommand(
                request.supplierDeptId(), request.quoteInvitationId(), request.supplierName(), request.quoteAmount(),
                request.quoteSummary(), request.attachmentId(), request.originalSource(),
                request.confirmationStatus(), request.remark()));
        return success(RepairSupplierWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/recommend-supplier")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> recommendSupplier(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody RecommendRepairSupplierRequest request) {
        RepairWorkOrder order = service.recommendSupplier(workOrderId, new RecommendRepairSupplierCommand(
                request.quoteId(), request.selectionMethod(), request.recommendationReason(),
                request.insufficientQuoteReason(), request.frameworkRelationId(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/start-local-decision")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:local-decision')")
    public Result<RepairWorkOrderResponse> startLocalDecision(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody(required = false) StartRepairLocalDecisionRequest request) {
        StartRepairLocalDecisionRequest safeRequest = request == null
                ? new StartRepairLocalDecisionRequest(null, null, null, null, null)
                : request;
        RepairWorkOrder order = service.startLocalDecision(workOrderId,
                new StartRepairLocalDecisionCommand(safeRequest.scopeType(), safeRequest.decisionChannel(), safeRequest.unitName(),
                        safeRequest.scopeLabel(), safeRequest.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/complete-local-decision")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:local-decision')")
    public Result<RepairWorkOrderResponse> completeLocalDecision(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody CompleteRepairLocalDecisionRequest request) {
        List<CompleteRepairLocalDecisionCommand.Entry> entries = (request.entries() == null
                ? List.<CompleteRepairLocalDecisionRequest.Entry>of()
                : request.entries()).stream()
                .map(item -> new CompleteRepairLocalDecisionCommand.Entry(
                        item.roomId(), item.ownerUid(), item.choice(), item.originalText()))
                .toList();
        RepairWorkOrder order = service.completeLocalDecision(workOrderId, new CompleteRepairLocalDecisionCommand(
                entries, request.evidenceAttachmentId(), request.evidenceAttachmentHash(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/pause-local-decision")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:local-decision')")
    public Result<RepairWorkOrderResponse> pauseLocalDecision(
            @PathVariable("workOrderId") Long workOrderId,
            @RequestBody(required = false) RepairActionRequest request) {
        RepairWorkOrder order = service.pauseLocalDecision(workOrderId,
                new RepairActionCommand(request == null ? null : request.remark(), null, List.of()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/resume-local-decision")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:local-decision')")
    public Result<RepairWorkOrderResponse> resumeLocalDecision(
            @PathVariable("workOrderId") Long workOrderId,
            @RequestBody(required = false) RepairActionRequest request) {
        RepairWorkOrder order = service.resumeLocalDecision(workOrderId,
                new RepairActionCommand(request == null ? null : request.remark(), null, List.of()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/local-decision-rooms")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:local-decision')")
    public Result<List<RepairDecisionRoomResponse>> listLocalDecisionRooms(
            @PathVariable("workOrderId") Long workOrderId) {
        return success(service.listLocalDecisionRooms(workOrderId).stream()
                .map(RepairDecisionRoomResponse::from)
                .toList());
    }

    @GetMapping("/admin/repair-work-orders/{workOrderId}/local-decision")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:local-decision')")
    public Result<RepairLocalDecisionResponse> getLocalDecision(
            @PathVariable("workOrderId") Long workOrderId) {
        return success(RepairLocalDecisionResponse.from(service.getLocalDecision(workOrderId)));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/approval-package")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> submitApprovalPackage(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody SubmitRepairApprovalPackageRequest request) {
        RepairWorkOrder order = service.submitApprovalPackage(workOrderId, new SubmitRepairApprovalPackageCommand(
                request.officialDocumentAttachmentId(), request.solitaireScreenshotAttachmentIds(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/price-review")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> reviewPrice(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody ReviewRepairPriceRequest request) {
        RepairWorkOrder order = service.reviewPrice(workOrderId, new ReviewRepairPriceCommand(
                request.reviewMode(), request.reviewedAmount(), request.reviewReportHash(),
                request.conclusion(), request.opinion()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/start-assembly-decision")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> startAssemblyDecision(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody StartRepairAssemblyDecisionRequest request) {
        RepairWorkOrder order = service.startAssemblyDecision(workOrderId,
                new StartRepairAssemblyDecisionCommand(request.packageId(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/complete-assembly-decision")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> completeAssemblyDecision(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.completeAssemblyDecision(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/governance-approve")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> governanceApprove(@PathVariable("workOrderId") Long workOrderId,
                                                             @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.governanceApprove(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/governance-confirm")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> governanceConfirm(@PathVariable("workOrderId") Long workOrderId,
                                                             @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.governanceApprove(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/seal")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> seal(@PathVariable("workOrderId") Long workOrderId,
                                                @Valid @RequestBody SealRepairGovernanceRequest request) {
        RepairWorkOrder order = service.sealGovernance(workOrderId,
                new SealRepairGovernanceCommand(request.sealingMethod(), request.sealedAttachmentId(),
                        request.electronicSealId(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/contracts")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> createContract(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody CreateRepairContractRequest request) {
        RepairWorkOrder order = service.createContract(workOrderId, new CreateRepairContractCommand(
                request.supplierDeptId(), request.supplierName(), request.contractAmount(),
                request.repairScopeHash(), request.fundSource(), request.signingMethod(),
                request.contractFileHash(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/contracts/complete")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> completeContract(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody CompleteRepairContractRequest request) {
        List<RepairContractSignature> signatures = request.signatures().stream()
                .map(item -> new RepairContractSignature(item.partyType(), item.signerName(), item.signerUserId(),
                        item.signatureMethod(), item.signatureFileHash(), item.signedAt()))
                .toList();
        RepairWorkOrder order = service.completeContract(workOrderId, new CompleteRepairContractCommand(
                signatures, request.finalContractFileHash(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/payment-requests")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairWorkOrderResponse> createPaymentRequest(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody CreateRepairPaymentRequest request) {
        RepairWorkOrder order = service.createPaymentRequest(workOrderId, new CreateRepairPaymentRequestCommand(
                request.milestoneType(), request.requestedAmount(),
                request.conditionEvidenceHash(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/start-work")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> startWork(@PathVariable("workOrderId") Long workOrderId,
                                                     @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.startWork(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/submit-acceptance")
    @PreAuthorize("hasAuthority('repair:workorder:field')")
    public Result<RepairWorkOrderResponse> submitAcceptance(@PathVariable("workOrderId") Long workOrderId,
                                                            @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.submitAcceptance(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/acceptance-records")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:local-decision','repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> recordAcceptance(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody RecordRepairAcceptanceRequest request) {
        RepairWorkOrder order = service.recordAcceptance(workOrderId, new RecordRepairAcceptanceCommand(
                request.roomId(), request.ownerUid(), request.participantType(), request.participantName(),
                request.participantOrganization(), request.conclusion(), request.opinion(),
                request.signatureHash(), request.evidenceHash(), request.remark()));
        return success(RepairWorkOrderResponse.from(order));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/acceptance-seal")
    @PreAuthorize("hasAuthority('repair:workorder:governance') and hasAuthority('committee:seal:use')")
    public Result<RepairWorkOrderResponse> sealAcceptance(
            @PathVariable("workOrderId") Long workOrderId,
            @Valid @RequestBody SealRepairAcceptanceRequest request) {
        return success(RepairWorkOrderResponse.from(service.sealAcceptance(
                workOrderId,
                new SealRepairAcceptanceCommand(request.sealedAttachmentId(), request.remark()))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/accept-completed")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:local-decision','repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> acceptCompleted(@PathVariable("workOrderId") Long workOrderId,
                                                           @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.acceptCompleted(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/request-rectification")
    @PreAuthorize("hasAnyAuthority('repair:workorder:field','repair:workorder:local-decision','repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> requestRectification(@PathVariable("workOrderId") Long workOrderId,
                                                                @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.requestRectification(workOrderId, action(request))));
    }

    @PostMapping("/admin/repair-work-orders/{workOrderId}/archive")
    @PreAuthorize("hasAuthority('repair:workorder:governance')")
    public Result<RepairWorkOrderResponse> archive(@PathVariable("workOrderId") Long workOrderId,
                                                   @Valid @RequestBody(required = false) RepairActionRequest request) {
        return success(RepairWorkOrderResponse.from(service.archive(workOrderId, action(request))));
    }

    private CreatePublicRepairCommand toCommand(CreatePublicRepairRequest request) {
        return new CreatePublicRepairCommand(
                request.publicAreaScope(), request.buildingId(), request.locationText(), request.title(),
                request.description(), request.category(), request.evidenceText());
    }

    private RepairActionCommand action(RepairActionRequest request) {
        return new RepairActionCommand(
                request == null ? null : request.remark(),
                request == null ? null : request.fieldSupplement(),
                request == null ? null : request.evidenceImagesBase64());
    }

    private RepairWorkOrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RepairWorkOrderStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID,
                    "未知报修状态 status=" + value);
        }
    }

    private RepairSpaceScope parseScope(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RepairSpaceScope.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID,
                    "未知报修范围 scope=" + value);
        }
    }
}
