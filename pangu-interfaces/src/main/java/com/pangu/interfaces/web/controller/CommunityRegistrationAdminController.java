// 关联业务：提供属地街镇和平台受控审核人的小区注册审核接口。
package com.pangu.interfaces.web.controller;

import com.pangu.application.registration.CommunityRegistrationApplicationService;
import com.pangu.domain.model.registration.CommunityRegistrationStatus;
import com.pangu.interfaces.web.controller.dto.registration.CommunityRegistrationResponse;
import com.pangu.interfaces.web.controller.dto.registration.ReviewCommunityRegistrationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * G 端小区注册审核入口。
 */
@RestController
@RequestMapping("/api/v1/admin/community-registrations")
@RequiredArgsConstructor
public class CommunityRegistrationAdminController extends BaseController {

    private final CommunityRegistrationApplicationService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('community:registration:review','community:registration:platform-review')")
    public Result<List<CommunityRegistrationResponse>> list(
            @RequestParam(name = "status", required = false) CommunityRegistrationStatus status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return success(service.listForReview(status, limit).stream()
                .map(CommunityRegistrationResponse::from)
                .toList());
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("hasAnyAuthority('community:registration:review','community:registration:platform-review')")
    public Result<CommunityRegistrationResponse> get(
            @PathVariable("applicationId") Long applicationId) {
        return success(CommunityRegistrationResponse.from(service.get(applicationId)));
    }

    @PostMapping("/{applicationId}/reviews")
    @PreAuthorize("hasAnyAuthority('community:registration:review','community:registration:platform-review')")
    public Result<CommunityRegistrationResponse> review(
            @PathVariable("applicationId") Long applicationId,
            @Valid @RequestBody ReviewCommunityRegistrationRequest request) {
        return success("小区注册审核已处理",
                CommunityRegistrationResponse.from(service.review(applicationId, request.toCommand())));
    }
}
