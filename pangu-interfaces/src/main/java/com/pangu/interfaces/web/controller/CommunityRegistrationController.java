// 关联业务：提供注册人创建、补充、提交、撤回申请及维护证明材料的接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.registration.CommunityRegistrationApplicationException;
import com.pangu.application.registration.CommunityRegistrationApplicationService;
import com.pangu.application.registration.command.UploadCommunityRegistrationMaterialCommand;
import com.pangu.domain.model.registration.CommunityRegistrationMaterialType;
import com.pangu.interfaces.web.controller.dto.registration.CommunityRegistrationMaterialPreviewResponse;
import com.pangu.interfaces.web.controller.dto.registration.CommunityRegistrationMaterialResponse;
import com.pangu.interfaces.web.controller.dto.registration.CommunityRegistrationRequest;
import com.pangu.interfaces.web.controller.dto.registration.CommunityRegistrationResponse;
import com.pangu.interfaces.web.controller.dto.registration.CommunityRegistrationVersionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 注册人侧小区注册接口。
 *
 * <p>调用方必须先走既有 C 端手机号短信登录；手机号和账号归属均从 JWT 对应的后端
 * 上下文读取，不能由前端替换。
 */
@RestController
@RequestMapping("/api/v1/community-registrations")
@RequiredArgsConstructor
public class CommunityRegistrationController extends BaseController {

    private final CommunityRegistrationApplicationService service;

    @PostMapping
    public ResponseEntity<Result<CommunityRegistrationResponse>> create(
            @Valid @RequestBody CommunityRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("小区注册申请草稿已创建",
                        CommunityRegistrationResponse.from(service.create(request.toCommand()))));
    }

    @PutMapping("/{applicationId}")
    public Result<CommunityRegistrationResponse> revise(
            @PathVariable("applicationId") Long applicationId,
            @Valid @RequestBody CommunityRegistrationRequest request) {
        return success("小区注册申请已更新",
                CommunityRegistrationResponse.from(service.revise(applicationId, request.toCommand())));
    }

    @PostMapping("/{applicationId}/submit")
    public Result<CommunityRegistrationResponse> submit(
            @PathVariable("applicationId") Long applicationId,
            @Valid @RequestBody CommunityRegistrationVersionRequest request) {
        return success("手机号验证成功，注册申请已提交",
                CommunityRegistrationResponse.from(
                        service.submit(applicationId, request.expectedVersion())));
    }

    @PostMapping("/{applicationId}/withdraw")
    public Result<CommunityRegistrationResponse> withdraw(
            @PathVariable("applicationId") Long applicationId,
            @Valid @RequestBody CommunityRegistrationVersionRequest request) {
        return success("小区注册申请已撤回",
                CommunityRegistrationResponse.from(
                        service.withdraw(applicationId, request.expectedVersion())));
    }

    @PostMapping(
            value = "/{applicationId}/materials",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<CommunityRegistrationMaterialResponse>> uploadMaterial(
            @PathVariable("applicationId") Long applicationId,
            @RequestParam("materialType") String materialType,
            @RequestPart("file") MultipartFile file) {
        try {
            var uploaded = service.uploadMaterial(applicationId, new UploadCommunityRegistrationMaterialCommand(
                    parseMaterialType(materialType), file.getOriginalFilename(), file.getContentType(), file.getBytes()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(success("注册审核材料已上传", CommunityRegistrationMaterialResponse.from(uploaded)));
        } catch (IOException ex) {
            throw new CommunityRegistrationApplicationException(
                    CommunityRegistrationApplicationException.Reason.PARAM_INVALID,
                    "读取注册审核材料失败", ex);
        }
    }

    @DeleteMapping("/{applicationId}/materials/{materialId}")
    public Result<Void> deleteMaterial(
            @PathVariable("applicationId") Long applicationId,
            @PathVariable("materialId") Long materialId) {
        service.deleteMaterial(applicationId, materialId);
        return success("注册审核材料已删除", null);
    }

    @GetMapping("/{applicationId}/materials/{materialId}/preview-url")
    public Result<CommunityRegistrationMaterialPreviewResponse> previewMaterial(
            @PathVariable("applicationId") Long applicationId,
            @PathVariable("materialId") Long materialId) {
        return success(CommunityRegistrationMaterialPreviewResponse.from(
                service.createMaterialPreviewTicket(applicationId, materialId)));
    }

    @GetMapping("/mine")
    public Result<List<CommunityRegistrationResponse>> listMine(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return success(service.listMine(limit).stream().map(CommunityRegistrationResponse::from).toList());
    }

    @GetMapping("/{applicationId}")
    public Result<CommunityRegistrationResponse> get(
            @PathVariable("applicationId") Long applicationId) {
        return success(CommunityRegistrationResponse.from(service.get(applicationId)));
    }

    private CommunityRegistrationMaterialType parseMaterialType(String value) {
        try {
            return CommunityRegistrationMaterialType.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new CommunityRegistrationApplicationException(
                    CommunityRegistrationApplicationException.Reason.PARAM_INVALID,
                    "不支持的注册材料类型：" + value, ex);
        }
    }
}
