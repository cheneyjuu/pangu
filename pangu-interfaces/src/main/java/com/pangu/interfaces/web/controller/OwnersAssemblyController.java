// 关联业务：提供业主大会会前事项、材料、公示、表决、纸质送达和计票的管理端接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.assembly.OwnersAssemblyApplicationException;
import com.pangu.application.assembly.OwnersAssemblyApplicationService;
import com.pangu.application.assembly.OwnersAssemblyWorkspace;
import com.pangu.application.assembly.command.CastAssemblyPaperVoteWithMaterialCommand;
import com.pangu.application.assembly.command.ConfirmAssemblyArrangementCommand;
import com.pangu.application.assembly.command.CreateAssemblySubjectDraftCommand;
import com.pangu.application.assembly.command.CreateOwnersAssemblySessionCommand;
import com.pangu.application.assembly.command.RecordAssemblyDeliveryWithMaterialCommand;
import com.pangu.application.assembly.command.UploadOwnersAssemblyMaterialCommand;
import com.pangu.domain.model.assembly.OwnersAssemblyMaterial.MaterialType;
import com.pangu.domain.model.assembly.OwnersAssemblyDeliveryRecord;
import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
import com.pangu.domain.model.assembly.OwnersAssemblySession;
import com.pangu.domain.model.assembly.OwnersAssemblyVoteRecord;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.assembly.CastAssemblyPaperVoteWithMaterialRequest;
import com.pangu.interfaces.web.controller.dto.assembly.ConfirmAssemblyArrangementRequest;
import com.pangu.interfaces.web.controller.dto.assembly.CreateAssemblySubjectDraftRequest;
import com.pangu.interfaces.web.controller.dto.assembly.CreateOwnersAssemblySessionRequest;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyArrangementResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyDeliveryResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyFormalSubjectResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyMaterialResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblySessionResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblySubjectDraftResponse;
import com.pangu.interfaces.web.controller.dto.assembly.OwnersAssemblyVoteResponse;
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
import java.util.List;

import static com.pangu.application.assembly.OwnersAssemblyApplicationException.Reason.FORBIDDEN;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OwnersAssemblyController extends BaseController {

    private final OwnersAssemblyApplicationService service;

    @GetMapping("/owners-assemblies")
    @PreAuthorize("hasAnyAuthority('voting:subject:create','voting:subject:publish','voting:subject:audit')")
    public Result<List<OwnersAssemblySessionResponse>> listSessions() {
        return success(service.listSessions(requireTenantId()).stream()
                .map(OwnersAssemblySessionResponse::from).toList());
    }

    @GetMapping("/owners-assemblies/{sessionId}/workspace")
    @PreAuthorize("hasAnyAuthority('voting:subject:create','voting:subject:publish','voting:subject:audit')")
    public Result<OwnersAssemblyWorkspaceResponse> workspace(@PathVariable("sessionId") Long sessionId) {
        return success(toWorkspaceResponse(service.loadWorkspace(sessionId, requireTenantId())));
    }

    @PostMapping("/owners-assemblies")
    @PreAuthorize("hasAuthority('voting:subject:create')")
    public ResponseEntity<Result<OwnersAssemblySessionResponse>> createSession(
            @Valid @RequestBody CreateOwnersAssemblySessionRequest request) {
        OwnersAssemblySession session = service.createSession(new CreateOwnersAssemblySessionCommand(
                requireTenantId(), request.title(), request.preparationMode(), requireUserId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("业主大会已创建", OwnersAssemblySessionResponse.from(session)));
    }

    @PostMapping("/owners-assemblies/{sessionId}/subjects")
    @PreAuthorize("hasAuthority('voting:subject:create')")
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
    @PreAuthorize("hasAnyAuthority('voting:subject:create','voting:subject:audit')")
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
    @PreAuthorize("hasAuthority('voting:subject:create')")
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
    @PreAuthorize("hasAuthority('voting:subject:publish')")
    public Result<OwnersAssemblyArrangementResponse> publishArrangement(@PathVariable("sessionId") Long sessionId) {
        return success("公示已发布", OwnersAssemblyArrangementResponse.from(
                service.publishCurrentArrangement(sessionId, requireTenantId())));
    }

    @PostMapping("/owners-assemblies/{sessionId}/start-voting")
    @PreAuthorize("hasAuthority('voting:subject:publish')")
    public Result<OwnersAssemblyArrangementResponse> startVoting(@PathVariable("sessionId") Long sessionId) {
        return success("投票已开始", OwnersAssemblyArrangementResponse.from(
                service.startVoting(sessionId, requireTenantId())));
    }

    @PostMapping("/owners-assemblies/{sessionId}/settle")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public Result<OwnersAssemblyArrangementResponse> settleCurrentArrangement(@PathVariable("sessionId") Long sessionId) {
        return success("已完成计票并形成结果", OwnersAssemblyArrangementResponse.from(
                service.settleCurrentArrangement(sessionId, requireTenantId())));
    }

    @PostMapping("/owners-assemblies/{sessionId}/paper-deliveries")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public ResponseEntity<Result<OwnersAssemblyDeliveryResponse>> recordPaperDelivery(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody RecordAssemblyDeliveryWithMaterialRequest request) {
        OwnersAssemblyDeliveryRecord delivery = service.recordPaperDelivery(new RecordAssemblyDeliveryWithMaterialCommand(
                sessionId,
                requireTenantId(),
                request.opid(),
                request.deliveryMethod(),
                request.evidenceMaterialId(),
                requireUserId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("纸质选票送达记录已保存", OwnersAssemblyDeliveryResponse.from(delivery)));
    }

    @PostMapping("/owners-assemblies/{sessionId}/paper-votes")
    @PreAuthorize("hasAuthority('voting:subject:audit')")
    public ResponseEntity<Result<OwnersAssemblyVoteResponse>> castPaperVoteWithMaterial(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody CastAssemblyPaperVoteWithMaterialRequest request) {
        OwnersAssemblyVoteRecord vote = service.castPaperVoteWithMaterial(new CastAssemblyPaperVoteWithMaterialCommand(
                sessionId,
                requireTenantId(),
                request.subjectId(),
                request.opid(),
                request.choice(),
                request.ballotMaterialId(),
                requireUserId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("纸质选票已录入", OwnersAssemblyVoteResponse.from(vote)));
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
