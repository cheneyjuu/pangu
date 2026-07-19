// 关联业务：提供业主大会会前事项、材料、公示、表决、纸质送达和计票的管理端接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.assembly.OwnersAssemblyApplicationException;
import com.pangu.application.assembly.OwnersAssemblyApplicationService;
import com.pangu.application.assembly.OwnersAssemblyWorkspace;
import com.pangu.application.assembly.command.ConfirmAssemblyArrangementCommand;
import com.pangu.application.assembly.command.CreateAssemblySubjectDraftCommand;
import com.pangu.application.assembly.command.CreateOwnersAssemblySessionCommand;
import com.pangu.application.assembly.command.RecordAssemblyDeliveryWithMaterialCommand;
import com.pangu.application.assembly.command.RegisterAssemblyPaperBallotCommand;
import com.pangu.application.assembly.command.ReviewAssemblyPaperBallotEntryCommand;
import com.pangu.application.assembly.command.ReviewAssemblyPaperDeliveryCommand;
import com.pangu.application.assembly.command.SubmitAssemblyPaperBallotEntryCommand;
import com.pangu.application.assembly.command.UploadOwnersAssemblyMaterialCommand;
import com.pangu.application.assembly.command.VoidAssemblyPaperBallotCommand;
import com.pangu.application.voting.PaperVotingService;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial.MaterialType;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.assembly.ConfirmAssemblyArrangementRequest;
import com.pangu.interfaces.web.controller.dto.assembly.CreateAssemblySubjectDraftRequest;
import com.pangu.interfaces.web.controller.dto.assembly.CreateOwnersAssemblySessionRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyArrangementResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyFormalSubjectResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyMaterialResponse;
import com.pangu.interfaces.web.controller.dto.assembly.PaperBallotEntryResponse;
import com.pangu.interfaces.web.controller.dto.assembly.PaperBallotResponse;
import com.pangu.interfaces.web.controller.dto.assembly.PaperBallotReviewResponse;
import com.pangu.interfaces.web.controller.dto.assembly.PaperVotingDeliveryResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyVotingWorkbenchResponse;
import com.pangu.interfaces.web.controller.dto.assembly.RegisterAssemblyPaperBallotRequest;
import com.pangu.interfaces.web.controller.dto.assembly.ReviewPaperVotingRecordRequest;
import com.pangu.interfaces.web.controller.dto.assembly.SubmitAssemblyPaperBallotEntryRequest;
import com.pangu.interfaces.web.controller.dto.assembly.VoidAssemblyPaperBallotRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblySessionResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblySubjectDraftResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyRuleSnapshotResponse;
import com.pangu.interfaces.web.controller.dto.assembly.RecordAssemblyDeliveryWithMaterialRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyWorkspaceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.FORBIDDEN;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OwnersAssemblyController extends BaseController {

    private final OwnersAssemblyApplicationService service;

    @GetMapping("/owners-assemblies")
    @PreAuthorize("hasAnyAuthority('voting:subject:create','voting:subject:publish','voting:subject:audit','owners-assembly:formal:manage')")
    public Result<List<OwnersAssemblySessionResponse>> listSessions() {
        return success(service.listSessions(requireTenantId()).stream()
                .map(OwnersAssemblySessionResponse::from).toList());
    }

    @GetMapping("/owners-assemblies/{sessionId}/workspace")
    @PreAuthorize("hasAnyAuthority('voting:subject:create','voting:subject:publish','voting:subject:audit','owners-assembly:formal:manage')")
    public Result<OwnersAssemblyWorkspaceResponse> workspace(@PathVariable("sessionId") Long sessionId) {
        return success(toWorkspaceResponse(service.loadWorkspace(sessionId, requireTenantId())));
    }

    @PostMapping("/owners-assemblies")
    @PreAuthorize("hasAnyAuthority('voting:subject:create','owners-assembly:formal:manage')")
    public ResponseEntity<Result<OwnersAssemblySessionResponse>> createSession(
            @Valid @RequestBody CreateOwnersAssemblySessionRequest request) {
        OwnersAssemblySession session = service.createSession(new CreateOwnersAssemblySessionCommand(
                requireTenantId(), request.title(), request.preparationMode(), requireUserId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("业主大会已创建", OwnersAssemblySessionResponse.from(session)));
    }

    @PostMapping("/owners-assemblies/{sessionId}/subjects")
    @PreAuthorize("hasAnyAuthority('voting:subject:create','owners-assembly:formal:manage')")
    public ResponseEntity<Result<OwnersAssemblySubjectDraftResponse>> createSubjectDraft(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody CreateAssemblySubjectDraftRequest request) {
        var draft = service.createSubjectDraft(new CreateAssemblySubjectDraftCommand(
                sessionId,
                requireTenantId(),
                request.subjectType(),
                request.title(),
                request.content(),
                requireUserId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("表决事项已加入本次业主大会", OwnersAssemblySubjectDraftResponse.from(draft)));
    }

    @PostMapping(path = "/owners-assemblies/{sessionId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('voting:subject:create','voting:subject:audit','owners-assembly:formal:manage')")
    public ResponseEntity<Result<OwnersAssemblyMaterialResponse>> uploadMaterial(
            @PathVariable("sessionId") Long sessionId,
            @RequestParam("materialType") String materialType,
            @RequestPart("file") MultipartFile file) {
        try {
            var material = service.uploadMaterial(new UploadOwnersAssemblyMaterialCommand(
                    sessionId,
                    requireTenantId(),
                    parseMaterialType(materialType),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(success("原始材料已归档", OwnersAssemblyMaterialResponse.from(material)));
        } catch (IOException ex) {
            throw new OwnersAssemblyApplicationException(
                    OwnersAssemblyApplicationException.Reason.PARAM_INVALID, "读取上传材料失败", ex);
        }
    }

    @PostMapping("/owners-assemblies/{sessionId}/arrangement")
    @PreAuthorize("hasAuthority('owners-assembly:formal:manage')")
    public Result<OwnersAssemblyArrangementResponse> confirmArrangement(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody ConfirmAssemblyArrangementRequest request) {
        OwnersAssemblyPackage arrangement = service.confirmArrangement(new ConfirmAssemblyArrangementCommand(
                sessionId,
                requireTenantId(),
                request.voteStartAt(),
                request.voteEndAt(),
                request.publicNoticeMaterialId(),
                request.planAttachmentMaterialIds(),
                request.ballotTemplateMaterialId()));
        return success("公示与表决安排已确认", OwnersAssemblyArrangementResponse.from(arrangement));
    }

    @PostMapping("/owners-assemblies/{sessionId}/publish")
    @PreAuthorize("hasAuthority('owners-assembly:formal:manage')")
    public Result<OwnersAssemblyArrangementResponse> publishArrangement(@PathVariable("sessionId") Long sessionId) {
        return success("公示已发布", OwnersAssemblyArrangementResponse.from(
                service.publishCurrentArrangement(sessionId, requireTenantId())));
    }

    @PostMapping("/owners-assemblies/{sessionId}/start-voting")
    @PreAuthorize("hasAuthority('owners-assembly:formal:manage')")
    public Result<OwnersAssemblyArrangementResponse> startVoting(@PathVariable("sessionId") Long sessionId) {
        return success("投票已开始", OwnersAssemblyArrangementResponse.from(
                service.startVoting(sessionId, requireTenantId())));
    }

    @PostMapping("/owners-assemblies/{sessionId}/settle")
    @PreAuthorize("hasAuthority('owners-assembly:formal:manage')")
    public Result<OwnersAssemblyArrangementResponse> settleCurrentArrangement(@PathVariable("sessionId") Long sessionId) {
        return success("已完成计票并形成结果", OwnersAssemblyArrangementResponse.from(
                service.settleCurrentArrangement(sessionId, requireTenantId())));
    }

    @PostMapping("/owners-assemblies/{sessionId}/paper-deliveries")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public ResponseEntity<Result<PaperVotingDeliveryResponse>> recordPaperDelivery(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody RecordAssemblyDeliveryWithMaterialRequest request) {
        var delivery = service.recordPaperDelivery(new RecordAssemblyDeliveryWithMaterialCommand(
                sessionId,
                requireTenantId(),
                request.opid(),
                request.recipientName(),
                request.deliveryMethod(),
                request.evidenceMaterialId(),
                requireUserId(),
                request.deliveredAt()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("纸质材料送达情况已登记，等待核对", PaperVotingDeliveryResponse.from(delivery)));
    }

    @PostMapping("/owners-assemblies/{sessionId}/paper-deliveries/{paperDeliveryId}/review")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PaperVotingDeliveryResponse> reviewPaperDelivery(
            @PathVariable("sessionId") Long sessionId,
            @PathVariable("paperDeliveryId") Long paperDeliveryId,
            @Valid @RequestBody ReviewPaperVotingRecordRequest request) {
        var delivery = service.reviewPaperDelivery(new ReviewAssemblyPaperDeliveryCommand(
                sessionId,
                paperDeliveryId,
                requireTenantId(),
                request.decision(),
                request.reviewNote(),
                requireUserId(),
                Instant.now()));
        return success("纸质材料送达情况已核对", PaperVotingDeliveryResponse.from(delivery));
    }

    @PostMapping("/owners-assemblies/{sessionId}/paper-ballots")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public ResponseEntity<Result<PaperBallotResponse>> registerPaperBallot(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody RegisterAssemblyPaperBallotRequest request) {
        var ballot = service.registerPaperBallot(new RegisterAssemblyPaperBallotCommand(
                sessionId,
                requireTenantId(),
                request.opid(),
                request.ballotNumber(),
                request.ballotMaterialId(),
                requireUserId(),
                request.receivedAt()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("纸质表决票已登记，等待录入", PaperBallotResponse.from(ballot)));
    }

    @PostMapping("/owners-assemblies/{sessionId}/paper-ballots/{paperBallotId}/void")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PaperBallotResponse> voidPaperBallot(
            @PathVariable("sessionId") Long sessionId,
            @PathVariable("paperBallotId") Long paperBallotId,
            @Valid @RequestBody VoidAssemblyPaperBallotRequest request) {
        var ballot = service.voidPaperBallot(new VoidAssemblyPaperBallotCommand(
                sessionId,
                paperBallotId,
                requireTenantId(),
                request.reason(),
                requireUserId(),
                Instant.now()));
        return success("纸质表决票登记已作废并保留记录", PaperBallotResponse.from(ballot));
    }

    @PostMapping("/owners-assemblies/{sessionId}/paper-ballots/{paperBallotId}/entries")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public ResponseEntity<Result<PaperBallotEntryResponse>> submitPaperBallotEntry(
            @PathVariable("sessionId") Long sessionId,
            @PathVariable("paperBallotId") Long paperBallotId,
            @Valid @RequestBody SubmitAssemblyPaperBallotEntryRequest request) {
        var items = toPaperBallotItems(request);
        var entry = service.submitPaperBallotEntry(new SubmitAssemblyPaperBallotEntryCommand(
                sessionId,
                paperBallotId,
                requireTenantId(),
                items,
                requireUserId(),
                Instant.now()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("纸质表决票录入已提交，等待另一名工作人员核对",
                        PaperBallotEntryResponse.from(entry)));
    }

    @PostMapping("/owners-assemblies/{sessionId}/paper-ballots/{paperBallotId}/entries/{entryId}/review")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<PaperBallotReviewResponse> reviewPaperBallotEntry(
            @PathVariable("sessionId") Long sessionId,
            @PathVariable("paperBallotId") Long paperBallotId,
            @PathVariable("entryId") Long entryId,
            @Valid @RequestBody ReviewPaperVotingRecordRequest request) {
        PaperVotingService.BallotReviewResult result = service.reviewPaperBallotEntry(
                new ReviewAssemblyPaperBallotEntryCommand(
                        sessionId,
                        paperBallotId,
                        entryId,
                        requireTenantId(),
                        request.decision(),
                        request.reviewNote(),
                        requireUserId(),
                        Instant.now()));
        return success(request.decision() == PaperVotingService.ReviewDecision.CONFIRM
                        ? "纸质表决票已核对并完成处理" : "纸质表决票录入已退回",
                PaperBallotReviewResponse.from(result));
    }

    @GetMapping("/owners-assemblies/{sessionId}/voting-workbench")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<OwnersAssemblyVotingWorkbenchResponse> votingWorkbench(
            @PathVariable("sessionId") Long sessionId) {
        return success(OwnersAssemblyVotingWorkbenchResponse.from(
                service.getVotingWorkbench(sessionId, requireTenantId())));
    }

    private Long requireTenantId() {
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到租户上下文");
        }
        return tenantId;
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new OwnersAssemblyApplicationException(FORBIDDEN, "未识别到管理端工作身份");
        }
        return userId;
    }

    private MaterialType parseMaterialType(String value) {
        try {
            return MaterialType.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new OwnersAssemblyApplicationException(
                    OwnersAssemblyApplicationException.Reason.PARAM_INVALID, "不支持的办理材料类型", ex);
        }
    }

    private List<com.pangu.domain.model.voting.PaperBallotEntry.Item> toPaperBallotItems(
            SubmitAssemblyPaperBallotEntryRequest request) {
        try {
            return request.toDomainItems();
        } catch (IllegalArgumentException ex) {
            throw new OwnersAssemblyApplicationException(
                    OwnersAssemblyApplicationException.Reason.PARAM_INVALID, ex.getMessage(), ex);
        }
    }

    private OwnersAssemblyWorkspaceResponse toWorkspaceResponse(OwnersAssemblyWorkspace workspace) {
        return new OwnersAssemblyWorkspaceResponse(
                OwnersAssemblySessionResponse.from(workspace.assembly()),
                OwnersAssemblyArrangementResponse.from(workspace.arrangement()),
                OwnersAssemblyRuleSnapshotResponse.from(workspace.ruleSnapshot()),
                workspace.draftSubjects().stream().map(OwnersAssemblySubjectDraftResponse::from).toList(),
                workspace.formalSubjects().stream().map(OwnersAssemblyFormalSubjectResponse::from).toList(),
                workspace.materials().stream().map(OwnersAssemblyMaterialResponse::from).toList());
    }
}
