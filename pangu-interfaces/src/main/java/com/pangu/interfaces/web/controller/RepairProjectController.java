// 关联业务：暴露维修工程项目台账、实施方案版本、受影响业主预览、项目附件和方案锁定后台接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.repair.RepairProjectAttachmentService;
import com.pangu.application.repair.RepairNarrativeImageService;
import com.pangu.application.repair.RepairProjectService;
import com.pangu.application.repair.RepairWorkOrderApplicationException;
import com.pangu.application.repair.command.UploadRepairProjectAttachmentCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.Attachment;
import com.pangu.interfaces.web.controller.dto.PageResponse;
import com.pangu.interfaces.web.controller.dto.repair.CreateRepairPlanVersionRequest;
import com.pangu.interfaces.web.controller.dto.repair.CreateRepairProjectRequest;
import com.pangu.interfaces.web.controller.dto.repair.LockRepairPlanRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairAttachmentDownloadTicketResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairPlanAttachmentLinkRequest;
import com.pangu.interfaces.web.controller.dto.repair.RepairNarrativeImageResponse;
import com.pangu.interfaces.web.controller.dto.repair.RepairProjectPageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/admin/repair-projects")
@RequiredArgsConstructor
public class RepairProjectController extends BaseController {

    private final RepairProjectService projectService;
    private final RepairProjectAttachmentService attachmentService;
    private final RepairNarrativeImageService narrativeImageService;

    @PostMapping
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairProject.Details> create(@Valid @RequestBody CreateRepairProjectRequest request) {
        return success("维修工程项目已创建", projectService.createProject(request.toCommand()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<PageResponse<RepairProject>> page(@Valid @ModelAttribute RepairProjectPageRequest request) {
        Page<RepairProject> result = projectService.pageProjects(
                request.getStatus(), request.getKeyword(), request.getPage(), request.getSize());
        return success(PageResponse.from(result, item -> item));
    }

    @GetMapping("/allocation-preview")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairProject.AllocationPreview> allocationPreview(
            @RequestParam("scopeType") RepairProject.ScopeType scopeType,
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "unitName", required = false) String unitName) {
        return success(projectService.previewAllocation(scopeType, buildingId, unitName));
    }

    @GetMapping("/affected-owner-preview")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairProject.AffectedOwnerPreview> affectedOwnerPreview(
            @RequestParam("scopeType") RepairProject.ScopeType scopeType,
            @RequestParam("buildingId") Long buildingId,
            @RequestParam(value = "unitName", required = false) String unitName) {
        return success(projectService.previewAffectedOwners(scopeType, buildingId, unitName));
    }

    @PostMapping(value = "/narrative-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairNarrativeImageResponse> uploadNarrativeImage(
            @RequestPart("file") MultipartFile file) {
        try {
            return success("正文图片已上传", RepairNarrativeImageResponse.from(
                    narrativeImageService.upload(new UploadRepairProjectAttachmentCommand(
                            file.getOriginalFilename(), file.getContentType(), file.getBytes()))));
        } catch (IOException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID, "读取正文图片失败", ex);
        }
    }

    @GetMapping("/narrative-images/{imageId}/preview-ticket")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairNarrativeImageResponse> narrativeImagePreviewTicket(
            @PathVariable("imageId") Long imageId) {
        return success(RepairNarrativeImageResponse.from(narrativeImageService.previewTicket(imageId)));
    }

    @DeleteMapping("/narrative-images/{imageId}")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<Void> deleteNarrativeImage(@PathVariable("imageId") Long imageId) {
        narrativeImageService.deleteDraft(imageId);
        return success("正文图片已删除", null);
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("hasAuthority('repair:workorder:read')")
    public Result<RepairProject.Details> detail(@PathVariable("projectId") Long projectId) {
        return success(projectService.findProject(projectId));
    }

    @PostMapping("/{projectId}/plan-versions")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairProject.Details> createPlanVersion(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody CreateRepairPlanVersionRequest request) {
        return success("实施方案新版本已创建",
                projectService.createPlanVersion(projectId, request.toCommand()));
    }

    @PostMapping("/{projectId}/plans/{planId}/attachments")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairProject.Details> linkPlanAttachment(
            @PathVariable("projectId") Long projectId,
            @PathVariable("planId") Long planId,
            @Valid @RequestBody RepairPlanAttachmentLinkRequest request) {
        return success("实施方案附件已关联", projectService.linkDraftPlanAttachment(
                projectId, planId, request.attachmentId(), request.purpose()));
    }

    @PostMapping("/{projectId}/plans/{planId}/lock")
    @PreAuthorize("hasAuthority('repair:workorder:manage')")
    public Result<RepairProject.Details> lockPlan(
            @PathVariable("projectId") Long projectId,
            @PathVariable("planId") Long planId,
            @Valid @RequestBody LockRepairPlanRequest request) {
        return success("实施方案已锁定",
                projectService.lockPlan(projectId, planId, request.expectedProjectVersion()));
    }

    @PostMapping(value = "/{projectId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('repair:workorder:manage','repair:workorder:field','repair:workorder:governance','repair:workorder:local-decision','repair:workorder:supplier')")
    public Result<Attachment> uploadAttachment(
            @PathVariable("projectId") Long projectId,
            @RequestPart("file") MultipartFile file) {
        try {
            return success("项目附件已上传", attachmentService.upload(projectId,
                    new UploadRepairProjectAttachmentCommand(
                            file.getOriginalFilename(), file.getContentType(), file.getBytes())));
        } catch (IOException ex) {
            throw new RepairWorkOrderApplicationException(
                    RepairWorkOrderApplicationException.Reason.PARAM_INVALID, "读取上传附件失败", ex);
        }
    }

    @GetMapping("/{projectId}/attachments/{attachmentId}/download-ticket")
    @PreAuthorize("hasAnyAuthority('repair:workorder:read','repair:workorder:supplier')")
    public Result<RepairAttachmentDownloadTicketResponse> downloadTicket(
            @PathVariable("projectId") Long projectId,
            @PathVariable("attachmentId") Long attachmentId) {
        return success(RepairAttachmentDownloadTicketResponse.from(
                attachmentService.createDownloadTicket(projectId, attachmentId)));
    }
}
