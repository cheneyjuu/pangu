// 关联业务：提供管理端登记、提交、核验并启用本小区物业服务组织与项目部的接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException;
import com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationService;
import com.pangu.application.propertyservice.command.UploadPropertyServiceOrganizationMaterialCommand;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationMaterialType;
import com.pangu.interfaces.web.controller.dto.propertyservice.ManualPropertyServiceOrganizationVerificationRequest;
import com.pangu.interfaces.web.controller.dto.propertyservice.PlatformPropertyServiceOrganizationVerificationRequest;
import com.pangu.interfaces.web.controller.dto.propertyservice.PropertyServiceEnterpriseVerificationProviderResponse;
import com.pangu.interfaces.web.controller.dto.propertyservice.PropertyServiceOrganizationMaterialPreviewResponse;
import com.pangu.interfaces.web.controller.dto.propertyservice.PropertyServiceOrganizationMaterialResponse;
import com.pangu.interfaces.web.controller.dto.propertyservice.PropertyServiceOrganizationRequest;
import com.pangu.interfaces.web.controller.dto.propertyservice.PropertyServiceOrganizationResponse;
import com.pangu.interfaces.web.controller.dto.propertyservice.PropertyServiceOrganizationVersionRequest;
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
 * 新小区物业服务组织登记与核验管理端入口。
 *
 * <p>物业经理和物业员工的工作身份只能在核验通过后，挂接到由本接口启用的 tenant
 * 项目部，不能借用业委会、初始化工作区或跨小区企业根组织。
 */
@RestController
@RequestMapping("/api/v1/admin/property-service-organizations")
@RequiredArgsConstructor
public class PropertyServiceOrganizationController extends BaseController {

    private final PropertyServiceOrganizationApplicationService service;

    @GetMapping
    @PreAuthorize("hasAuthority('property:service-organization:read')")
    public Result<List<PropertyServiceOrganizationResponse>> list() {
        return success(service.list().stream().map(PropertyServiceOrganizationResponse::from).toList());
    }

    @GetMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('property:service-organization:read')")
    public Result<PropertyServiceOrganizationResponse> get(
            @PathVariable("organizationId") Long organizationId) {
        return success(PropertyServiceOrganizationResponse.from(service.get(organizationId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('property:service-organization:submit')")
    public ResponseEntity<Result<PropertyServiceOrganizationResponse>> create(
            @Valid @RequestBody PropertyServiceOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("物业服务组织登记草稿已创建",
                        PropertyServiceOrganizationResponse.from(service.create(request.toCommand()))));
    }

    @PutMapping("/{organizationId}")
    @PreAuthorize("hasAuthority('property:service-organization:submit')")
    public Result<PropertyServiceOrganizationResponse> revise(
            @PathVariable("organizationId") Long organizationId,
            @Valid @RequestBody PropertyServiceOrganizationRequest request) {
        return success("物业服务组织登记已更新",
                PropertyServiceOrganizationResponse.from(service.revise(organizationId, request.toCommand())));
    }

    @PostMapping(value = "/{organizationId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('property:service-organization:submit')")
    public ResponseEntity<Result<PropertyServiceOrganizationMaterialResponse>> uploadMaterial(
            @PathVariable("organizationId") Long organizationId,
            @RequestParam("materialType") String materialType,
            @RequestPart("file") MultipartFile file) {
        try {
            var material = service.uploadMaterial(organizationId, new UploadPropertyServiceOrganizationMaterialCommand(
                    parseMaterialType(materialType), file.getOriginalFilename(), file.getContentType(), file.getBytes()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(success("物业服务组织材料已上传", PropertyServiceOrganizationMaterialResponse.from(material)));
        } catch (IOException ex) {
            throw new PropertyServiceOrganizationApplicationException(
                    PropertyServiceOrganizationApplicationException.Reason.PARAM_INVALID,
                    "读取物业服务组织材料失败", ex);
        }
    }

    @DeleteMapping("/{organizationId}/materials/{materialId}")
    @PreAuthorize("hasAuthority('property:service-organization:submit')")
    public Result<Void> deleteMaterial(
            @PathVariable("organizationId") Long organizationId,
            @PathVariable("materialId") Long materialId) {
        service.deleteMaterial(organizationId, materialId);
        return success("物业服务组织材料已删除", null);
    }

    @GetMapping("/{organizationId}/materials/{materialId}/preview-url")
    @PreAuthorize("hasAuthority('property:service-organization:read')")
    public Result<PropertyServiceOrganizationMaterialPreviewResponse> previewMaterial(
            @PathVariable("organizationId") Long organizationId,
            @PathVariable("materialId") Long materialId) {
        return success(PropertyServiceOrganizationMaterialPreviewResponse.from(
                service.createMaterialPreviewTicket(organizationId, materialId)));
    }

    @PostMapping("/{organizationId}/submit")
    @PreAuthorize("hasAuthority('property:service-organization:submit')")
    public Result<PropertyServiceOrganizationResponse> submit(
            @PathVariable("organizationId") Long organizationId,
            @Valid @RequestBody PropertyServiceOrganizationVersionRequest request) {
        return success("物业服务组织已提交属地核验",
                PropertyServiceOrganizationResponse.from(service.submit(organizationId, request.expectedVersion())));
    }

    @GetMapping("/verification-provider")
    @PreAuthorize("hasAuthority('property:service-organization:verify')")
    public Result<PropertyServiceEnterpriseVerificationProviderResponse> verificationProvider() {
        return success(PropertyServiceEnterpriseVerificationProviderResponse.from(service.providerDescriptor()));
    }

    @PostMapping("/{organizationId}/verifications/manual")
    @PreAuthorize("hasAuthority('property:service-organization:verify')")
    public Result<PropertyServiceOrganizationResponse> verifyManually(
            @PathVariable("organizationId") Long organizationId,
            @Valid @RequestBody ManualPropertyServiceOrganizationVerificationRequest request) {
        return success("物业服务企业手工核验已处理",
                PropertyServiceOrganizationResponse.from(service.verifyManually(organizationId, request.toCommand())));
    }

    @PostMapping("/{organizationId}/verifications/platform")
    @PreAuthorize("hasAuthority('property:service-organization:verify')")
    public Result<PropertyServiceOrganizationResponse> verifyWithPlatform(
            @PathVariable("organizationId") Long organizationId,
            @Valid @RequestBody PlatformPropertyServiceOrganizationVerificationRequest request) {
        return success("物业服务企业平台核验已处理",
                PropertyServiceOrganizationResponse.from(service.verifyWithPlatform(organizationId, request.toCommand())));
    }

    private PropertyServiceOrganizationMaterialType parseMaterialType(String value) {
        try {
            return PropertyServiceOrganizationMaterialType.valueOf(
                    value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new PropertyServiceOrganizationApplicationException(
                    PropertyServiceOrganizationApplicationException.Reason.PARAM_INVALID,
                    "不支持的物业服务组织材料类型：" + value, ex);
        }
    }
}
