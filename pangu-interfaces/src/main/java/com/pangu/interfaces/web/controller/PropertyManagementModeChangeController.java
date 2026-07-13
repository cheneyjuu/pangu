// 关联业务：提供业委会主任发起、街道办审核执行物业管理模式变更的管理端接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.community.PropertyManagementModeChangeApplicationException;
import com.pangu.application.community.PropertyManagementModeChangeApplicationService;
import com.pangu.application.community.command.UploadPropertyManagementModeChangeMaterialCommand;
import com.pangu.domain.model.community.PropertyManagementModeChangeMaterialType;
import com.pangu.interfaces.web.controller.dto.community.PropertyManagementModeChangeMaterialPreviewResponse;
import com.pangu.interfaces.web.controller.dto.community.PropertyManagementModeChangeMaterialResponse;
import com.pangu.interfaces.web.controller.dto.community.PropertyManagementModeChangeRequest;
import com.pangu.interfaces.web.controller.dto.community.PropertyManagementModeChangeResponse;
import com.pangu.interfaces.web.controller.dto.community.PropertyManagementModeChangeVersionRequest;
import com.pangu.interfaces.web.controller.dto.community.ReviewPropertyManagementModeChangeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 物业管理模式变更管理端入口。
 *
 * <p>该接口不允许直接写入租户模式：业委会主任提交业主大会决议材料后，只有街道办
 * 管理员能够审核并执行最终模式切换。
 */
@RestController
@RequestMapping("/api/v1/admin/property-management-mode-changes")
@RequiredArgsConstructor
public class PropertyManagementModeChangeController extends BaseController {

    private final PropertyManagementModeChangeApplicationService service;

    @GetMapping
    @PreAuthorize("hasAuthority('property:management-mode:read')")
    public Result<List<PropertyManagementModeChangeResponse>> list() {
        return success(service.list().stream().map(PropertyManagementModeChangeResponse::from).toList());
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAuthority('property:management-mode:read')")
    public Result<PropertyManagementModeChangeResponse> get(@PathVariable("requestId") Long requestId) {
        return success(PropertyManagementModeChangeResponse.from(service.get(requestId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('property:management-mode:submit')")
    public ResponseEntity<Result<PropertyManagementModeChangeResponse>> create(
            @Valid @RequestBody PropertyManagementModeChangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("物业管理模式变更申请草稿已创建",
                        PropertyManagementModeChangeResponse.from(service.create(request.toCommand()))));
    }

    @PutMapping("/{requestId}")
    @PreAuthorize("hasAuthority('property:management-mode:submit')")
    public Result<PropertyManagementModeChangeResponse> revise(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody PropertyManagementModeChangeRequest request) {
        return success("物业管理模式变更申请已更新",
                PropertyManagementModeChangeResponse.from(service.revise(requestId, request.toCommand())));
    }

    @PostMapping(value = "/{requestId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('property:management-mode:submit')")
    public ResponseEntity<Result<PropertyManagementModeChangeMaterialResponse>> uploadMaterial(
            @PathVariable("requestId") Long requestId,
            @RequestParam("materialType") String materialType,
            @RequestPart("file") MultipartFile file) {
        try {
            var material = service.uploadMaterial(requestId, new UploadPropertyManagementModeChangeMaterialCommand(
                    parseMaterialType(materialType), file.getOriginalFilename(), file.getContentType(), file.getBytes()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(success("物业管理模式变更材料已上传",
                            PropertyManagementModeChangeMaterialResponse.from(material)));
        } catch (IOException ex) {
            throw new PropertyManagementModeChangeApplicationException(
                    PropertyManagementModeChangeApplicationException.Reason.PARAM_INVALID,
                    "读取物业管理模式变更材料失败", ex);
        }
    }

    @DeleteMapping("/{requestId}/materials/{materialId}")
    @PreAuthorize("hasAuthority('property:management-mode:submit')")
    public Result<Void> deleteMaterial(
            @PathVariable("requestId") Long requestId,
            @PathVariable("materialId") Long materialId) {
        service.deleteMaterial(requestId, materialId);
        return success("物业管理模式变更材料已删除", null);
    }

    @GetMapping("/{requestId}/materials/{materialId}/preview-url")
    @PreAuthorize("hasAuthority('property:management-mode:read')")
    public Result<PropertyManagementModeChangeMaterialPreviewResponse> previewMaterial(
            @PathVariable("requestId") Long requestId,
            @PathVariable("materialId") Long materialId) {
        return success(PropertyManagementModeChangeMaterialPreviewResponse.from(
                service.createMaterialPreviewTicket(requestId, materialId)));
    }

    @PostMapping("/{requestId}/submit")
    @PreAuthorize("hasAuthority('property:management-mode:submit')")
    public Result<PropertyManagementModeChangeResponse> submit(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody PropertyManagementModeChangeVersionRequest request) {
        return success("物业管理模式变更申请已提交街道办审核",
                PropertyManagementModeChangeResponse.from(service.submit(requestId, request.toCommand())));
    }

    @PostMapping("/{requestId}/reviews")
    @PreAuthorize("hasAuthority('property:management-mode:review')")
    public Result<PropertyManagementModeChangeResponse> review(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody ReviewPropertyManagementModeChangeRequest request) {
        return success("物业管理模式变更申请已处理",
                PropertyManagementModeChangeResponse.from(service.review(requestId, request.toCommand())));
    }

    private PropertyManagementModeChangeMaterialType parseMaterialType(String value) {
        try {
            return PropertyManagementModeChangeMaterialType.valueOf(
                    value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new PropertyManagementModeChangeApplicationException(
                    PropertyManagementModeChangeApplicationException.Reason.PARAM_INVALID,
                    "不支持的物业管理模式变更材料类型：" + value, ex);
        }
    }
}
